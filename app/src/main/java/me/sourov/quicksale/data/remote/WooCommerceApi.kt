package me.sourov.quicksale.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sourov.quicksale.data.local.Customer
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.settings.CheckoutConfig
import me.sourov.quicksale.data.settings.PaymentGateway
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.data.settings.normalizeHttpsSiteUrl
import me.sourov.quicksale.data.settings.StoreSettings
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** Minimal WooCommerce REST client for pulling the catalog and customers. */
class WooCommerceApi(private val settings: StoreSettings) {

    data class Page<T>(val items: List<T>, val totalPages: Int)

    suspend fun fetchProducts(page: Int, perPage: Int = 100): Page<Product> =
        fetchPage("products", page, perPage) { it.toProduct() }

    suspend fun fetchCustomers(page: Int, perPage: Int = 100): Page<Customer> =
        fetchPage("customers", page, perPage, extraQuery = "&role=all") { it.toCustomer() }

    /** The store's active display currency. */
    data class Currency(val code: String, val symbol: String)

    /**
     * Reads the store's current currency from `/wc/v3/data/currencies/current`
     * so prices render with the right symbol (e.g. £, €, ৳) instead of a hardcoded $.
     */
    suspend fun fetchCurrency(): Currency = withContext(Dispatchers.IO) {
        val base = normalizeHttpsSiteUrl(settings.siteUrl)
            ?: throw IllegalStateException("Invalid store URL")
        val ck = URLEncoder.encode(settings.consumerKey.trim(), "UTF-8")
        val cs = URLEncoder.encode(settings.consumerSecret.trim(), "UTF-8")
        val endpoint =
            "$base/wp-json/wc/v3/data/currencies/current?consumer_key=$ck&consumer_secret=$cs"

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Store returned HTTP $code while loading currency")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val json = JSONObject(body)
            Currency(
                code = json.optString("code"),
                // WooCommerce sometimes returns the symbol as an HTML entity (e.g. "&#36;").
                symbol = json.optString("symbol").decodeHtmlEntities(),
            )
        } finally {
            connection?.disconnect()
        }
    }

    /** A product line to send when creating an order. */
    data class LineItem(val productId: Long, val quantity: Int)

    /** A shipping charge to attach to an order. [total] is the net (pre-tax) amount. */
    data class ShippingSelection(val methodId: String, val methodTitle: String, val total: String)

    /** Totals WooCommerce calculated for a newly created order (tax included where configured). */
    data class CreatedOrder(
        val id: Long,
        val total: String,
        val totalTax: String,
        val shippingTotal: String,
        val discountTotal: String,
    )

    /**
     * Creates an order in WooCommerce and returns the totals the store calculated (the store is
     * authoritative for tax). Billing/shipping addresses are passed as the raw JSON objects the
     * customers endpoint returned; when the shipping address is empty the billing address is
     * reused, matching web-checkout behaviour.
     *
     * @param status WooCommerce status slug (e.g. "processing").
     * @param setPaid whether to mark the order paid (records a payment date).
     * @param couponCode optional coupon the store validates and applies server-side.
     */
    suspend fun createOrder(
        customerId: Long,
        lineItems: List<LineItem>,
        status: String,
        setPaid: Boolean,
        paymentMethod: PaymentGateway? = null,
        billingJson: String? = null,
        shippingJson: String? = null,
        shipping: ShippingSelection? = null,
        couponCode: String? = null,
    ): CreatedOrder = withContext(Dispatchers.IO) {
        val base = normalizeHttpsSiteUrl(settings.siteUrl)
            ?: throw IllegalStateException("Invalid store URL")
        val ck = URLEncoder.encode(settings.consumerKey.trim(), "UTF-8")
        val cs = URLEncoder.encode(settings.consumerSecret.trim(), "UTF-8")
        val endpoint = "$base/wp-json/wc/v3/orders?consumer_key=$ck&consumer_secret=$cs"

        val billing = billingJson.toAddressOrNull()
        val shippingAddress = shippingJson.toAddressOrNull() ?: billing

        val payload = JSONObject().apply {
            put("customer_id", customerId)
            put("status", status)
            put("set_paid", setPaid)
            paymentMethod?.let {
                put("payment_method", it.id)
                put("payment_method_title", it.title)
            }
            billing?.let { put("billing", it) }
            shippingAddress?.let { put("shipping", it) }
            put("line_items", JSONArray().apply {
                lineItems.forEach { item ->
                    put(JSONObject().apply {
                        put("product_id", item.productId)
                        put("quantity", item.quantity)
                    })
                }
            })
            shipping?.let {
                put("shipping_lines", JSONArray().put(JSONObject().apply {
                    put("method_id", it.methodId)
                    put("method_title", it.methodTitle)
                    put("total", it.total)
                }))
            }
            couponCode?.takeIf { it.isNotBlank() }?.let {
                put("coupon_lines", JSONArray().put(JSONObject().put("code", it.trim())))
            }
        }

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 20_000
                readTimeout = 20_000
                doOutput = true
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "application/json")
            }
            connection.outputStream.use { it.write(payload.toString().toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(errorMessage(connection, code))
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val order = JSONObject(body)
            CreatedOrder(
                id = order.optLong("id"),
                total = order.optString("total"),
                totalTax = order.optString("total_tax"),
                shippingTotal = order.optString("shipping_total"),
                discountTotal = order.optString("discount_total"),
            )
        } finally {
            connection?.disconnect()
        }
    }

    /** A single fresh product (e.g. to refresh local stock right after an order). */
    suspend fun fetchProduct(id: Long): Product = withContext(Dispatchers.IO) {
        JSONObject(getBody("products/$id")).toProduct()
    }

    /**
     * Reads the store's checkout behaviour: enabled payment gateways, enabled shipping methods
     * across all zones, and tax settings. Each section degrades independently so a store without
     * (say) shipping zones still yields its gateways.
     */
    suspend fun fetchCheckoutConfig(): CheckoutConfig = withContext(Dispatchers.IO) {
        val general = runCatching { JSONArray(getBody("settings/general")).settingsMap() }
            .getOrDefault(emptyMap())
        val taxesEnabled = general["woocommerce_calc_taxes"] == "yes"
        // "CH:BL" → "CH"; used to pick the tax rate that applies at the store's base.
        val baseCountry = general["woocommerce_default_country"].orEmpty().substringBefore(":")

        var pricesIncludeTax = false
        var ratePercent: Double? = null
        var taxLabel = "Tax"
        if (taxesEnabled) {
            runCatching {
                val tax = JSONArray(getBody("settings/tax")).settingsMap()
                pricesIncludeTax = tax["woocommerce_prices_include_tax"] == "yes"
            }
            runCatching {
                val rates = JSONArray(getBody("taxes?per_page=100"))
                var chosen: JSONObject? = null
                for (i in 0 until rates.length()) {
                    val rate = rates.getJSONObject(i)
                    val taxClass = rate.optString("class")
                    if (taxClass.isNotBlank() && taxClass != "standard") continue
                    if (chosen == null) chosen = rate
                    if (rate.optString("country").equals(baseCountry, ignoreCase = true)) {
                        chosen = rate
                        break
                    }
                }
                chosen?.let {
                    ratePercent = it.optString("rate").toDoubleOrNull()
                    taxLabel = it.optString("name").ifBlank { "Tax" }
                }
            }
        }

        val gateways = runCatching {
            val array = JSONArray(getBody("payment_gateways"))
            buildList {
                for (i in 0 until array.length()) {
                    val gateway = array.getJSONObject(i)
                    if (!gateway.optBoolean("enabled")) continue
                    val id = gateway.optString("id")
                    if (id.isBlank()) continue
                    add(PaymentGateway(id, gateway.optString("title").decodeHtmlEntities().ifBlank { id }))
                }
            }
        }.getOrDefault(emptyList())

        val shippingOptions = runCatching {
            val zones = JSONArray(getBody("shipping/zones"))
            buildList {
                for (i in 0 until zones.length()) {
                    val zone = zones.getJSONObject(i)
                    val methods = JSONArray(getBody("shipping/zones/${zone.optLong("id")}/methods"))
                    for (j in 0 until methods.length()) {
                        val method = methods.getJSONObject(j)
                        if (!method.optBoolean("enabled")) continue
                        val methodSettings = method.optJSONObject("settings")
                        add(
                            ShippingOption(
                                zoneName = zone.optString("name"),
                                methodId = method.optString("method_id"),
                                title = methodSettings.settingValue("title")
                                    .ifBlank { method.optString("title") },
                                cost = methodSettings.settingValue("cost"),
                                taxable = methodSettings.settingValue("tax_status")
                                    .ifBlank { "taxable" } == "taxable",
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())

        CheckoutConfig(
            taxesEnabled = taxesEnabled,
            pricesIncludeTax = pricesIncludeTax,
            standardTaxRatePercent = ratePercent,
            taxLabel = taxLabel,
            gateways = gateways,
            shippingOptions = shippingOptions,
        )
    }

    /** Performs an authenticated GET for `/wc/v3/[pathAndQuery]` and returns the response body. */
    private fun getBody(pathAndQuery: String): String {
        val base = normalizeHttpsSiteUrl(settings.siteUrl)
            ?: throw IllegalStateException("Invalid store URL")
        val ck = URLEncoder.encode(settings.consumerKey.trim(), "UTF-8")
        val cs = URLEncoder.encode(settings.consumerSecret.trim(), "UTF-8")
        val separator = if ('?' in pathAndQuery) '&' else '?'
        val endpoint =
            "$base/wp-json/wc/v3/$pathAndQuery${separator}consumer_key=$ck&consumer_secret=$cs"

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException(errorMessage(connection, code))
            }
            return connection.inputStream.bufferedReader().use { it.readText() }
        } finally {
            connection?.disconnect()
        }
    }

    /** Extracts WooCommerce's human-readable `message` from an error response, if present. */
    private fun errorMessage(connection: HttpURLConnection, code: Int): String {
        val detail = runCatching {
            connection.errorStream?.bufferedReader()?.use { it.readText() }
                ?.let { JSONObject(it).optString("message") }
                ?.stripHtml()
        }.getOrNull()
        return if (detail.isNullOrBlank()) "Store returned HTTP $code" else detail
    }

    /** `[{id, value}, …]` settings arrays → `id → value` map. */
    private fun JSONArray.settingsMap(): Map<String, String> = buildMap {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { put(it.optString("id"), it.optString("value")) }
        }
    }

    /** Reads `settings.<key>.value` from a shipping method's settings object. */
    private fun JSONObject?.settingValue(key: String): String =
        this?.optJSONObject(key)?.optString("value").orEmpty()

    /**
     * Parses a stored address JSON and returns it only when it carries actual address data;
     * a customer whose profile is blank should not overwrite the order with empty fields.
     */
    private fun String?.toAddressOrNull(): JSONObject? {
        if (this.isNullOrBlank()) return null
        val address = runCatching { JSONObject(this) }.getOrNull() ?: return null
        val hasContent = listOf(
            "first_name", "last_name", "company", "address_1", "city", "postcode", "country",
        ).any { address.optString(it).isNotBlank() }
        return if (hasContent) address else null
    }

    private suspend fun <T> fetchPage(
        resource: String,
        page: Int,
        perPage: Int,
        extraQuery: String = "",
        map: (JSONObject) -> T,
    ): Page<T> = withContext(Dispatchers.IO) {
        val base = normalizeHttpsSiteUrl(settings.siteUrl)
            ?: throw IllegalStateException("Invalid store URL")
        val ck = URLEncoder.encode(settings.consumerKey.trim(), "UTF-8")
        val cs = URLEncoder.encode(settings.consumerSecret.trim(), "UTF-8")
        val endpoint =
            "$base/wp-json/wc/v3/$resource?per_page=$perPage&page=$page&consumer_key=$ck&consumer_secret=$cs$extraQuery"

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 20_000
                readTimeout = 20_000
                setRequestProperty("Accept", "application/json")
            }
            val code = connection.responseCode
            if (code !in 200..299) {
                throw IllegalStateException("Store returned HTTP $code while loading $resource")
            }
            val totalPages = connection.getHeaderField("X-WP-TotalPages")?.toIntOrNull() ?: 1
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            val array = JSONArray(body)
            val items = buildList(array.length()) {
                for (i in 0 until array.length()) add(map(array.getJSONObject(i)))
            }
            Page(items, totalPages)
        } finally {
            connection?.disconnect()
        }
    }

    private fun JSONObject.toProduct(): Product {
        val firstImage = optJSONArray("images")
            ?.takeIf { it.length() > 0 }
            ?.getJSONObject(0)
            ?.optString("src")
            ?.takeIf { it.isNotBlank() }
        val categoryNames = optJSONArray("categories").namesList("name")
        return Product(
            id = optLong("id"),
            name = optString("name"),
            sku = optString("sku"),
            price = optString("price"),
            regularPrice = optString("regular_price"),
            salePrice = optString("sale_price"),
            stockStatus = optString("stock_status", "instock"),
            stockQuantity = if (isNull("stock_quantity")) null else optInt("stock_quantity"),
            imageUrl = firstImage,
            categories = categoryNames.joinToString(", "),
            description = optString("short_description").ifBlank { optString("description") }.stripHtml(),
        )
    }

    private fun JSONObject.toCustomer(): Customer {
        val billing = optJSONObject("billing")
        val shipping = optJSONObject("shipping")
        return Customer(
            id = optLong("id"),
            firstName = optString("first_name"),
            lastName = optString("last_name"),
            email = optString("email"),
            phone = billing?.optString("phone").orEmpty(),
            company = billing?.optString("company").orEmpty(),
            city = billing?.optString("city").orEmpty(),
            billingJson = billing?.toString().orEmpty(),
            shippingJson = shipping?.toString().orEmpty(),
        )
    }

    private fun JSONArray?.namesList(key: String): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.optString(key)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private fun String.stripHtml(): String =
        replace(Regex("<[^>]*>"), "")
            .replace("&amp;", "&")
            .replace("&nbsp;", " ")
            .replace("&#8211;", "-")
            .replace(Regex("\\s+"), " ")
            .trim()

    /** Decodes numeric (`&#36;`, `&#x24;`) and a few named HTML entities used by currency symbols. */
    private fun String.decodeHtmlEntities(): String {
        if ('&' !in this) return this
        return Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);").replace(this) { match ->
            val entity = match.groupValues[1]
            val codePoint = when {
                entity.startsWith("#x") -> entity.drop(2).toIntOrNull(16)
                entity.startsWith("#") -> entity.drop(1).toIntOrNull()
                else -> namedEntities[entity]
            }
            codePoint?.let { String(Character.toChars(it)) } ?: match.value
        }
    }

    private companion object {
        val namedEntities = mapOf(
            "amp" to '&'.code, "lt" to '<'.code, "gt" to '>'.code, "nbsp" to ' '.code,
            "pound" to '£'.code, "euro" to '€'.code, "yen" to '¥'.code, "cent" to '¢'.code,
        )
    }

}
