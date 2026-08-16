package me.sourov.quicksale.ui.orders

import me.sourov.quicksale.data.local.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How a quantity moves, on both screens that move one — and they move it identically.
 *
 * + adds a case, so the quantity a counter reaches by tapping is one the store sells in. − comes
 * down a single unit and says so on the line, because a case with a damaged unit in it has to be
 * recordable. Neither screen refuses an off-pack quantity: the Kontor plugin enforces its rule on
 * the storefront cart alone, so nothing the app posts is measured against it.
 *
 * The two are tested side by side deliberately. They were once different — the editor held − to the
 * case — and an order the counter can build but not then correct is the failure this file exists to
 * catch.
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

    /** The same product and the same buttons, on an order that has already been placed. */
    @Test
    fun an_order_line_moves_the_same_way_the_cart_does() {
        val line = editableLine(quantity = 6, packSize = 6, quantityStep = 6)

        assertEquals(12, line.stepped(+1).quantity)
        assertEquals(5, line.lowered().quantity)
        assertEquals(11, line.copy(quantity = 12).lowered().quantity)
        assertEquals(0, line.copy(quantity = 1).lowered().quantity)
    }

    /** And says the same sentence about it, word for word — the note comes from one function. */
    @Test
    fun an_order_line_reads_the_same_note_as_a_cart_line() {
        val sixes = product(min = 6, step = 6)

        listOf(5, 7, 12).forEach { quantity ->
            assertEquals(
                CartLine(sixes, quantity).packSizeNote,
                editableLine(quantity, packSize = 6, quantityStep = 6).packSizeNote,
            )
        }
        assertEquals(
            "Store sells 6 or 12, not 7",
            editableLine(quantity = 7, packSize = 6, quantityStep = 6).packSizeNote,
        )
    }

    /**
     * A line billed before the store had a pack size at all. + is what brings it onto the lattice;
     * − leaves it where the operator puts it, saying what the store would rather have.
     */
    @Test
    fun a_line_that_was_never_on_the_lattice_is_not_forced_onto_it() {
        val line = editableLine(quantity = 7, packSize = 6, quantityStep = 6)

        assertEquals(6, line.lowered().quantity)
        assertEquals(12, line.stepped(+1).quantity)
        assertTrue(line.lowered().quantity > 0)
    }

    /** A product this device has no catalog row for still steps one unit at a time. */
    @Test
    fun an_unknown_product_steps_in_ones() {
        val line = editableLine(quantity = 3)

        assertEquals(4, line.stepped(+1).quantity)
        assertEquals(2, line.lowered().quantity)
        assertEquals(0, line.copy(quantity = 1).lowered().quantity)
        assertEquals(null, line.packSizeNote)
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
