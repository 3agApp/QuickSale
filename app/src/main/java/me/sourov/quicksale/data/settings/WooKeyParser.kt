package me.sourov.quicksale.data.settings

/**
 * Extracts WooCommerce REST API credentials from arbitrary scanned text.
 *
 * The QR code WooCommerce shows after generating keys may encode JSON, a URL, or
 * a plain string — but the values always carry the `ck_` / `cs_` prefixes, so we
 * just pull those out regardless of the surrounding format.
 */
object WooKeyParser {
    private val consumerKeyRegex = Regex("ck_[A-Za-z0-9]+")
    private val consumerSecretRegex = Regex("cs_[A-Za-z0-9]+")

    data class Parsed(val consumerKey: String, val consumerSecret: String)

    fun parse(raw: String): Parsed? {
        val key = consumerKeyRegex.find(raw)?.value
        val secret = consumerSecretRegex.find(raw)?.value
        return if (!key.isNullOrBlank() && !secret.isNullOrBlank()) {
            Parsed(consumerKey = key, consumerSecret = secret)
        } else {
            null
        }
    }
}
