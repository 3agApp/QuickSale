package me.sourov.quicksale.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Getting this mapping wrong bills real money the wrong way round: an invoice sale marked paid is
 * one nobody chases, and a card sale left on hold is one the customer is asked to pay twice.
 */
class OrderOutcomeTest {

    private fun gateway(id: String) = PaymentGateway(id, "Whatever the store calls it")

    @Test
    fun `bank transfer and cheque wait for the money`() {
        listOf("bacs", "cheque").forEach { id ->
            val outcome = OrderOutcome.forGateway(gateway(id))
            assertEquals("on-hold", outcome.status)
            assertFalse(outcome.setPaid)
        }
    }

    @Test
    fun `cash on delivery ships now and is paid later`() {
        val outcome = OrderOutcome.forGateway(gateway("cod"))
        assertEquals("processing", outcome.status)
        assertFalse(outcome.setPaid)
    }

    @Test
    fun `a paid gateway sends no status, so the store picks one`() {
        val outcome = OrderOutcome.forGateway(gateway("stripe"))
        assertNull(outcome.status)
        assertTrue(outcome.setPaid)
    }

    /** Gateway ids arrive from the store verbatim, and WooCommerce is not consistent about case. */
    @Test
    fun `ids are matched regardless of case`() {
        assertEquals(OrderOutcome.AWAITING_TRANSFER, OrderOutcome.forGateway(gateway("BACS")))
        assertEquals(OrderOutcome.PAY_ON_DELIVERY, OrderOutcome.forGateway(gateway("COD")))
    }

    /** No gateway means nothing is going to collect later, so the sale is treated as taken. */
    @Test
    fun `no gateway at all counts as paid`() {
        assertEquals(OrderOutcome.PAID, OrderOutcome.forGateway(null))
    }
}
