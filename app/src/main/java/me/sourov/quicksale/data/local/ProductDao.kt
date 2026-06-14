package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

@Dao
interface ProductDao {

    @Query(
        """
        SELECT * FROM products
        WHERE :query = '' OR name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        """
    )
    fun pagingSource(query: String): PagingSource<Int, Product>

    @Query(
        """
        SELECT COUNT(*) FROM products
        WHERE :query = '' OR name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%'
        """
    )
    fun countMatching(query: String): Flow<Int>

    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<Product?>

    @Query(
        """
        SELECT * FROM products
        WHERE name LIKE '%' || :query || '%' OR sku LIKE '%' || :query || '%'
        ORDER BY name COLLATE NOCASE
        LIMIT 50
        """
    )
    fun search(query: String): Flow<List<Product>>

    @Query("SELECT * FROM products WHERE sku = :sku LIMIT 1")
    suspend fun findBySku(sku: String): Product?

    @Query("SELECT COUNT(*) FROM products")
    fun count(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(items: List<Product>)

    @Query("DELETE FROM products")
    suspend fun clear()

    @Transaction
    suspend fun replaceAll(items: List<Product>) {
        clear()
        upsertAll(items)
    }
}
