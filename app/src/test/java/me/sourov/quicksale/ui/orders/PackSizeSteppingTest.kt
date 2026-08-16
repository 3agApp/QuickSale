package me.sourov.quicksale.ui.orders

import me.sourov.quicksale.data.local.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a quantity moves, on both screens that move one.
 *
 * The two screens agree on +: it adds a case, so a quantity built at the till is one the store
 * will take. They part company on −. The order-edit screen writes to an order the store already
 * holds, and a save off the lattice is refused after the correction has been promised to the
 * customer, so it stays on the case. The till's cart is not posted until "Place order", so − there
 * is one unit and the disagreement is shown rather than prevented.
 */
class PackSizeSteppingTest {

    /** Sold in sixes: + at the till adds a case. */
    @Test
    fun a_cart_line_moves_up_by_the_case() {
        val sixes = product(min = 6, step = 6)

        assertEquals(12, CartLine(sixes, 6).stepped(+1).quantity)
        // Off the lattice, + comes back onto it rather than carrying the odd unit upward.
        assertEquals(6, CartLine(sixes, 5).stepped(+1).quantity)
    }

    /**
     * − at the till, on the other hand, is one unit — the counter has to be able to say "five" for
     * a case with a damaged unit in it, whatever the store sells in.
     */
    @Test
    fun a_cart_line_comes_down_one_unit_at_a_time() {
        val sixes = product(min = 6, step = 6)

        assertEquals(11, CartLine(sixes, 12).lowered().quantity)
        assertEquals(5, CartLine(sixes, 6).lowered().quantity)
        // Only zero takes the product off the order; the caller drops it there.
        assertEquals(0, CartLine(sixes, 1).lowered().quantity)
    }

    /** What the line says about a quantity the store wouldn't sell — it is said, not prevented. */
    @Test
    fun an_off_pack_quantity_names_the_ones_the_store_takes() {
        val sixes = product(min = 6, step = 6)

        assertEquals(null, CartLine(sixes, 12).packSizeNote)
        assertEquals("Store sells 6 or 12, not 7", CartLine(sixes, 7).packSizeNote)
        // Short of a single pack there is no lower quantity to name, only the pack itself.
        assertEquals("Store sells 6, not 5", CartLine(sixes, 5).packSizeNote)
        // A minimum larger than the step (2/1 exists in the live catalog) reads the same way.
        assertEquals("Store sells 2, not 1", CartLine(product(min = 2, step = 1), 1).packSizeNote)
        assertEquals(null, CartLine(product(min = 2, step = 1), 3).packSizeNote)
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

    /**
     * A cart read back from disk keeps the quantity it was left with, pack rule or not.
     *
     * It used to be rounded onto the lattice, from when the till couldn't produce an off-lattice
     * quantity in the first place. Now that − can, rounding on restore would quietly undo the
     * correction the operator made before the app was killed.
     */
    @Test
    fun a_restored_line_keeps_the_quantity_it_was_left_with() {
        val sixes = product(min = 6, step = 6)

        assertEquals(12, CartLine.restored(sixes, 12).quantity)
        assertEquals(11, CartLine.restored(sixes, 11).quantity)
        // Short of a pack, too — the note on the line is what tells the operator.
        assertEquals(5, CartLine.restored(sixes, 5).quantity)
        assertEquals(1, CartLine.restored(product(min = 4, step = 3), 1).quantity)
    }

    /** A store's leftover zero is a missing rule, not a pack of none — the first scan rings up 1. */
    @Test
    fun a_zero_rule_rings_up_one_rather_than_nothing() {
        val sloppy = product(min = 0, step = 0)

        assertEquals(1, sloppy.packSize)
        assertEquals(1, sloppy.quantityStep)
        assertEquals(2, CartLine(sloppy, 1).stepped(+1).quantity)
        assertFalse(CartLine(sloppy, 1).canStepDown)
        assertEquals(null, CartLine(sloppy, 1).packSizeNote)
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
