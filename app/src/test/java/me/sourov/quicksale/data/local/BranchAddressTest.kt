package me.sourov.quicksale.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A branch fills the delivery form on the checkout, and whether the form still matches it decides
 * what the order request carries: the branch's ID, or a typed address the store validates from
 * scratch. Getting that comparison wrong is silent — the order goes through either way, to the
 * wrong place or under the wrong rules.
 */
class BranchAddressTest {

    private fun branch(
        firstName: String = "Grace",
        lastName: String = "Hopper",
        company: String = "",
        address1: String = "9 Lagerweg",
        address2: String = "",
        city: String = "Hamburg",
        state: String = "",
        postcode: String = "20095",
        country: String = "DE",
        phone: String = "+49 40 123456",
    ) = OrgLocation(
        id = 3,
        organizationId = 12,
        name = "Warehouse North",
        isDefault = true,
        formatted = "Grace Hopper\n9 Lagerweg\n20095 Hamburg",
        firstName = firstName,
        lastName = lastName,
        company = company,
        address1 = address1,
        address2 = address2,
        city = city,
        state = state,
        postcode = postcode,
        country = country,
        phone = phone,
    )

    @Test
    fun `every column is exported under its WooCommerce name`() {
        val fields = branch().toAddressFields()

        assertEquals("Grace", fields["first_name"])
        assertEquals("Hopper", fields["last_name"])
        assertEquals("", fields["company"])
        assertEquals("9 Lagerweg", fields["address_1"])
        assertEquals("", fields["address_2"])
        assertEquals("Hamburg", fields["city"])
        assertEquals("", fields["state"])
        assertEquals("20095", fields["postcode"])
        assertEquals("DE", fields["country"])
        assertEquals("+49 40 123456", fields["phone"])
    }

    /** The label and the default flag describe the branch, not the address it holds. */
    @Test
    fun `the branch's own name and default flag are not address fields`() {
        val fields = branch().toAddressFields()

        assertFalse(fields.containsKey("name"))
        assertFalse(fields.containsKey("is_default"))
    }

    @Test
    fun `a form filled straight from the branch still matches it`() {
        val subject = branch()

        assertTrue(subject.matchesAddress(subject.toAddressFields()))
    }

    @Test
    fun `one changed character is a change`() {
        val subject = branch()
        val edited = subject.toAddressFields() + ("address_1" to "9a Lagerweg")

        assertFalse(subject.matchesAddress(edited))
    }

    @Test
    fun `a different country is a change`() {
        val subject = branch()
        val edited = subject.toAddressFields() + ("country" to "AT")

        assertFalse(subject.matchesAddress(edited))
    }

    /**
     * A country whose form has no `state` field never puts one in the map. That absence must read
     * as "unchanged" rather than as an edit, or every German order would post a typed address.
     */
    @Test
    fun `a field the country's form omits is not a change when the branch has it blank`() {
        val subject = branch(state = "")
        val withoutState = subject.toAddressFields() - "state"

        assertTrue(subject.matchesAddress(withoutState))
    }

    /** But dropping a field the branch actually filled in *is* a change. */
    @Test
    fun `a field the branch filled in and the form dropped is a change`() {
        val subject = branch(state = "BY")
        val withoutState = subject.toAddressFields() - "state"

        assertFalse(subject.matchesAddress(withoutState))
    }

    /** The store trims what it stores, so trailing whitespace is not somebody changing an address. */
    @Test
    fun `surrounding whitespace is not a change`() {
        val subject = branch()
        val padded = subject.toAddressFields() + ("city" to "  Hamburg  ")

        assertTrue(subject.matchesAddress(padded))
    }
}
