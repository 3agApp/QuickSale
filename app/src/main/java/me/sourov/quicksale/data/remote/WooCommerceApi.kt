package me.sourov.quicksale.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sourov.quicksale.data.local.Customer
import me.sourov.quicksale.data.local.Product
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

    /**
     * Creates an order in WooCommerce and returns the new remote order id.
     * @param status WooCommerce status slug (e.g. "processing").
     * @param setPaid whether to mark the order paid (records a payment date).
     */
    suspend fun createOrder(
        customerId: Long,
        lineItems: List<LineItem>,
        status: String,
        setPaid: Boolean,
    ): Long = withContext(Dispatchers.IO) {
        val base = normalizeHttpsSiteUrl(settings.siteUrl)
            ?: throw IllegalStateException("Invalid store URL")
        val ck = URLEncoder.encode(settings.consumerKey.trim(), "UTF-8")
        val cs = URLEncoder.encode(settings.consumerSecret.trim(), "UTF-8")
        val endpoint = "$base/wp-json/wc/v3/orders?consumer_key=$ck&consumer_secret=$cs"

        val payload = JSONObject().apply {
            put("customer_id", customerId)
            put("status", status)
            put("set_paid", setPaid)
            put("line_items", JSONArray().apply {
                lineItems.forEach { item ->
                    put(JSONObject().apply {
                        put("product_id", item.productId)
                        put("quantity", item.quantity)
                    })
                }
            })
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
                throw IllegalStateException("Store returned HTTP $code while creating the order")
            }
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            JSONObject(body).optLong("id")
        } finally {
            connection?.disconnect()
        }
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
        return Customer(
            id = optLong("id"),
            firstName = optString("first_name"),
            lastName = optString("last_name"),
            email = optString("email"),
            phone = billing?.optString("phone").orEmpty(),
            company = billing?.optString("company").orEmpty(),
            city = billing?.optString("city").orEmpty(),
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
