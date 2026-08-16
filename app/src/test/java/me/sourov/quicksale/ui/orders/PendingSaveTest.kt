package me.sourov.quicksale.ui.orders

import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.settings.CheckoutConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What an order edit will do, and what the counter is told it will do.
 *
 * Both come out of one function on purpose, and these tests hold them to each other: every entry
 * the confirmation shows has a request behind it, and nothing is sent that the confirmation didn't
 * mention. A dialog that overstates is one that gets waved through; one that understates changes an
 * order behind someone's back, after a price has already been quoted to the customer.
 */
class PendingSaveTest {

    @Test
    fun `an untouched edit has nothing to send and nothing to say`() {
        val pending = pendingSave(
            original = order(item(id = 1, productId = 10, name = "Notebook", quantity = 2)),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 2)),
            shipping = null,
            shippingEdited = false,
            config = CheckoutConfig(),
        )

        assertTrue(pending.summary.isEmpty())
        assertTrue(pending.lineItems.isEmpty())
        assertNull(pending.shipping)
    }

    @Test
    fun `a quantity change is sent by id and read back as before and after`() {
        val pending = pendingSave(
            original = order(item(id = 1, productId = 10, name = "Notebook", quantity = 6)),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 5)),
            shipping = null,
            shippingEdited = false,
            config = CheckoutConfig(),
        )

        val sent = pending.lineItems.single()
        assertEquals(1L, sent.id)
        assertEquals(5, sent.quantity)
        // Priced explicitly: WooCommerce only auto-prices a line when it is created.
        assertEquals("6.00", sent.total)

        val said = pending.summary.single()
        assertEquals(OrderChange.Kind.CHANGED, said.kind)
        assertEquals("Notebook", said.label)
        assertTrue(said.detail, said.detail.startsWith("6 → 5"))
    }

    @Test
    fun `a dropped line is sent as a zero quantity and named as removed`() {
        val pending = pendingSave(
            original = order(
                item(id = 1, productId = 10, name = "Notebook", quantity = 2),
                item(id = 2, productId = 20, name = "Stickerblock", quantity = 6),
            ),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 2)),
            shipping = null,
            shippingEdited = false,
            config = CheckoutConfig(),
        )

        val sent = pending.lineItems.single()
        assertEquals(2L, sent.id)
        assertEquals(0, sent.quantity)

        val said = pending.summary.single()
        assertEquals(OrderChange.Kind.REMOVED, said.kind)
        assertEquals("Stickerblock", said.label)
        assertEquals("was 6", said.detail)
    }

    @Test
    fun `a product added during the edit is sent without an id`() {
        val pending = pendingSave(
            original = order(item(id = 1, productId = 10, name = "Notebook", quantity = 2)),
            lines = listOf(
                line(itemId = 1, productId = 10, name = "Notebook", quantity = 2),
                line(itemId = null, productId = 20, name = "Stickerblock", quantity = 6),
            ),
            shipping = null,
            shippingEdited = false,
            config = CheckoutConfig(),
        )

        val sent = pending.lineItems.single()
        assertNull(sent.id)
        assertEquals(20L, sent.productId)
        assertEquals(6, sent.quantity)

        assertEquals(OrderChange.Kind.ADDED, pending.summary.single().kind)
        assertEquals("Stickerblock", pending.summary.single().label)
    }

    /** The reason the flag exists: a shipping line nobody touched must not be re-priced. */
    @Test
    fun `untouched shipping is not sent even though the order has some`() {
        val pending = pendingSave(
            original = order(
                item(id = 1, productId = 10, name = "Notebook", quantity = 2),
                shippingLine = WooCommerceApi.OrderShippingLine(9, "flat_rate", "Flat rate", "10.00"),
            ),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 1)),
            shipping = EditableShipping("flat_rate", "Flat rate", "10.00", taxable = true),
            shippingEdited = false,
            config = CheckoutConfig(),
        )

        assertNull(pending.shipping)
        // Only the quantity change is announced — shipping is not mentioned because it is not sent.
        assertEquals(1, pending.summary.size)
        assertEquals(OrderChange.Kind.CHANGED, pending.summary.single().kind)
    }

    @Test
    fun `a touched shipping cost is sent against the existing line`() {
        val pending = pendingSave(
            original = order(
                item(id = 1, productId = 10, name = "Notebook", quantity = 2),
                shippingLine = WooCommerceApi.OrderShippingLine(9, "flat_rate", "Flat rate", "10.00"),
            ),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 2)),
            shipping = EditableShipping("flat_rate", "Flat rate", "12.00", taxable = true),
            shippingEdited = true,
            config = CheckoutConfig(),
        )

        val sent = pending.shipping as WooCommerceApi.ShippingChange.Set
        assertEquals(9L, sent.lineId)
        assertEquals("12.00", sent.total)

        val said = pending.summary.single()
        assertEquals(OrderChange.Kind.CHANGED, said.kind)
        assertEquals("Shipping", said.label)
        assertTrue(said.detail, said.detail.startsWith("Flat rate"))
    }

    @Test
    fun `clearing shipping removes the line the order already had`() {
        val pending = pendingSave(
            original = order(
                item(id = 1, productId = 10, name = "Notebook", quantity = 2),
                shippingLine = WooCommerceApi.OrderShippingLine(9, "flat_rate", "Flat rate", "10.00"),
            ),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 2)),
            shipping = null,
            shippingEdited = true,
            config = CheckoutConfig(),
        )

        assertEquals(
            WooCommerceApi.ShippingChange.Remove(9L),
            pending.shipping,
        )
        assertEquals(OrderChange.Kind.REMOVED, pending.summary.single().kind)
        assertEquals("Shipping", pending.summary.single().label)
    }

    /** Nothing to remove is not a removal — an order that never had shipping sends nothing. */
    @Test
    fun `clearing shipping on an order that had none sends nothing`() {
        val pending = pendingSave(
            original = order(item(id = 1, productId = 10, name = "Notebook", quantity = 2)),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 2)),
            shipping = null,
            shippingEdited = true,
            config = CheckoutConfig(),
        )

        assertNull(pending.shipping)
        assertTrue(pending.summary.isEmpty())
    }

    @Test
    fun `shipping added where there was none is sent without a line id`() {
        val pending = pendingSave(
            original = order(item(id = 1, productId = 10, name = "Notebook", quantity = 2)),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 2)),
            shipping = EditableShipping("flat_rate", "Flat rate", "12.00", taxable = true),
            shippingEdited = true,
            config = CheckoutConfig(),
        )

        val sent = pending.shipping as WooCommerceApi.ShippingChange.Set
        assertNull(sent.lineId)
        assertEquals(OrderChange.Kind.ADDED, pending.summary.single().kind)
    }

    /** On a tax-inclusive store the quoted figure is grossed, and what is stored is not. */
    @Test
    fun `a tax inclusive store sends the shipping charge net`() {
        val pending = pendingSave(
            original = order(item(id = 1, productId = 10, name = "Notebook", quantity = 2)),
            lines = listOf(line(itemId = 1, productId = 10, name = "Notebook", quantity = 2)),
            shipping = EditableShipping("flat_rate", "Flat rate", "10.81", taxable = true),
            shippingEdited = true,
            config = CheckoutConfig(
                taxesEnabled = true,
                pricesIncludeTax = true,
                standardTaxRatePercent = 8.1,
            ),
        )

        assertEquals("10.00", (pending.shipping as WooCommerceApi.ShippingChange.Set).total)
    }

    private fun order(
        vararg items: WooCommerceApi.OrderLineItem,
        shippingLine: WooCommerceApi.OrderShippingLine? = null,
    ) = WooCommerceApi.OrderDetail(
        id = 14834,
        number = "14834",
        status = "on-hold",
        dateCreatedGmt = "2026-08-16T10:00:00",
        customerId = 7,
        total = "0.00",
        totalTax = "0.00",
        shippingTotal = shippingLine?.total ?: "0.00",
        discountTotal = "0.00",
        organizationName = "Acme GmbH",
        locationName = "Warehouse North",
        lineItems = items.toList(),
        shippingLine = shippingLine,
    )

    private fun item(id: Long, productId: Long, name: String, quantity: Int) =
        WooCommerceApi.OrderLineItem(
            id = id,
            productId = productId,
            name = name,
            sku = "sku-$productId",
            quantity = quantity,
            price = "1.20",
            total = "0.00",
        )

    private fun line(itemId: Long?, productId: Long, name: String, quantity: Int) = EditableLine(
        localKey = itemId ?: -1L,
        itemId = itemId,
        productId = productId,
        name = name,
        sku = "sku-$productId",
        unitPrice = "1.20",
        quantity = quantity,
    )
}
