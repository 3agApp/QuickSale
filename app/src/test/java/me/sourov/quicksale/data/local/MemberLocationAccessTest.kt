package me.sourov.quicksale.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `location_access` is the one field where getting the default backwards silently shows the wrong
 * addresses at the counter: in storage an empty access list means *unrestricted*, so anything that
 * doesn't parse into real IDs has to read as "all", never as "none".
 */
class MemberLocationAccessTest {

    private fun member(locationAccess: String) = Member(
        memberId = 1,
        organizationId = 12,
        userId = 45,
        name = "Grace Hopper",
        email = "grace@acme.example",
        role = "admin",
        status = "active",
        canPlaceOrders = true,
        locationAccess = locationAccess,
    )

    @Test
    fun `all means unrestricted`() {
        assertNull(member("all").allowedLocationIds)
    }

    @Test
    fun `a blank value means unrestricted, not locked out`() {
        assertNull(member("").allowedLocationIds)
        assertNull(member("   ").allowedLocationIds)
    }

    @Test
    fun `an id list restricts to exactly those ids`() {
        assertEquals(setOf(3L, 5L), member("3,5").allowedLocationIds)
    }

    @Test
    fun `whitespace around ids is tolerated`() {
        assertEquals(setOf(3L, 5L), member(" 3 , 5 ").allowedLocationIds)
    }

    @Test
    fun `a list that parses to nothing falls back to unrestricted`() {
        // Better to offer every location and let the server refuse one than to hide them all and
        // strand the operator with no way to complete a legitimate order.
        assertNull(member("banana,").allowedLocationIds)
    }

    @Test
    fun `canUseLocation follows the access list`() {
        val restricted = member("3")
        assertTrue(restricted.canUseLocation(3))
        assertFalse(restricted.canUseLocation(4))

        val unrestricted = member("all")
        assertTrue(unrestricted.canUseLocation(3))
        assertTrue(unrestricted.canUseLocation(999))
    }
}

class OrganizationStatusTest {

    @Test
    fun `only active organizations may trade`() {
        assertTrue(OrganizationStatus.fromSlug("active").canTrade)
        assertFalse(OrganizationStatus.fromSlug("pending").canTrade)
        assertFalse(OrganizationStatus.fromSlug("suspended").canTrade)
        assertFalse(OrganizationStatus.fromSlug("rejected").canTrade)
    }

    @Test
    fun `an unrecognised status is unknown rather than guessed`() {
        // A status the app doesn't know about must not be optimistically treated as tradeable.
        val status = OrganizationStatus.fromSlug("archived")
        assertEquals(OrganizationStatus.UNKNOWN, status)
        assertFalse(status.canTrade)
    }

    @Test
    fun `a missing status is unknown`() {
        assertEquals(OrganizationStatus.UNKNOWN, OrganizationStatus.fromSlug(null))
    }
}
