package me.sourov.quicksale.ui.orders

import me.sourov.quicksale.data.local.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a quantity moves, on both screens that move one.
 *
 * The till and the order-edit screen have to agree: an order the counter could never have *built*
 * off a product's pack size is one the store refuses when the correction is saved — and it refuses
 * it after the customer has been told what changed.
 */
class PackSizeSteppingTest {

    /** Sold in sixes: what the +/− buttons do at the till. */
    @Test
    fun a_cart_line_moves_by_the_case() {
        val sixes = product(min = 6, step = 6)

        assertEquals(12, CartLine(sixes, 6).stepped(+1).quantity)
        assertEquals(6, CartLine(sixes, 12).stepped(-1).quantity)
        // Below one case there is no line left; the caller drops it.
        assertEquals(0, CartLine(sixes, 6).stepped(-1).quantity)
    }

    /** The same product, the same buttons, on an order that has already been placed. */
    @Test
    fun an_order_line_moves_by_the_same_case() {
        val line = editableLine(quantity = 6, packSize = 6, quantityStep = 6)

        assertEquals(12, line.stepped(+1).quantity)
        assertEquals(6, line.copy(quantity = 12).stepped(-1).quantity)
        assertEquals(0, line.stepped(-1).quantity)
    }

    /**
     * A line billed before the store had a pack size at all. − brings it *onto* the lattice rather
     * than through it — one tap should correct 7 to 6, not take the product off the order.
     */
    @Test
    fun a_line_that_was_never_on_the_lattice_comes_down_onto_it() {
        val line = editableLine(quantity = 7, packSize = 6, quantityStep = 6)

        assertEquals(6, line.stepped(-1).quantity)
        assertEquals(12, line.stepped(+1).quantity)
        assertTrue(line.stepped(-1).quantity > 0)
    }

    /** A product this device has no catalog row for still steps one unit at a time. */
    @Test
    fun an_unknown_product_steps_in_ones() {
        val line = editableLine(quantity = 3)

        assertEquals(4, line.stepped(+1).quantity)
        assertEquals(2, line.stepped(-1).quantity)
        assertEquals(0, line.copy(quantity = 1).stepped(-1).quantity)
    }

    /** A cart read back from disk is measured against the pack size the catalog holds *now*. */
    @Test
    fun a_restored_line_is_snapped_to_the_rule_as_it_stands() {
        val sixes = product(min = 6, step = 6)

        assertEquals(12, CartLine.restored(sixes, 12).quantity)
        // Stored between two cases: rounded down to the one the store will sell.
        assertEquals(6, CartLine.restored(sixes, 11).quantity)
    }

    /**
     * Stored short of a case — the rule was introduced while the cart sat there. The line comes
     * back at one pack rather than vanishing: the operator scanned that product, and a quantity
     * they can see and correct beats a line silently missing from the order they place.
     */
    @Test
    fun a_restored_line_below_one_pack_comes_back_at_the_pack_size() {
        assertEquals(6, CartLine.restored(product(min = 6, step = 6), 5).quantity)
        assertEquals(4, CartLine.restored(product(min = 4, step = 3), 1).quantity)
    }

    /** A store's leftover zero is a missing rule, not a pack of none — the first scan rings up 1. */
    @Test
    fun a_zero_rule_rings_up_one_rather_than_nothing() {
        val sloppy = product(min = 0, step = 0)

        assertEquals(1, sloppy.packSize)
        assertEquals(1, sloppy.quantityStep)
        assertEquals(2, CartLine(sloppy, 1).stepped(+1).quantity)
        assertFalse(CartLine(sloppy, 1).canStepDown)
    }

    private fun editableLine(quantity: Int, packSize: Int = 1, quantityStep: Int = 1) = EditableLine(
        localKey = -1,
        itemId = 41,
        productId = 1,
        name = "Abel blocks 24",
        sku = "abel-AB24",
        unitPrice = "46.29",
        quantity = quantity,
        packSize = packSize,
        quantityStep = quantityStep,
    )

    private fun product(min: Int, step: Int) = Product(
        id = 1,
        name = "Abel blocks 24",
        brand = "Abel",
        sku = "abel-AB24",
        ean = "7426870707154",
        price = "46.29",
        regularPrice = "46.29",
        salePrice = "",
        msrp = "",
        stockStatus = "instock",
        stockQuantity = null,
        imageUrl = null,
        categories = "Toys",
        description = "",
        minOrderQuantity = min,
        orderQuantityStep = step,
    )
}
