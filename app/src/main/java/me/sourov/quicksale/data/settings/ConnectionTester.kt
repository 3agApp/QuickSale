package me.sourov.quicksale.data.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

sealed interface ConnectionResult {
    data class Success(val message: String) : ConnectionResult
    data class Failure(val message: String) : ConnectionResult
}

/**
 * Performs a lightweight, real request against the WooCommerce REST API to confirm the
 * store URL and keys work. Uses query-string auth (the broadly compatible option over HTTPS).
 */
class ConnectionTester {

    suspend fun test(settings: StoreSettings): ConnectionResult = withContext(Dispatchers.IO) {
        val base = normalizeHttpsSiteUrl(settings.siteUrl)
            ?: return@withContext ConnectionResult.Failure("Enter a valid store URL")
        if (settings.consumerKey.isBlank() || settings.consumerSecret.isBlank()) {
            return@withContext ConnectionResult.Failure("Enter both the consumer key and secret")
        }

        val ck = URLEncoder.encode(settings.consumerKey.trim(), "UTF-8")
        val cs = URLEncoder.encode(settings.consumerSecret.trim(), "UTF-8")
        val endpoint = "$base/wp-json/wc/v3/products?per_page=1&consumer_key=$ck&consumer_secret=$cs"

        var connection: HttpURLConnection? = null
        try {
            connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 12_000
                readTimeout = 12_000
                instanceFollowRedirects = true
                setRequestProperty("Accept", "application/json")
            }
            when (val code = connection.responseCode) {
                in 200..299 -> ConnectionResult.Success("Connected to your store successfully")
                401, 403 -> ConnectionResult.Failure("Keys rejected (HTTP $code) — check the consumer key and secret")
                404 -> ConnectionResult.Failure("WooCommerce API not found (404) — check the site URL")
                in 500..599 -> ConnectionResult.Failure("Store error (HTTP $code) — try again shortly")
                else -> ConnectionResult.Failure("Unexpected response (HTTP $code)")
            }
        } catch (e: Exception) {
            ConnectionResult.Failure(e.message ?: "Could not reach the store")
        } finally {
            connection?.disconnect()
        }
    }

}
