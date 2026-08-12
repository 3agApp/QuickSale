package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

/**
 * The local catalog.
 *
 * The table holds the store's products in every status it has them in, but almost nothing here
 * will show one that isn't published: browsing, counting and searching are the counter's ways of
 * finding something to sell, and a draft is not for sale. The two lookups that deliberately ignore
 * the status are [findByCode] and [observeById] — they answer "what is this?" rather than "what can
 * I sell?", and a scan that finds nothing is indistinguishable from a product the store never had.
 *
 * `'publish'` is written literally in each query because Room compiles these to SQL at build time
 * and cannot read [Product.STATUS_PUBLISHED]; the constant is the one to change with them.
 */
@Dao
interface ProductDao {

    @Query(
        """
        SELECT * FROM products
        WHERE status = 'publish'
          AND (:query = ''
           OR name LIKE '%' || :query || '%'
           OR sku LIKE '%' || :query || '%'
           OR (ean != '' AND ean LIKE '%' || :query || '%'))
        ORDER BY name COLLATE NOCASE
        """
    )
    fun pagingSource(query: String): PagingSource<Int, Product>

    @Query(
        """
        SELECT COUNT(*) FROM products
        WHERE status = 'publish'
          AND (:query = ''
           OR name LIKE '%' || :query || '%'
           OR sku LIKE '%' || :query || '%'
           OR (ean != '' AND ean LIKE '%' || :query || '%'))
        """
    )
    fun countMatching(query: String): Flow<Int>

    /** Unfiltered on purpose: the detail screen describes a product, it doesn't sell one. */
    @Query("SELECT * FROM products WHERE id = :id")
    fun observeById(id: Long): Flow<Product?>

    @Query(
        """
        SELECT * FROM products
        WHERE status = 'publish'
          AND (name LIKE '%' || :query || '%'
           OR sku LIKE '%' || :query || '%'
           OR (ean != '' AND ean LIKE '%' || :query || '%'))
        ORDER BY name COLLATE NOCASE
        LIMIT 50
        """
    )
    fun search(query: String): Flow<List<Product>>

    /**
     * Exact match on a scanned or typed code, in any status. A barcode reader sends the EAN, so
     * that is tried first; the SKU still resolves for stores that label their goods with it.
     *
     * Unpublished products are found here rather than filtered out so the caller can tell the
     * operator *why* a real product can't be sold. Every caller must check [Product.isPublished]
     * before putting the result in an order.
     */
    @Query(
        """
        SELECT * FROM products
        WHERE (ean != '' AND ean = :code) OR sku = :code
        ORDER BY CASE WHEN ean = :code THEN 0 ELSE 1 END
        LIMIT 1
        """
    )
    suspend fun findByCode(code: String): Product?

    /**
     * Every product a code matches, in the same preference order as [findByCode].
     *
     * [findByCode] answers "what did they scan?" and picks a winner, which is right when the answer
     * feeds a cart the operator can still see and correct. Printing a label is not that: it commits
     * to paper straight away, so the one caller that prints unattended asks for the whole set and
     * refuses to guess when a code turns out to name two products.
     */
    @Query(
        """
        SELECT * FROM products
        WHERE (ean != '' AND ean = :code) OR sku = :code
        ORDER BY CASE WHEN ean = :code THEN 0 ELSE 1 END, name COLLATE NOCASE
        """
    )
    suspend fun findAllByCode(code: String): List<Product>

    @Query("SELECT COUNT(*) FROM products WHERE status = 'publish'")
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
