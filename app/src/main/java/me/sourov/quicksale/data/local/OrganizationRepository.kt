package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OrganizationRepository(private val dao: OrganizationDao) {

    /** [status] is an [OrganizationStatus] slug, or blank for every status. */
    fun pagingSource(query: String, status: String = ""): PagingSource<Int, Organization> =
        dao.pagingSource(query.trim(), status)

    fun countMatching(query: String, status: String = ""): Flow<Int> =
        dao.countMatching(query.trim(), status)

    /** The same accounts read as people rather than companies, for the Accounts tab's other view. */
    fun memberPagingSource(
        query: String,
        status: String = "",
    ): PagingSource<Int, MemberWithOrganization> = dao.memberPagingSource(query.trim(), status)

    fun countMatchingMembers(query: String, status: String = ""): Flow<Int> =
        dao.countMatchingMembers(query.trim(), status)

    fun organization(id: Long): Flow<Organization?> = dao.observeById(id)

    /** People the till can sell to, matching [query] against their name, email or company. */
    fun searchSellableCustomers(
        query: String,
        limit: Int = SELLABLE_SEARCH_LIMIT,
    ): Flow<List<SellableCustomer>> = dao.searchSellableCustomers(query.trim(), limit)

    /** Active companies matching [query], for attaching a new person to an existing one. */
    fun searchSellableOrganizations(
        query: String,
        limit: Int = SELLABLE_SEARCH_LIMIT,
    ): Flow<List<Organization>> = dao.searchSellableOrganizations(query.trim(), limit)

    fun count(): Flow<Int> = dao.count()

    fun countByStatus(status: OrganizationStatus): Flow<Int> = dao.countByStatus(status.slug)

    fun memberCount(): Flow<Int> = dao.memberCount()

    fun members(organizationId: Long): Flow<List<Member>> = dao.observeMembers(organizationId)

    fun member(organizationId: Long, userId: Long): Flow<Member?> =
        dao.observeMember(organizationId, userId)

    /** The person behind a WordPress user id — how an order names the buyer who placed it. */
    fun memberByUserId(userId: Long): Flow<Member?> = dao.observeMemberByUserId(userId)

    fun locations(organizationId: Long): Flow<List<OrgLocation>> = dao.observeLocations(organizationId)

    /**
     * The locations [member] may actually choose from. `location_access` of "all" means every
     * location the organization has; otherwise only the listed IDs.
     */
    fun locationsFor(member: Member): Flow<List<OrgLocation>> =
        dao.observeLocations(member.organizationId).map { locations ->
            val allowed = member.allowedLocationIds ?: return@map locations
            locations.filter { it.id in allowed }
        }

    fun tallies(): Flow<Map<Long, OrganizationTally>> =
        dao.observeTallies().map { rows -> rows.associateBy { it.organizationId } }

    suspend fun replaceAll(
        organizations: List<Organization>,
        members: List<Member>,
        locations: List<OrgLocation>,
    ) = dao.replaceAll(organizations, members, locations)

    /*
     * Write-through, for the rows the app itself changes.
     *
     * Everything below applies a record the store has *already accepted*, so the screen shows the
     * change immediately instead of waiting for the next snapshot. That wait was the visible bug:
     * add a location and it wasn't in the location picker; approve an account and it stayed pending
     * until a sync that might be half an hour away, on a stand where the customer is still
     * standing there. The next full sync remains authoritative and simply agrees.
     */

    suspend fun saveOrganization(organization: Organization) =
        dao.insertOrganizations(listOf(organization))

    suspend fun saveMember(member: Member) = dao.insertMembers(listOf(member))

    /** Applies a location, and the single-default rule that comes with it. */
    suspend fun saveLocation(location: OrgLocation) = dao.saveLocation(location)

    suspend fun deleteMember(memberId: Long) = dao.deleteMember(memberId)

    suspend fun deleteLocation(locationId: Long) = dao.deleteLocation(locationId)

    private companion object {
        /** Enough to scroll a sheet, few enough that the answer is one glance, not a hunt. */
        const val SELLABLE_SEARCH_LIMIT = 40
    }
}
