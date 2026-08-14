package me.sourov.quicksale.data.settings

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.sourov.quicksale.data.remote.WooApiException
import me.sourov.quicksale.data.remote.WooHttp
import me.sourov.quicksale.data.remote.isCertificateTrustFailure

sealed interface ConnectionResult {
    data class Success(val message: String) : ConnectionResult

    /** Reached and authenticated, but the organization plugin's routes aren't there. */
    data class Partial(val message: String) : ConnectionResult
    data class Failure(val message: String) : ConnectionResult
}

/**
 * Performs real requests against the store to confirm the URL and keys work.
 *
 * Two checks, because a B2B till needs both: WooCommerce itself, and the organization-accounts
 * routes the app now depends on. A store that answers the first but not the second is reported
 * separately — the keys are fine, the plugin is the problem, and that is a different fix.
 */
class ConnectionTester {

    suspend fun test(settings: StoreSettings): ConnectionResult = withContext(Dispatchers.IO) {
        if (normalizeSiteUrl(settings.siteUrl) == null) {
            return@withContext ConnectionResult.Failure("Enter a valid store URL")
        }
        if (settings.consumerKey.isBlank() || settings.consumerSecret.isBlank()) {
            return@withContext ConnectionResult.Failure("Enter both the consumer key and secret")
        }

        val http = WooHttp(settings)

        try {
            http.get("wc/v3/products", mapOf("per_page" to "1"))
        } catch (e: WooApiException) {
            return@withContext ConnectionResult.Failure(wooFailure(e))
        } catch (e: Exception) {
            return@withContext ConnectionResult.Failure(reachFailure(e, settings))
        }

        try {
            http.get("wc-woap/v1/organizations", mapOf("per_page" to "1"))
            ConnectionResult.Success("Connected — WooCommerce and organization accounts both responded")
        } catch (e: WooApiException) {
            ConnectionResult.Partial(
                when (e.status) {
                    404 -> "WooCommerce is connected, but the organization accounts plugin isn't " +
                        "responding — check it's installed and active on the store"

                    401, 403 -> "WooCommerce is connected, but these keys can't read organizations " +
                        "— the key's user needs the manage_woocommerce capability"

                    else -> "WooCommerce is connected, but organizations failed: ${e.message}"
                }
            )
        } catch (e: Exception) {
            ConnectionResult.Partial(
                "WooCommerce is connected, but organizations couldn't be reached: ${e.message}"
            )
        }
    }

    /**
     * Names the one failure the operator can fix from this screen.
     *
     * A rejected certificate arrives as "Chain validation failed", which reads like a bug in the
     * app rather than a property of the store — and the fix is a switch a few lines further down
     * the same screen, so the message says so instead of repeating the platform's wording.
     */
    private fun reachFailure(e: Exception, settings: StoreSettings): String = when {
        isCertificateTrustFailure(e) && !settings.allowInsecureTls ->
            "The store's HTTPS certificate couldn't be verified. If it's self-signed or from an " +
                "internal CA, turn on \"Allow insecure connection\" below and test again."

        else -> e.message ?: "Could not reach the store"
    }

    private fun wooFailure(e: WooApiException): String = when (e.status) {
        401, 403 -> "Keys rejected (HTTP ${e.status}) — check the consumer key and secret"
        404 -> "WooCommerce API not found (404) — check the site URL"
        in 500..599 -> "Store error (HTTP ${e.status}) — try again shortly"
        else -> e.message
    }
}
