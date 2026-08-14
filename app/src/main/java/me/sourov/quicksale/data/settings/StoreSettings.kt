package me.sourov.quicksale.data.settings

/**
 * Connection details for the WooCommerce store that acts as the source of truth.
 * Persisted locally via [SettingsRepository].
 */
data class StoreSettings(
    val siteUrl: String = "",
    val consumerKey: String = "",
    val consumerSecret: String = "",
    /**
     * Skip TLS certificate checks for this store.
     *
     * Defaults on: the fleet's stores need it, and a till that silently can't reach the store is
     * worse than one that can't prove who it's talking to. Turn it off for a store with a
     * certificate Android accepts on its own. The connection stays encrypted either way; what this
     * gives up is the proof of who is on the other end, so the keys below are exposed to anything
     * on the path. No effect on an `http://` store, which was never encrypted to begin with.
     */
    val allowInsecureTls: Boolean = true,
) {
    /** True once every field needed to talk to the WooCommerce REST API is present. */
    val isConfigured: Boolean
        get() = hasSiteUrlHost(siteUrl) && consumerKey.isNotBlank() && consumerSecret.isNotBlank()
}
