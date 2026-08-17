package me.sourov.quicksale.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The rules an order placed at a stand is created under.
 *
 * Which queue it joins is the shop's choice, and the two it may choose between are pinned here
 * because both have consequences past this app: `processing` is what releases an order to Kontor,
 * and `on-hold` is what holds it back. Being *paid* is not offered at all — a till marking an order
 * paid is a till deciding the shop's books.
 */
class OrderOutcomeTest {

    @Test
    fun `a new order is processing unless the till says otherwise`() {
        assertEquals(NewOrderStatus.PROCESSING, NewOrderStatus.DEFAULT)
        // Nothing stored, and anything stored against the older setting's menu, lands on default.
        assertEquals(NewOrderStatus.DEFAULT, NewOrderStatus.fromSlug(null))
        assertEquals(NewOrderStatus.DEFAULT, NewOrderStatus.fromSlug("completed"))
    }

    @Test
    fun `each choice is a WooCommerce slug that survives the round trip to disk`() {
        assertEquals("processing", NewOrderStatus.PROCESSING.slug)
        assertEquals("on-hold", NewOrderStatus.ON_HOLD.slug)
        // Both round-trip through the stored slug.
        NewOrderStatus.entries.forEach { status ->
            assertEquals(status, NewOrderStatus.fromSlug(status.slug))
        }
    }

    @Test
    fun `every choice says at checkout where the order lands`() {
        assertTrue(NewOrderStatus.entries.all { it.checkoutSummary.isNotBlank() })
    }

    @Test
    fun `no order is ever marked paid`() {
        // `set_paid` would run WooCommerce's own payment_complete(), which stamps a payment date
        // and moves the order on regardless of the status sent with it.
        assertFalse(OrderOutcome.SET_PAID)
    }
}
