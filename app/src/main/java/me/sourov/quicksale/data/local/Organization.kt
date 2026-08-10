package me.sourov.quicksale.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * One organization from the store's snapshot (`GET /wc-woap/v1/organizations`), with its members
 * and locations stored alongside in [Member] and [OrgLocation].
 *
 * The snapshot is replaced wholesale on every sync that reports a change, so anything missing from
 * a fresh snapshot has been deleted on the store.
 */
@Entity(tableName = "organizations")
data class Organization(
    @PrimaryKey val id: Long,
    val name: String,
    /** `pending`, `active`, `suspended` or `rejected`. */
    val status: String,
    /** When false, every delivery must name one of the organization's locations. */
    val allowCustomShipping: Boolean,
    /** The organization's centralised billing address, verbatim, for display only. */
    val billingJson: String,
    /** Billing address as WooCommerce prints it for its country. Newline separated. */
    val billingFormatted: String,
    // Denormalised from billing so the list can be searched without parsing JSON per row.
    val email: String,
    val phone: String,
    val city: String,
    val country: String,
    val dateModifiedGmt: String,
) {
    val orgStatus: OrganizationStatus get() = OrganizationStatus.fromSlug(status)

    val initials: String
        get() = name.split(' ', '-', '_')
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { "?" }
}

/**
 * The organization lifecycle states the API documents. An unknown slug maps to [UNKNOWN] rather
 * than being guessed at — the counter needs "this account is suspended", never a wrong answer.
 */
enum class OrganizationStatus(val slug: String, val label: String) {
    ACTIVE("active", "Active"),
    PENDING("pending", "Pending approval"),
    SUSPENDED("suspended", "Suspended"),
    REJECTED("rejected", "Rejected"),
    UNKNOWN("", "Unknown");

    /** Only an active organization can have orders placed against it. */
    val canTrade: Boolean get() = this == ACTIVE

    companion object {
        fun fromSlug(slug: String?): OrganizationStatus =
            entries.firstOrNull { it.slug == slug } ?: UNKNOWN
    }
}

/**
 * A person who may buy on an organization's behalf.
 *
 * [userId] is the WordPress user ID and the value to send as `customer_id` when creating an order;
 * [memberId] identifies the membership row and is never used in an order call.
 */
@Entity(
    tableName = "org_members",
    indices = [Index("organizationId")],
)
data class Member(
    @PrimaryKey val memberId: Long,
    val organizationId: Long,
    val userId: Long,
    val name: String,
    val email: String,
    /** The *organization* role — `admin` or `member` — not a WordPress role. */
    val role: String,
    val status: String,
    /**
     * The store's resolved answer to "may this person buy right now?". Displayed as given and
     * never re-derived: the inputs behind it are deliberately not exposed by the API, and the
     * server re-checks at order creation anyway.
     */
    val canPlaceOrders: Boolean,
    /**
     * Either [LOCATION_ACCESS_ALL] or a comma-joined list of location IDs. The API never sends an
     * empty list — in storage that means *unrestricted* — so a blank value is read as "all".
     */
    val locationAccess: String,
) {
    val isAdmin: Boolean get() = role.equals("admin", ignoreCase = true)

    val roleLabel: String get() = if (isAdmin) "Admin" else "Member"

    val initials: String
        get() = name.split(' ')
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifBlank { (email.firstOrNull()?.uppercaseChar() ?: '?').toString() }

    /** True when this member may be offered the location with [locationId]. */
    fun canUseLocation(locationId: Long): Boolean {
        val allowed = allowedLocationIds ?: return true
        return locationId in allowed
    }

    /** The location IDs this member is limited to, or null when unrestricted. */
    val allowedLocationIds: Set<Long>?
        get() {
            val raw = locationAccess.trim()
            if (raw.isBlank() || raw == LOCATION_ACCESS_ALL) return null
            val ids = raw.split(',').mapNotNull { it.trim().toLongOrNull() }.toSet()
            // A restriction that parsed to nothing is not a restriction we can trust; treat it as
            // unrestricted and let the server refuse, rather than silently hiding every location.
            return ids.ifEmpty { null }
        }

    companion object {
        const val LOCATION_ACCESS_ALL = "all"
    }
}

/**
 * A delivery address the organization has saved. Chosen by ID at order time — the app never posts
 * these fields, it posts `woap_location_id`.
 */
@Entity(
    tableName = "org_locations",
    indices = [Index("organizationId")],
)
data class OrgLocation(
    @PrimaryKey val id: Long,
    val organizationId: Long,
    /** The label the location is chosen by. */
    val name: String,
    val isDefault: Boolean,
    /** The address as WooCommerce prints it for its country. Newline separated — show this. */
    val formatted: String,
    val firstName: String,
    val lastName: String,
    val company: String,
    val address1: String,
    val address2: String,
    val city: String,
    val state: String,
    val postcode: String,
    val country: String,
    val phone: String,
) {
    /** A one-line version of [formatted], for rows too tight for the envelope layout. */
    val singleLine: String
        get() = formatted.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .joinToString(", ")
}
