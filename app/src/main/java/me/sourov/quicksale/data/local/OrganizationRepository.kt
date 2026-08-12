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

    private companion object {
        /** Enough to scroll a sheet, few enough that the answer is one glance, not a hunt. */
        const val SELLABLE_SEARCH_LIMIT = 40
    }
}
