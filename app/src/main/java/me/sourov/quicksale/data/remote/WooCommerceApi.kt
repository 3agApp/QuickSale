package me.sourov.quicksale.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sourov.quicksale.data.local.Customer
import me.sourov.quicksale.data.local.Product
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

    private suspend fun <T> fetchPage(
        resource: String,
        page: Int,
        perPage: Int,
        extraQuery: String = "",
        map: (JSONObject) -> T,
    ): Page<T> = withContext(Dispatchers.IO) {
        val base = normalizeBaseUrl(settings.siteUrl)
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

    private fun normalizeBaseUrl(raw: String): String? {
        val trimmed = raw.trim().trimEnd('/')
        if (trimmed.isBlank()) return null
        return if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) trimmed else "https://$trimmed"
    }
}
