package me.sourov.quicksale.data.local

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * What quantities a product can actually be ordered in.
 *
 * The store refuses an order whose quantity is off the product's pack size, and it refuses it at
 * the very end — after the operator has rung everything up and picked a delivery. Snapping is what
 * keeps the counter from getting there, so it is asserted rather than trusted to the +/- buttons.
 */
class ProductPackSizeTest {

    @Test
    fun an_unrestricted_product_takes_any_whole_quantity() {
        val product = product(minOrderQuantity = 1, orderQuantityStep = 1)

        assertEquals(1, product.snapOrderQuantity(1))
        assertEquals(7, product.snapOrderQuantity(7))
        // Below one unit there is no order left to place.
        assertEquals(0, product.snapOrderQuantity(0))
    }

    @Test
    fun a_quantity_between_two_cases_falls_back_to_the_lower_one() {
        val product = product(minOrderQuantity = 6, orderQuantityStep = 6)

        assertEquals(6, product.snapOrderQuantity(6))
        assertEquals(6, product.snapOrderQuantity(11))
        assertEquals(12, product.snapOrderQuantity(12))
        assertEquals(12, product.snapOrderQuantity(17))
    }

    /** A minimum and a step that differ: the lattice starts at the minimum, not at the step. */
    @Test
    fun the_steps_are_counted_from_the_minimum_not_from_zero() {
        val product = product(minOrderQuantity = 4, orderQuantityStep = 3)

        assertEquals(4, product.snapOrderQuantity(4))
        assertEquals(4, product.snapOrderQuantity(6))
        assertEquals(7, product.snapOrderQuantity(7))
        assertEquals(10, product.snapOrderQuantity(11))
    }

    /** Short of the pack size there is nothing to sell — the caller drops the line. */
    @Test
    fun anything_below_the_minimum_snaps_to_nothing() {
        val product = product(minOrderQuantity = 6, orderQuantityStep = 6)

        assertEquals(0, product.snapOrderQuantity(5))
        assertEquals(0, product.snapOrderQuantity(1))
        assertEquals(0, product.snapOrderQuantity(-3))
    }

    /**
     * A store that leaves a zero behind in either field means "no rule", and a zero step would
     * otherwise divide by zero on the first tap of the + button.
     */
    @Test
    fun a_zero_rule_behaves_as_no_rule_at_all() {
        val product = product(minOrderQuantity = 0, orderQuantityStep = 0)

        assertEquals(1, product.snapOrderQuantity(1))
        assertEquals(5, product.snapOrderQuantity(5))
        assertEquals(0, product.snapOrderQuantity(0))
    }

    private fun product(minOrderQuantity: Int, orderQuantityStep: Int) = Product(
        id = 1,
        name = "Classic Cotton T-Shirt",
        brand = "Northwind",
        sku = "TSHIRT-001",
        ean = "4006381333931",
        price = "19.99",
        regularPrice = "19.99",
        salePrice = "",
        msrp = "",
        stockStatus = "instock",
        stockQuantity = 120,
        imageUrl = null,
        categories = "Apparel",
        description = "",
        minOrderQuantity = minOrderQuantity,
        orderQuantityStep = orderQuantityStep,
    )
}
