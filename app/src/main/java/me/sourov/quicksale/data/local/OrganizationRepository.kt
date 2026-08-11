package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OrganizationRepository(private val dao: OrganizationDao) {

    fun pagingSource(query: String): PagingSource<Int, Organization> = dao.pagingSource(query.trim())

    fun countMatching(query: String): Flow<Int> = dao.countMatching(query.trim())

    fun organization(id: Long): Flow<Organization?> = dao.observeById(id)

    fun count(): Flow<Int> = dao.count()

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

    /**
     * Organizations whose single member can be served without a stop at the detail screen, mapped
     * to that member's user id.
     *
     * There is nothing to choose on the detail screen when an account has one member — picking them
     * is the only thing it asks for. Accounts whose one member can't buy are left out: their detail
     * screen is where the reason is explained.
     */
    fun soleOrderableMembers(): Flow<Map<Long, Long>> =
        dao.observeSoleMembers().map { rows ->
            rows.filter { it.canPlaceOrders && OrganizationStatus.fromSlug(it.organizationStatus).canTrade }
                .associate { it.organizationId to it.userId }
        }

    fun tallies(): Flow<Map<Long, OrganizationTally>> =
        dao.observeTallies().map { rows -> rows.associateBy { it.organizationId } }

    suspend fun replaceAll(
        organizations: List<Organization>,
        members: List<Member>,
        locations: List<OrgLocation>,
    ) = dao.replaceAll(organizations, members, locations)
}
