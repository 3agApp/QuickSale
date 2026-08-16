package me.sourov.quicksale.data.settings

import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

/**
 * Turning the shipping charge an operator quotes into the one WooCommerce stores, and back.
 *
 * WooCommerce holds `shipping_lines.total` net and adds tax on top. The till and the order editor
 * both work in gross, because gross is what the customer was told — so every crossing of that
 * boundary goes through these two, and getting one of them wrong charges a delivery at the wrong
 * price in exactly one of the two screens, which is the hardest kind of pricing bug to notice.
 */
class ShippingTaxTest {

    private val inclusive = CheckoutConfig(
        taxesEnabled = true,
        pricesIncludeTax = true,
        standardTaxRatePercent = 8.1,
    )

    @Test
    fun `a tax inclusive store posts the charge net of tax`() {
        // 10.81 gross at 8.1% is 10.00 net; the customer pays the 10.81 they were quoted.
        assertEquals(
            BigDecimal("10.00"),
            inclusive.shippingNet(BigDecimal("10.81"), taxable = true),
        )
    }

    @Test
    fun `and reads a stored net charge back as the same gross figure`() {
        assertEquals(
            BigDecimal("10.81"),
            inclusive.shippingGross(BigDecimal("10.00"), taxable = true),
        )
    }

    /**
     * The round trip an untouched shipping line would make if it were ever resent. It holds here,
     * but the editor still refuses to resend an untouched line rather than rely on that: the
     * property is rounding-dependent, and a delivery quietly re-priced by a cent is worse than a
     * request not made.
     */
    @Test
    fun `net survives a trip out to gross and back`() {
        listOf("5.00", "10.00", "12.50", "7.41").forEach { stored ->
            val net = BigDecimal(stored)
            val gross = inclusive.shippingGross(net, taxable = true)
            assertEquals(net, inclusive.shippingNet(gross, taxable = true))
        }
    }

    @Test
    fun `a method the store does not tax is stored exactly as entered`() {
        assertEquals(
            BigDecimal("10.81"),
            inclusive.shippingNet(BigDecimal("10.81"), taxable = false),
        )
    }

    /** Most stores enter prices net, and then the two figures are simply the same figure. */
    @Test
    fun `a tax exclusive store converts nothing`() {
        val exclusive = inclusive.copy(pricesIncludeTax = false)

        assertEquals(BigDecimal("10.81"), exclusive.shippingNet(BigDecimal("10.81"), taxable = true))
        assertEquals(BigDecimal("10.00"), exclusive.shippingGross(BigDecimal("10.00"), taxable = true))
    }

    /** A store with no tax rate synced yet must not invent one and shave the charge. */
    @Test
    fun `no configured rate converts nothing`() {
        val unknown = inclusive.copy(standardTaxRatePercent = null)

        assertEquals(BigDecimal("10.81"), unknown.shippingNet(BigDecimal("10.81"), taxable = true))
    }

    @Test
    fun `taxes switched off convert nothing`() {
        val untaxed = inclusive.copy(taxesEnabled = false)

        assertEquals(BigDecimal("10.81"), untaxed.shippingNet(BigDecimal("10.81"), taxable = true))
    }
}
