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
    /** The display name, as the shop prints it. Show this. */
    val name: String,
    /**
     * The given name behind [name], as stored on the WordPress account.
     *
     * Kept alongside the display name because it is the field an edit sends, and [name] cannot
     * stand in for it: splitting on the first space gets "Anna Maria" and "van der Berg" wrong, and
     * an edit form that silently reshaped somebody's name would be worse than not offering one.
     * Blank on rows last synced before the store began sending it.
     */
    val firstName: String = "",
    /** The surname on the WordPress account. Blank on rows synced before the store sent it. */
    val lastName: String = "",
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

    /**
     * Just the given name, for addressing this person in a button or a sentence.
     *
     * Prefers the stored [firstName] and falls back to the display name's first word, which is all
     * a row synced before the store sent the field has.
     */
    val givenName: String get() = firstName.ifBlank { name.trim().substringBefore(' ') }

    /**
     * The surname, for filling an edit form. Same fallback as [givenName], and safe for the same
     * reason: a split that reads "Anna Maria Schmidt" wrong is only ever shown, never sent — an
     * edit sends the fields the operator actually changed.
     */
    val familyName: String get() = lastName.ifBlank { name.trim().substringAfter(' ', "").trim() }

    /**
     * What this person may do *on their own account*, named so it can't be read as anything larger.
     *
     * "Admin" alone reads as an administrator of the shop — someone with the run of the store — when
     * what it actually means is a buyer who may add a colleague to their own company. The two are a
     * long way apart, and the label is the only thing on screen that distinguishes them.
     *
     * There is no matching "Company member": the plain role needs no qualifying, and only ever
     * appears on a page that has already said which company is being talked about.
     */
    val roleLabel: String get() = if (isAdmin) "Company admin" else "Member"

    /** A membership is [STATUS_ACTIVE] or `inactive`; only an active one may be sold to. */
    val isActive: Boolean get() = status.equals(STATUS_ACTIVE, ignoreCase = true)

    val statusLabel: String get() = if (isActive) "Active" else "Inactive"

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
        const val STATUS_ACTIVE = "active"
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

    /**
     * This address under WooCommerce's own field names — the keys the address form renders and the
     * order's `shipping` block is submitted with.
     *
     * A location is a WooCommerce shipping address column for column, so filling a form from one is
     * a rename and nothing more. [name] and [isDefault] are deliberately absent: they label the
     * location, they are not part of the address.
     */
    fun toAddressFields(): Map<String, String> = mapOf(
        "first_name" to firstName,
        "last_name" to lastName,
        "company" to company,
        "address_1" to address1,
        "address_2" to address2,
        "city" to city,
        "state" to state,
        "postcode" to postcode,
        "country" to country,
        "phone" to phone,
    )

    /**
     * True when [values] is still this location's own address.
     *
     * This is what decides whether an order names the location by ID or posts a typed address, so it
     * has to answer the question the operator would: a field the current country's form doesn't
     * render is absent from [values] rather than blank, and absent-versus-blank is not a change.
     * Surrounding whitespace isn't a change either — the store trims it too.
     */
    fun matchesAddress(values: Map<String, String>): Boolean =
        toAddressFields().all { (field, saved) ->
            saved.trim() == values[field].orEmpty().trim()
        }
}
