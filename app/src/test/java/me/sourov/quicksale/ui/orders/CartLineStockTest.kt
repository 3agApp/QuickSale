package me.sourov.quicksale.ui.orders

import me.sourov.quicksale.data.local.Product
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * How far past the shelf a line has gone.
 *
 * This is the number the whole backorder setting turns on: it decides whether a line warns, and —
 * with backorders switched off — whether the order can be placed at all. The store's own stock
 * fields are looser than they look (an uncounted product, a count already run negative, a product
 * the shop flags as backorderable), and reading any of those as a shortage would put a warning on
 * a line that has nothing wrong with it.
 */
class CartLineStockTest {

    @Test
    fun a_line_within_stock_is_not_short() {
        val line = CartLine(product(stockQuantity = 10), quantity = 4)

        assertEquals(10, line.product.availableStock)
        assertEquals(0, line.beyondStock)
    }

    @Test
    fun a_line_exactly_on_the_last_unit_is_not_short() {
        val line = CartLine(product(stockQuantity = 3), quantity = 3)

        assertEquals(0, line.beyondStock)
    }

    @Test
    fun a_line_past_stock_reports_only_the_shortfall() {
        val line = CartLine(product(stockQuantity = 2), quantity = 5)

        // Three units short, not five ordered — the shortfall is what has to be explained.
        assertEquals(3, line.beyondStock)
    }

    @Test
    fun an_uncounted_product_is_never_short() {
        // manage_stock off: WooCommerce sends no quantity, and the shop will supply any amount.
        val product = product(stockQuantity = null, stockStatus = "instock")

        assertNull(product.availableStock)
        assertEquals(0, CartLine(product, quantity = 999).beyondStock)
    }

    @Test
    fun a_backorderable_product_is_never_short() {
        // The shop itself says this may be ordered past its count, so the till has nothing to warn about.
        val product = product(stockQuantity = 0, stockStatus = Product.STOCK_ON_BACKORDER)

        assertNull(product.availableStock)
        assertEquals(0, CartLine(product, quantity = 12).beyondStock)
    }

    @Test
    fun an_out_of_stock_product_with_no_count_supplies_none() {
        val product = product(stockQuantity = null, stockStatus = Product.STOCK_OUT_OF_STOCK)

        assertEquals(0, product.availableStock)
        assertEquals(2, CartLine(product, quantity = 2).beyondStock)
    }

    @Test
    fun a_count_already_run_negative_reads_as_none_left() {
        // A store oversold elsewhere can hold -1. There is no less than nothing on the shelf, and
        // a shortfall of "4" on a line of 3 would be arithmetic nobody at the counter can act on.
        val product = product(stockQuantity = -1)

        assertEquals(0, product.availableStock)
        assertEquals(3, CartLine(product, quantity = 3).beyondStock)
    }

    @Test
    fun a_line_above_one_may_still_be_stepped_down() {
        assertEquals(true, CartLine(product(stockQuantity = 50), quantity = 2).canStepDown)
        assertEquals(true, CartLine(product(stockQuantity = 50), quantity = 9).canStepDown)
    }

    @Test
    fun the_last_one_may_not_be_stepped_down() {
        // Held − stops here; taking the product off the order is the bin button's job.
        assertEquals(false, CartLine(product(stockQuantity = 50), quantity = 1).canStepDown)
    }

    @Test
    fun a_pack_size_product_stops_at_its_pack_rather_than_at_one() {
        // Sold in sixes: 12 comes down to 6, and 6 is the last quantity that is still an order.
        val sixes = product(stockQuantity = 50).copy(minOrderQuantity = 6, orderQuantityStep = 6)

        assertEquals(true, CartLine(sixes, quantity = 12).canStepDown)
        assertEquals(false, CartLine(sixes, quantity = 6).canStepDown)
    }

    private fun product(
        stockQuantity: Int?,
        stockStatus: String = if ((stockQuantity ?: 0) > 0) "instock" else Product.STOCK_OUT_OF_STOCK,
    ) = Product(
        id = 1,
        name = "Abel blocks 24",
        brand = "Abel",
        sku = "abel-AB24",
        ean = "7426870707154",
        price = "46.29",
        regularPrice = "46.29",
        salePrice = "",
        msrp = "",
        stockStatus = stockStatus,
        stockQuantity = stockQuantity,
        imageUrl = null,
        categories = "Toys",
        description = "",
    )
}
