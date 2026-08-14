package me.sourov.quicksale.data.remote

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * How an order's address reads on the order screen.
 *
 * WooCommerce sends every field whether or not the country uses it, so the difference between a
 * good address block and a bad one is entirely in what gets dropped: a Swiss address has no state,
 * a counter sale has no delivery address at all, and printing either as an empty row is how a
 * screen ends up with gaps nobody can explain.
 */
class OrderAddressTest {

    @Test
    fun a_swiss_address_reads_postcode_then_town_and_skips_the_empty_state() {
        val address = address(
            address1 = "Bahnhofstrasse 12",
            postcode = "4142",
            city = "Münchenstein",
            country = "CH",
        )

        assertEquals(
            listOf("Bahnhofstrasse 12", "4142 Münchenstein", "CH"),
            address.streetLines,
        )
    }

    @Test
    fun a_state_joins_the_town_rather_than_taking_its_own_line() {
        val address = address(
            address1 = "1 Market St",
            postcode = "94105",
            city = "San Francisco",
            state = "CA",
            country = "US",
        )

        assertEquals(
            listOf("1 Market St", "94105 San Francisco, CA", "US"),
            address.streetLines,
        )
    }

    @Test
    fun a_second_address_line_is_kept_and_a_blank_one_is_not() {
        val withUnit = address(address1 = "12 High St", address2 = "Unit 4", city = "Bern")
        val withoutUnit = address(address1 = "12 High St", city = "Bern")

        assertEquals(listOf("12 High St", "Unit 4", "Bern"), withUnit.streetLines)
        assertEquals(listOf("12 High St", "Bern"), withoutUnit.streetLines)
    }

    @Test
    fun a_counter_sale_carries_no_address_at_all() {
        // WooCommerce sends the shipping block empty rather than omitting it, and the screen has to
        // tell that apart from an address it merely failed to read.
        assertTrue(WooCommerceApi.OrderAddress.EMPTY.isEmpty)
        assertEquals(emptyList<String>(), WooCommerceApi.OrderAddress.EMPTY.streetLines)
    }

    @Test
    fun contact_details_alone_do_not_make_an_address() {
        // A billing block with only an email is not something to print as a destination.
        val contactOnly = WooCommerceApi.OrderAddress.EMPTY.copy(email = "ada@example.com")

        assertTrue(contactOnly.isEmpty)
    }

    @Test
    fun any_address_field_makes_it_real() {
        assertFalse(address(city = "Bern").isEmpty)
    }

    @Test
    fun the_name_joins_what_is_there_and_nothing_more() {
        assertEquals("Sascha Brenk", address(firstName = "Sascha", lastName = "Brenk").name)
        assertEquals("Ada", address(firstName = "Ada").name)
        assertEquals("", WooCommerceApi.OrderAddress.EMPTY.name)
    }

    private fun address(
        firstName: String = "",
        lastName: String = "",
        company: String = "",
        address1: String = "",
        address2: String = "",
        city: String = "",
        state: String = "",
        postcode: String = "",
        country: String = "",
    ) = WooCommerceApi.OrderAddress(
        firstName = firstName,
        lastName = lastName,
        company = company,
        address1 = address1,
        address2 = address2,
        city = city,
        state = state,
        postcode = postcode,
        country = country,
        email = "",
        phone = "",
    )
}
