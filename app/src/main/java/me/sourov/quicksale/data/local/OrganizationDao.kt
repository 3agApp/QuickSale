package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface OrganizationDao {

    /** An empty [status] means every status, matching how an empty [query] means every row. */
    @Query(
        """
        SELECT * FROM organizations
        WHERE (:query = ''
           OR name LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
           OR city LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%')
          AND (:status = '' OR status = :status)
        ORDER BY name COLLATE NOCASE
        """
    )
    fun pagingSource(query: String, status: String): PagingSource<Int, Organization>

    @Query(
        """
        SELECT COUNT(*) FROM organizations
        WHERE (:query = ''
           OR name LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
           OR city LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%')
          AND (:status = '' OR status = :status)
        """
    )
    fun countMatching(query: String, status: String): Flow<Int>

    @Query("SELECT * FROM organizations WHERE id = :id")
    fun observeById(id: Long): Flow<Organization?>

    @Query("SELECT COUNT(*) FROM organizations")
    fun count(): Flow<Int>

    @Query("SELECT COUNT(*) FROM organizations WHERE status = :status")
    fun countByStatus(status: String): Flow<Int>

    @Query("SELECT COUNT(*) FROM org_members")
    fun memberCount(): Flow<Int>

    @Query(
        """
        SELECT * FROM org_members WHERE organizationId = :organizationId
        ORDER BY canPlaceOrders DESC, name COLLATE NOCASE
        """
    )
    fun observeMembers(organizationId: Long): Flow<List<Member>>

    @Query("SELECT * FROM org_members WHERE organizationId = :organizationId AND userId = :userId LIMIT 1")
    fun observeMember(organizationId: Long, userId: Long): Flow<Member?>

    @Query(
        """
        SELECT * FROM org_locations WHERE organizationId = :organizationId
        ORDER BY isDefault DESC, name COLLATE NOCASE
        """
    )
    fun observeLocations(organizationId: Long): Flow<List<OrgLocation>>

    /**
     * The one member of every organization that has exactly one, with what's needed to decide
     * whether an order can be started for them straight away.
     *
     * `COUNT(*) = 1` makes the other selected columns unambiguous — they can only come from that
     * single row — so no aggregate wrapper is needed around them.
     */
    @Query(
        """
        SELECT m.organizationId AS organizationId,
               m.userId AS userId,
               m.canPlaceOrders AS canPlaceOrders,
               o.status AS organizationStatus
        FROM org_members m
        JOIN organizations o ON o.id = m.organizationId
        GROUP BY m.organizationId
        HAVING COUNT(*) = 1
        """
    )
    fun observeSoleMembers(): Flow<List<SoleMember>>

    /** Member and location tallies for every organization, keyed by organization id. */
    @Query(
        """
        SELECT o.id AS organizationId,
               (SELECT COUNT(*) FROM org_members m WHERE m.organizationId = o.id) AS memberCount,
               (SELECT COUNT(*) FROM org_locations l WHERE l.organizationId = o.id) AS locationCount
        FROM organizations o
        """
    )
    fun observeTallies(): Flow<List<OrganizationTally>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrganizations(items: List<Organization>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMembers(items: List<Member>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertLocations(items: List<OrgLocation>)

    @Query("DELETE FROM organizations")
    suspend fun clearOrganizations()

    @Query("DELETE FROM org_members")
    suspend fun clearMembers()

    @Query("DELETE FROM org_locations")
    suspend fun clearLocations()

    /**
     * Replaces the entire local snapshot in one transaction.
     *
     * The API serves snapshots rather than deltas precisely because deletions can only be
     * expressed by omission — members and locations carry no modification dates and every delete
     * on the store is a hard delete. So anything absent from [organizations] is gone, and merging
     * would leave the till offering an address the organization has abandoned.
     */
    @Transaction
    suspend fun replaceAll(
        organizations: List<Organization>,
        members: List<Member>,
        locations: List<OrgLocation>,
    ) {
        clearLocations()
        clearMembers()
        clearOrganizations()
        insertOrganizations(organizations)
        insertMembers(members)
        insertLocations(locations)
    }
}

/** An organization's only member, and whether that member may buy right now. */
data class SoleMember(
    val organizationId: Long,
    val userId: Long,
    val canPlaceOrders: Boolean,
    val organizationStatus: String,
)

/** How many members and locations one organization has, for the list rows. */
data class OrganizationTally(
    val organizationId: Long,
    val memberCount: Int,
    val locationCount: Int,
)
