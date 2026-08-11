package me.sourov.quicksale.data.remote

import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.settings.CheckoutConfig
import me.sourov.quicksale.data.settings.PaymentGateway
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.data.settings.StoreSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * WooCommerce's own `/wc/v3` routes: the catalog, the store's checkout configuration, and orders.
 *
 * Orders go through here rather than a plugin-specific route — the organization layer is a handful
 * of extra fields on WooCommerce's standard order endpoint, so line items, tax, coupons and stock
 * all behave exactly as they do for any other order.
 */
class WooCommerceApi(settings: StoreSettings) {

    private val http = WooHttp(settings)

    data class Page<T>(val items: List<T>, val totalPages: Int)

    suspend fun fetchProducts(page: Int, perPage: Int = 100): Page<Product> {
        val response = http.get(
            path = "wc/v3/products",
            query = mapOf("page" to page.toString(), "per_page" to perPage.toString()),
        )
        val array = JSONArray(response.body)
        val items = buildList(array.length()) {
            for (i in 0 until array.length()) add(array.getJSONObject(i).toProduct())
        }
        return Page(items, response.totalPages)
    }

    /** A single fresh product (e.g. to refresh local stock right after an order). */
    suspend fun fetchProduct(id: Long): Product =
        JSONObject(http.get("wc/v3/products/$id").body).toProduct()

    /** The store's active display currency. */
    data class Currency(val code: String, val symbol: String)

    /**
     * Reads the store's current currency from `/wc/v3/data/currencies/current` so prices render
     * with the right symbol (e.g. £, €, ৳) instead of a hardcoded $.
     */
    suspend fun fetchCurrency(): Currency {
        val json = JSONObject(http.get("wc/v3/data/currencies/current").body)
        return Currency(
            code = json.optString("code"),
            // WooCommerce sometimes returns the symbol as an HTML entity (e.g. "&#36;").
            symbol = json.optString("symbol").decodeHtmlEntities(),
        )
    }

    /** A product line to send when creating an order. */
    data class LineItem(val productId: Long, val quantity: Int)

    /** A shipping charge to attach to an order. [total] is the net (pre-tax) amount. */
    data class ShippingSelection(val methodId: String, val methodTitle: String, val total: String)

    /**
     * Where an order is being delivered.
     *
     * The store decides whether an order needs a destination at all by looking at its shipping
     * lines, not its products — so a walk-out sale is [None] and is stamped with location `0`.
     */
    sealed interface Destination {
        /** No delivery: no shipping lines, no location. */
        data object None : Destination

        /** One of the organization's saved locations, resolved server-side against the member. */
        data class Location(val id: Long) : Destination

        /**
         * A typed address, allowed only when the organization permits custom shipping. The map is
         * keyed by the field names the address form supplied, and the store validates it with the
         * same rules its checkout applies.
         */
        data class OneOff(val fields: Map<String, String>) : Destination
    }

    /** Totals WooCommerce calculated for a newly created order (tax included where configured). */
    data class CreatedOrder(
        val id: Long,
        val total: String,
        val totalTax: String,
        val shippingTotal: String,
        val discountTotal: String,
        val organizationName: String,
        val locationName: String,
    )

    /**
     * Creates an order and returns the totals the store calculated (the store is authoritative for
     * tax, and re-runs every organization rule regardless of what the app believes).
     *
     * [customerId] must be the member's WordPress `user_id` from the organization snapshot — that
     * is what makes the order the member's, putting it in their *My orders*, their organization's
     * order list and the customer emails. The API key only identifies the till.
     *
     * No billing block is sent: the store writes the organization's own billing address over
     * anything posted, so sending one changes nothing.
     *
     * @param status WooCommerce status slug (e.g. "processing").
     * @param setPaid whether to mark the order paid (records a payment date).
     * @param couponCode optional coupon the store validates and applies server-side.
     * @throws WooApiException with `woap_rest_cannot_purchase`, `woap_rest_shipping_destination`
     *   or `woap_rest_shipping_address` when the store refuses. No order exists after a refusal.
     */
    suspend fun createOrder(
        customerId: Long,
        lineItems: List<LineItem>,
        status: String,
        setPaid: Boolean,
        destination: Destination,
        paymentMethod: PaymentGateway? = null,
        shipping: ShippingSelection? = null,
        couponCode: String? = null,
    ): CreatedOrder {
        val payload = JSONObject().apply {
            put("customer_id", customerId)
            put("status", status)
            put("set_paid", setPaid)
            paymentMethod?.let {
                put("payment_method", it.id)
                put("payment_method_title", it.title)
            }
            when (destination) {
                is Destination.Location -> put("woap_location_id", destination.id)
                is Destination.OneOff -> put("shipping", JSONObject().apply {
                    destination.fields.forEach { (name, value) -> put(name, value) }
                })
                Destination.None -> Unit
            }
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

        val order = JSONObject(http.post("wc/v3/orders", payload).body)
        return CreatedOrder(
            id = order.optLong("id"),
            total = order.optString("total"),
            totalTax = order.optString("total_tax"),
            shippingTotal = order.optString("shipping_total"),
            discountTotal = order.optString("discount_total"),
            // Read-only stamps the plugin adds; both are snapshots taken at order time.
            organizationName = order.optString("woap_organization_name").decodeHtmlEntities(),
            locationName = order.optString("woap_location_name").decodeHtmlEntities(),
        )
    }

    /**
     * Reads the store's checkout behaviour: enabled payment gateways, enabled shipping methods
     * across all zones, and tax settings. Each section degrades independently so a store without
     * (say) shipping zones still yields its gateways.
     */
    suspend fun fetchCheckoutConfig(): CheckoutConfig {
        val general = runCatching { JSONArray(body("wc/v3/settings/general")).settingsMap() }
            .getOrDefault(emptyMap())
        val taxesEnabled = general["woocommerce_calc_taxes"] == "yes"
        // "CH:BL" → "CH"; used to pick the tax rate that applies at the store's base.
        val baseCountry = general["woocommerce_default_country"].orEmpty().substringBefore(":")

        var pricesIncludeTax = false
        var ratePercent: Double? = null
        var taxLabel = "Tax"
        if (taxesEnabled) {
            runCatching {
                val tax = JSONArray(body("wc/v3/settings/tax")).settingsMap()
                pricesIncludeTax = tax["woocommerce_prices_include_tax"] == "yes"
            }
            runCatching {
                val rates = JSONArray(body("wc/v3/taxes", mapOf("per_page" to "100")))
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
            val array = JSONArray(body("wc/v3/payment_gateways"))
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
            val zones = JSONArray(body("wc/v3/shipping/zones"))
            buildList {
                for (i in 0 until zones.length()) {
                    val zone = zones.getJSONObject(i)
                    val methods = JSONArray(body("wc/v3/shipping/zones/${zone.optLong("id")}/methods"))
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

        return CheckoutConfig(
            taxesEnabled = taxesEnabled,
            pricesIncludeTax = pricesIncludeTax,
            standardTaxRatePercent = ratePercent,
            taxLabel = taxLabel,
            gateways = gateways,
            shippingOptions = shippingOptions,
        )
    }

    private suspend fun body(path: String, query: Map<String, String> = emptyMap()): String =
        http.get(path, query).body

    /** `[{id, value}, …]` settings arrays → `id → value` map. */
    private fun JSONArray.settingsMap(): Map<String, String> = buildMap {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { put(it.optString("id"), it.optString("value")) }
        }
    }

    /** Reads `settings.<key>.value` from a shipping method's settings object. */
    private fun JSONObject?.settingValue(key: String): String =
        this?.optJSONObject(key)?.optString("value").orEmpty()

    private fun JSONObject.toProduct(): Product {
        val firstImage = optJSONArray("images")
            ?.takeIf { it.length() > 0 }
            ?.getJSONObject(0)
            ?.optString("src")
            ?.takeIf { it.isNotBlank() }
        val categoryNames = optJSONArray("categories").namesList("name")
        return Product(
            id = optLong("id"),
            name = optString("name").decodeHtmlEntities(),
            sku = optString("sku"),
            ean = readBarcodeNumber(),
            price = optString("price"),
            regularPrice = optString("regular_price"),
            salePrice = optString("sale_price"),
            msrp = readMsrp(),
            stockStatus = optString("stock_status", "instock"),
            stockQuantity = if (isNull("stock_quantity")) null else optInt("stock_quantity"),
            imageUrl = firstImage,
            categories = categoryNames.joinToString(", "),
            description = optString("short_description").ifBlank { optString("description") }.stripHtml(),
        )
    }

    /**
     * The product's manufacturer's suggested retail price, or blank when it has none.
     *
     * `msrp` is added by a plugin rather than WooCommerce core, so the key may be missing entirely
     * and its value may be null — both mean the same thing here, and both print nothing. A store
     * that has no MSRP for a particular product commonly leaves a zero behind rather than removing
     * the key, so a zero is read as "none" too: "MSRP 0.00" on a shelf label is worse than no line
     * at all. The value is kept as the store's own string so it formats like every other price.
     */
    private fun JSONObject.readMsrp(): String {
        val value = opt("msrp") ?: return ""
        if (value == JSONObject.NULL) return ""
        val text = value.toString().trim()
        if (text.isBlank() || text.toDoubleOrNull() == 0.0) return ""
        return text
    }

    /**
     * The product's barcode number.
     *
     * WooCommerce 9.2 added `global_unique_id` — one field holding whichever of GTIN, UPC, EAN or
     * ISBN the product carries — and that is preferred. Stores still on a barcode plugin keep the
     * number in product meta instead, so the keys those plugins write are checked next, in the
     * order a store is likeliest to have them.
     */
    private fun JSONObject.readBarcodeNumber(): String {
        optString("global_unique_id").trim().takeIf { it.isNotBlank() }?.let { return it }
        val meta = optJSONArray("meta_data") ?: return ""
        val values = buildMap {
            for (i in 0 until meta.length()) {
                val entry = meta.optJSONObject(i) ?: continue
                val key = entry.optString("key")
                if (key.isBlank() || entry.isNull("value")) continue
                // Meta values are free-form; only a scalar can be a barcode.
                val value = entry.opt("value") ?: continue
                if (value is JSONObject || value is JSONArray) continue
                putIfAbsent(key, value.toString().trim())
            }
        }
        return BARCODE_META_KEYS.firstNotNullOfOrNull { key ->
            values[key]?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    private fun JSONArray?.namesList(key: String): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.optString(key)?.takeIf { it.isNotBlank() }?.let { add(it) }
            }
        }
    }

    private companion object {
        /** Product meta keys the common WooCommerce barcode/GTIN plugins store the number under. */
        val BARCODE_META_KEYS = listOf(
            "_wpm_gtin_code",
            "_ean",
            "ean",
            "_barcode",
            "barcode",
            "_gtin",
            "gtin",
            "_upc",
            "upc",
        )
    }
}
