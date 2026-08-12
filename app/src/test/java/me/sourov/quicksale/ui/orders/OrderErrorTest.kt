package me.sourov.quicksale.ui.orders

import me.sourov.quicksale.data.remote.WooApiException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Refusals are branched on the error `code`, never the `message` — messages are translated into
 * the site's locale and follow the shop's organization vocabulary, so the same refusal reads
 * "Branch" on one shop and "Campus" on another.
 */
class OrderErrorTest {

    private fun apiError(code: String, message: String = "Localised reason", status: Int = 400) =
        WooApiException(code, message, status)

    @Test
    fun `a purchase refusal keeps the store's reason and offers a re-sync`() {
        val error = OrderError.from(
            apiError(
                code = "woap_rest_cannot_purchase",
                message = "Acme GmbH is still awaiting approval.",
                status = 403,
            )
        )

        assertEquals("This member can't place orders right now", error.headline)
        assertEquals("Acme GmbH is still awaiting approval.", error.detail)
        // The store just disagreed with the local snapshot, so refreshing it is the useful action.
        assertTrue(error.suggestsSync)
    }

    @Test
    fun `a rejected destination suggests a re-sync`() {
        val error = OrderError.from(apiError("woap_rest_shipping_destination"))
        assertEquals("The store wouldn't deliver there", error.headline)
        assertTrue(error.suggestsSync)
    }

    @Test
    fun `a bad typed address is the operator's to fix, not a sync problem`() {
        val error = OrderError.from(apiError("woap_rest_shipping_address"))
        assertEquals("The delivery address needs fixing", error.headline)
        assertFalse(error.suggestsSync)
    }

    @Test
    fun `an unknown code still surfaces the store's message`() {
        val error = OrderError.from(apiError("woocommerce_rest_product_invalid_id", "No such product"))
        assertEquals("The store refused the order", error.headline)
        assertEquals("No such product", error.detail)
    }

    @Test
    fun `a plain exception is reported without pretending to know a code`() {
        val error = OrderError.from(IllegalStateException("Network unreachable"))
        assertEquals("Couldn't place the order", error.headline)
        assertEquals("Network unreachable", error.detail)
        assertFalse(error.suggestsSync)
    }

    @Test
    fun `combined reads as one sentence for a snackbar`() {
        val error = OrderError.from(apiError("woap_rest_shipping_address", "Postcode is not valid."))
        assertEquals("The delivery address needs fixing — Postcode is not valid.", error.combined)
    }
}
