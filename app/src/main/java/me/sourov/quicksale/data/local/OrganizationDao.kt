package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Embedded
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

    /**
     * The same list read from the other end: every person, under the company they buy for.
     *
     * One search box has to answer both halves of the question, because the operator at the stand
     * knows one of two things — the name of the person in front of them, or the name of the shop
     * they came from — and never reliably which. So a person matches on their own name and email
     * *and* on their company's name, town, email and phone; the row that comes back names both,
     * which is the answer either way.
     *
     * Ordered by company first so the list groups, and by person within it. The status narrows on
     * the *organization*, matching the company view's filter: a person's own membership status is
     * shown on their row rather than filtered on, since an inactive person on an active account is
     * exactly what someone auditing the account has come to find.
     */
    @Query(
        """
        SELECT m.*,
               o.name AS organizationName,
               o.city AS organizationCity,
               o.status AS organizationStatus
        FROM org_members m
        JOIN organizations o ON o.id = m.organizationId
        WHERE (:query = ''
           OR m.name LIKE '%' || :query || '%'
           OR m.email LIKE '%' || :query || '%'
           OR o.name LIKE '%' || :query || '%'
           OR o.city LIKE '%' || :query || '%'
           OR o.email LIKE '%' || :query || '%'
           OR o.phone LIKE '%' || :query || '%')
          AND (:status = '' OR o.status = :status)
        ORDER BY o.name COLLATE NOCASE, m.name COLLATE NOCASE
        """
    )
    fun memberPagingSource(query: String, status: String): PagingSource<Int, MemberWithOrganization>

    @Query(
        """
        SELECT COUNT(*)
        FROM org_members m
        JOIN organizations o ON o.id = m.organizationId
        WHERE (:query = ''
           OR m.name LIKE '%' || :query || '%'
           OR m.email LIKE '%' || :query || '%'
           OR o.name LIKE '%' || :query || '%'
           OR o.city LIKE '%' || :query || '%'
           OR o.email LIKE '%' || :query || '%'
           OR o.phone LIKE '%' || :query || '%')
          AND (:status = '' OR o.status = :status)
        """
    )
    fun countMatchingMembers(query: String, status: String): Flow<Int>

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

    /**
     * Every person who can actually be sold to, with the company they buy for.
     *
     * The customer is the *person*: they are the WooCommerce customer the order is stamped with,
     * and a person belongs to exactly one organization, so there is nothing to choose after they
     * are picked. Searching companies first made the operator navigate a hierarchy that has only
     * ever had one answer at the bottom.
     *
     * Only `active` organizations and members the store says may order appear. The picker exists to
     * answer "who is this order for", and offering someone the store would refuse turns a
     * two-second choice into a dead end; the Accounts tab still shows every status.
     */
    @Query(
        """
        SELECT m.*,
               o.name AS organizationName,
               o.city AS organizationCity
        FROM org_members m
        JOIN organizations o ON o.id = m.organizationId
        WHERE o.status = 'active'
          AND m.canPlaceOrders = 1
          AND (:query = ''
             OR m.name LIKE '%' || :query || '%'
             OR m.email LIKE '%' || :query || '%'
             OR o.name LIKE '%' || :query || '%')
        ORDER BY m.name COLLATE NOCASE
        LIMIT :limit
        """
    )
    fun searchSellableCustomers(query: String, limit: Int): Flow<List<SellableCustomer>>

    /** Active organizations by name, for attaching a new person to a company that already exists. */
    @Query(
        """
        SELECT * FROM organizations
        WHERE status = 'active'
          AND (:query = '' OR name LIKE '%' || :query || '%' OR city LIKE '%' || :query || '%')
        ORDER BY name COLLATE NOCASE
        LIMIT :limit
        """
    )
    fun searchSellableOrganizations(query: String, limit: Int): Flow<List<Organization>>

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

    /**
     * The person behind a WordPress user id, whichever company they buy for.
     *
     * An order names its buyer by user id alone, so reading it back cannot go through an
     * organization the way the rest of this DAO does. Null is an ordinary answer: the order may
     * predate a sync, or the member may have since been taken off the account.
     */
    @Query("SELECT * FROM org_members WHERE userId = :userId LIMIT 1")
    fun observeMemberByUserId(userId: Long): Flow<Member?>

    @Query(
        """
        SELECT * FROM org_locations WHERE organizationId = :organizationId
        ORDER BY isDefault DESC, name COLLATE NOCASE
        """
    )
    fun observeLocations(organizationId: Long): Flow<List<OrgLocation>>

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

    /** Clears the default flag on every location of an organization except [keepId]. */
    @Query(
        """
        UPDATE org_locations SET isDefault = 0
        WHERE organizationId = :organizationId AND id != :keepId
        """
    )
    suspend fun clearOtherDefaults(organizationId: Long, keepId: Long)

    /**
     * Writes one location back after the store accepted it, keeping the single-default rule.
     *
     * Setting a location as default clears the flag on the others *server-side*, so applying only
     * the row that came back would leave two locally — and the till would offer a default that no
     * longer is one until the next full sync happened to correct it.
     */
    @Transaction
    suspend fun saveLocation(location: OrgLocation) {
        insertLocations(listOf(location))
        if (location.isDefault) clearOtherDefaults(location.organizationId, location.id)
    }

    /*
     * Single-row removals, for the rows the app itself deletes. The store has already accepted the
     * delete by the time these run, so the screen updates without waiting for the next snapshot.
     */

    @Query("DELETE FROM org_members WHERE memberId = :memberId")
    suspend fun deleteMember(memberId: Long)

    @Query("DELETE FROM org_locations WHERE id = :locationId")
    suspend fun deleteLocation(locationId: Long)

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

/**
 * A person the till can sell to, carrying just enough of their company to be recognised.
 *
 * Not the whole [Organization]: the picker shows a name and a town, and reading the rest per row
 * would be work done to be thrown away.
 */
data class SellableCustomer(
    @Embedded val member: Member,
    val organizationName: String,
    val organizationCity: String,
)

/**
 * One person with the company they buy for, for the Accounts tab's people view.
 *
 * Carries the organization's status as well as its name because the people list is filtered by it
 * exactly as the company list is — "show me everyone on a suspended account" is the same question
 * asked from the other end.
 */
data class MemberWithOrganization(
    @Embedded val member: Member,
    val organizationName: String,
    val organizationCity: String,
    val organizationStatus: String,
)

/** How many members and locations one organization has, for the list rows. */
data class OrganizationTally(
    val organizationId: Long,
    val memberCount: Int,
    val locationCount: Int,
)
