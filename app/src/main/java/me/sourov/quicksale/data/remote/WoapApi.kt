package me.sourov.quicksale.data.remote

import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.settings.AddressField
import me.sourov.quicksale.data.settings.AddressForms
import me.sourov.quicksale.data.settings.StoreSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Client for the organization-accounts plugin's own routes.
 *
 * The namespace prefix (`wc-woap`) is load-bearing: WooCommerce's authentication only reads
 * consumer keys for request URIs containing `wc/` or `wc-`.
 */
class WoapApi(settings: StoreSettings) {

    private val http = WooHttp(settings)

    /**
     * One page of the organization snapshot, with the members and locations embedded.
     *
     * [notModified] means the page's ETag matched and the store sent no body — the caller should
     * leave its stored copy of this page alone.
     */
    class OrganizationPage(
        val organizations: List<Organization>,
        val members: List<Member>,
        val locations: List<OrgLocation>,
        val etag: String?,
        val totalPages: Int,
        val notModified: Boolean,
    )

    /**
     * Fetches one page of `GET /wc-woap/v1/organizations`.
     *
     * Pass the page's previously stored ETag as [ifNoneMatch] to let an unchanged page answer
     * `304` with no payload. The ETag covers the whole page, members and locations included, so a
     * change *below* the organization row still invalidates it.
     */
    suspend fun fetchOrganizations(
        page: Int,
        perPage: Int = ORGANIZATIONS_PER_PAGE,
        ifNoneMatch: String? = null,
    ): OrganizationPage {
        val response = http.get(
            path = "wc-woap/v1/organizations",
            query = mapOf("page" to page.toString(), "per_page" to perPage.toString()),
            ifNoneMatch = ifNoneMatch,
        )
        if (response.notModified) {
            return OrganizationPage(
                organizations = emptyList(),
                members = emptyList(),
                locations = emptyList(),
                etag = ifNoneMatch,
                totalPages = response.totalPages,
                notModified = true,
            )
        }

        val array = JSONArray(response.body)
        val organizations = mutableListOf<Organization>()
        val members = mutableListOf<Member>()
        val locations = mutableListOf<OrgLocation>()
        for (i in 0 until array.length()) {
            val json = array.optJSONObject(i) ?: continue
            val organization = json.toOrganization()
            organizations += organization
            members += json.optJSONArray("members").mapObjects { it.toMember(organization.id) }
            locations += json.optJSONArray("locations").mapObjects { it.toLocation(organization.id) }
        }
        return OrganizationPage(
            organizations = organizations,
            members = members,
            locations = locations,
            etag = response.etag,
            totalPages = response.totalPages,
            notModified = false,
        )
    }

    /** The result of an address-form fetch; [forms] is null when the stored copy is still current. */
    class AddressFormResult(val forms: AddressForms?, val etag: String?)

    /**
     * Fetches `GET /wc-woap/v1/address-form` — the shop's per-country shipping field definitions.
     * Revalidated the same way as the snapshot, and it changes rarely.
     */
    suspend fun fetchAddressForms(ifNoneMatch: String? = null): AddressFormResult {
        val response = http.get(path = "wc-woap/v1/address-form", ifNoneMatch = ifNoneMatch)
        if (response.notModified) return AddressFormResult(forms = null, etag = ifNoneMatch)

        val json = JSONObject(response.body)
        val countries = json.optJSONObject("countries").toStringMap()
        val formsJson = json.optJSONObject("forms")
        val forms = buildMap {
            formsJson?.keys()?.forEach { country ->
                val fields = formsJson.optJSONArray(country).mapObjects { it.toAddressField() }
                if (fields.isNotEmpty()) put(country, fields)
            }
        }
        return AddressFormResult(
            forms = AddressForms(
                defaultCountry = json.optString("default_country"),
                countries = countries,
                forms = forms,
            ),
            etag = response.etag,
        )
    }

    /**
     * The answer to a status write. [changed] is false when the organization already held that
     * status — a success, not an error: two people working the same queue, or one double-tap, must
     * not produce two approval emails.
     */
    class StatusChange(val changed: Boolean, val organization: Organization?)

    /**
     * Moves an organization through its lifecycle — `pending`, `active`, `suspended`, `rejected`.
     *
     * This is its own route rather than a field on the edit because it is what sends the shop's
     * approval and rejection mail; `PATCH`ing an organization with a status is refused outright, so
     * every status write in the app goes through here.
     */
    suspend fun setOrganizationStatus(organizationId: Long, status: String): StatusChange {
        val response = http.post(
            path = "wc-woap/v1/organizations/$organizationId/status",
            body = JSONObject().put("status", status),
        )
        val json = JSONObject(response.body)
        return StatusChange(
            changed = json.optBoolean("changed"),
            organization = json.optJSONObject("organization")?.toOrganization(),
        )
    }

    /**
     * Sets one membership's status — `active` or `inactive`.
     *
     * Only `status` is sent. Permissions are stored as a diff against the role, so echoing back a
     * capability map read for a previous role would pin the member to permissions their role has
     * moved away from.
     */
    suspend fun setMemberStatus(organizationId: Long, memberId: Long, status: String): Member {
        val response = http.patch(
            path = "wc-woap/v1/organizations/$organizationId/members/$memberId",
            body = JSONObject().put("status", status),
        )
        return JSONObject(response.body).toMember(organizationId)
    }

    /**
     * Adds a branch. [fields] carries WooCommerce's own shipping field names at the top level of
     * the body, matching how the snapshot reports them.
     *
     * `name` is required; a surname and a phone are not, even where the shop's checkout requires a
     * phone of a buyer. A blank `company` becomes the organization's name, stored rather than
     * resolved later.
     */
    suspend fun createLocation(
        organizationId: Long,
        name: String,
        isDefault: Boolean,
        fields: Map<String, String>,
    ): OrgLocation {
        val response = http.post(
            path = "wc-woap/v1/organizations/$organizationId/locations",
            body = locationBody(name, isDefault, fields),
        )
        return JSONObject(response.body).toLocation(organizationId)
    }

    /** Edits a branch. The edit is partial, but the merged address is validated whole. */
    suspend fun updateLocation(
        organizationId: Long,
        locationId: Long,
        name: String,
        isDefault: Boolean,
        fields: Map<String, String>,
    ): OrgLocation {
        val response = http.patch(
            path = "wc-woap/v1/organizations/$organizationId/locations/$locationId",
            body = locationBody(name, isDefault, fields),
        )
        return JSONObject(response.body).toLocation(organizationId)
    }

    private fun locationBody(
        name: String,
        isDefault: Boolean,
        fields: Map<String, String>,
    ): JSONObject = JSONObject().apply {
        put("name", name)
        put("is_default", isDefault)
        fields.forEach { (key, value) -> put(key, value) }
    }

    private fun JSONObject.toOrganization(): Organization {
        val billing = optJSONObject("billing")
        return Organization(
            id = optLong("id"),
            name = optString("name").decodeHtmlEntities(),
            status = optString("status"),
            allowCustomShipping = optBoolean("allow_custom_shipping"),
            billingJson = billing?.toString().orEmpty(),
            billingFormatted = optString("billing_formatted").stripHtmlKeepingLines(),
            email = billing?.optString("email").orEmpty(),
            phone = billing?.optString("phone").orEmpty(),
            city = billing?.optString("city").orEmpty(),
            country = billing?.optString("country").orEmpty(),
            dateModifiedGmt = optString("date_modified_gmt"),
        )
    }

    private fun JSONObject.toMember(organizationId: Long): Member = Member(
        memberId = optLong("member_id"),
        organizationId = organizationId,
        userId = optLong("user_id"),
        name = optString("name").decodeHtmlEntities(),
        email = optString("email"),
        role = optString("role"),
        status = optString("status"),
        canPlaceOrders = optBoolean("can_place_orders"),
        locationAccess = readLocationAccess(),
    )

    /**
     * `location_access` is the string `"all"` or an array of location IDs — never an empty array,
     * because in storage an empty access list means *unrestricted*. Anything unexpected is stored
     * as "all" so the server, not a parsing guess, decides what a member may reach.
     */
    private fun JSONObject.readLocationAccess(): String {
        val ids = optJSONArray("location_access") ?: return Member.LOCATION_ACCESS_ALL
        val values = buildList {
            for (i in 0 until ids.length()) {
                ids.optLong(i, 0L).takeIf { it > 0L }?.let { add(it) }
            }
        }
        return if (values.isEmpty()) Member.LOCATION_ACCESS_ALL else values.joinToString(",")
    }

    private fun JSONObject.toLocation(organizationId: Long): OrgLocation = OrgLocation(
        id = optLong("id"),
        organizationId = organizationId,
        name = optString("name").decodeHtmlEntities(),
        isDefault = optBoolean("is_default"),
        formatted = optString("formatted").stripHtmlKeepingLines(),
        firstName = optString("first_name"),
        lastName = optString("last_name"),
        company = optString("company"),
        address1 = optString("address_1"),
        address2 = optString("address_2"),
        city = optString("city"),
        state = optString("state"),
        postcode = optString("postcode"),
        country = optString("country"),
        phone = optString("phone"),
    )

    private fun JSONObject.toAddressField(): AddressField = AddressField(
        name = optString("name"),
        label = optString("label").decodeHtmlEntities(),
        required = optBoolean("required"),
        hidden = optBoolean("hidden"),
        type = optString("type").ifBlank { "text" },
        options = optJSONObject("options").toStringMap(),
    )

    private companion object {
        /** The route's documented ceiling is 200; anything above is a 400, not a silent clamp. */
        const val ORGANIZATIONS_PER_PAGE = 100

        fun <T> JSONArray?.mapObjects(map: (JSONObject) -> T): List<T> {
            if (this == null) return emptyList()
            return buildList(length()) {
                for (i in 0 until length()) optJSONObject(i)?.let { add(map(it)) }
            }
        }

        fun JSONObject?.toStringMap(): Map<String, String> {
            if (this == null) return emptyMap()
            return buildMap {
                keys().forEach { key -> put(key, optString(key).decodeHtmlEntities()) }
            }
        }

        /**
         * Formatted addresses arrive newline-separated and are shown as WooCommerce laid them out
         * for their country, so tags are stripped but the line breaks are kept.
         */
        fun String.stripHtmlKeepingLines(): String =
            replace(Regex("<br\\s*/?>", RegexOption.IGNORE_CASE), "\n")
                .replace(Regex("<[^>]*>"), "")
                .decodeHtmlEntities()
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotBlank() }
                .joinToString("\n")
    }
}
