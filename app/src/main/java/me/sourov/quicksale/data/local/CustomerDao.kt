package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomerDao {

    @Query(
        """
        SELECT * FROM customers
        WHERE :query = ''
           OR firstName LIKE '%' || :query || '%'
           OR lastName LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
           OR company LIKE '%' || :query || '%'
        ORDER BY firstName COLLATE NOCASE, lastName COLLATE NOCASE
        """
    )
    fun pagingSource(query: String): PagingSource<Int, Customer>

    @Query(
        """
        SELECT COUNT(*) FROM customers
        WHERE :query = ''
           OR firstName LIKE '%' || :query || '%'
           OR lastName LIKE '%' || :query || '%'
           OR email LIKE '%' || :query || '%'
           OR phone LIKE '%' || :query || '%'
           OR company LIKE '%' || :query || '%'
        """
    )
    fun countMatching(query: String): Flow<Int>

    @Query("SELECT * FROM customers WHERE id = :id")
    fun observeById(id: Long): Flow<Customer?>

    @Query("SELECT COUNT(*) FROM customers")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Customer>)

    @Query("DELETE FROM customers")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<Customer>) {
        clear()
        upsertAll(items)
    }
}
