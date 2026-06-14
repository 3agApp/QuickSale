package me.sourov.quicksale.data.settings

/**
 * Connection details for the WooCommerce store that acts as the source of truth.
 * Persisted locally via [SettingsRepository].
 */
data class StoreSettings(
    val siteUrl: String = "",
    val consumerKey: String = "",
    val consumerSecret: String = "",
) {
    /** True once every field needed to talk to the WooCommerce REST API is present. */
    val isConfigured: Boolean
        get() = hasHttpsSiteUrlHost(siteUrl) && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
}
