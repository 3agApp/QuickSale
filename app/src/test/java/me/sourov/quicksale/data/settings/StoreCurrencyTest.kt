package me.sourov.quicksale.data.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.math.BigDecimal

/**
 * Prices have to come out of the app looking exactly as they do on the shop's own website — the
 * same number formatted two ways reads as two different prices to whoever is holding the label.
 *
 * Every case here is a real WooCommerce configuration, because the formatting settings are four
 * independent options and the combinations a European store uses are the ones a US-default
 * implementation gets wrong.
 */
class StoreCurrencyTest {

    /** WooCommerce out of the box: symbol left, no space, comma groups, two decimals. */
    @Test
    fun a_default_store_formats_like_woocommerce_does() {
        val currency = StoreCurrency(code = "USD", symbol = "$")

        assertEquals("$1,234.50", currency.format("1234.5"))
        assertEquals("$0.00", currency.format("0"))
        assertEquals("$999.99", currency.format("999.99"))
    }

    /** The German convention: swapped separators and the symbol trailing behind a space. */
    @Test
    fun a_german_store_swaps_the_separators_and_trails_the_symbol() {
        val currency = StoreCurrency(
            code = "EUR",
            symbol = "€",
            position = CurrencyPosition.RIGHT_SPACE,
            thousandSeparator = ".",
            decimalSeparator = ",",
        )

        assertEquals("1.234,50 €", currency.format("1234.5"))
        assertEquals("19,99 €", currency.format("19.99"))
    }

    /** Grouping with nothing at all is a setting stores really choose, not a missing value. */
    @Test
    fun an_empty_thousand_separator_leaves_the_digits_unbroken() {
        val currency = StoreCurrency(symbol = "kr", thousandSeparator = "")

        assertEquals("kr1234567.00", currency.format("1234567"))
    }

    /** A zero-decimal currency rounds to whole units and drops the separator with them. */
    @Test
    fun a_zero_decimal_currency_prints_no_fraction() {
        val currency = StoreCurrency(code = "JPY", symbol = "¥", decimals = 0)

        assertEquals("¥1,235", currency.format("1234.5"))
    }

    @Test
    fun rounding_follows_the_stores_decimal_count_rather_than_the_value() {
        val currency = StoreCurrency(symbol = "$", decimals = 2)

        assertEquals("$0.13", currency.format("0.125"))
        assertEquals("$10.00", currency.format("9.999"))
    }

    /** WooCommerce writes the sign ahead of everything, symbol included. */
    @Test
    fun a_negative_amount_keeps_its_sign_outside_the_symbol() {
        val currency = StoreCurrency(symbol = "$")

        assertEquals("-$5.00", currency.format(BigDecimal("-5")))
    }

    /** Grouping has to survive the boundaries either side of a group of three. */
    @Test
    fun groups_of_three_start_from_the_decimal_point() {
        val currency = StoreCurrency(symbol = "$")

        assertEquals("$100.00", currency.format("100"))
        assertEquals("$1,000.00", currency.format("1000"))
        assertEquals("$12,345.00", currency.format("12345"))
        assertEquals("$1,234,567.00", currency.format("1234567"))
    }

    /** No price is not a price of zero — the caller decides what to show instead. */
    @Test
    fun a_blank_amount_has_no_formatting() {
        assertNull(StoreCurrency().format(""))
        assertNull(StoreCurrency().format("   "))
    }

    /**
     * Whatever a store put in a price field, showing it beats dropping it: an unparsable value is
     * a catalog problem the counter can see and report, and a blank one is a bug report about us.
     */
    @Test
    fun an_unparsable_amount_is_shown_rather_than_swallowed() {
        assertEquals("\$on request", StoreCurrency(symbol = "$").format("on request"))
    }
}
