package me.sourov.quicksale.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/**
 * The one rule an order placed at a stand is created under.
 *
 * This used to be a mapping from the payment method — bank transfer on hold, cash on delivery
 * processing, everything else paid. It is now flat, and the flatness is the point: a till marking
 * an order paid is a till deciding the shop's books, and `processing` is also the status that
 * releases an order to Kontor. Both consequences follow from these two constants, so they are
 * pinned here rather than left to be re-derived from a payload.
 */
class OrderOutcomeTest {

    @Test
    fun `every order is created on hold`() {
        assertEquals("on-hold", OrderOutcome.STATUS)
    }

    @Test
    fun `no order is ever marked paid`() {
        // `set_paid` would run WooCommerce's own payment_complete(), which moves the order to
        // processing or completed and stamps a payment date — straight past the hold.
        assertFalse(OrderOutcome.SET_PAID)
    }
}
