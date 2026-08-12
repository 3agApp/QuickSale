package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {

    fun pagingSource(query: String): PagingSource<Int, Product> =
        dao.pagingSource(query.trim())

    fun countMatching(query: String): Flow<Int> =
        dao.countMatching(query.trim())

    fun product(id: Long): Flow<Product?> = dao.observeById(id)

    /** A single read of one product, for restoring a persisted cart line. */
    suspend fun byId(id: Long): Product? = dao.findById(id)

    /** Up to 50 *published* products matching the query by name, SKU or EAN, for the order picker. */
    fun search(query: String): Flow<List<Product>> = dao.search(query.trim())

    /**
     * Exact EAN or SKU match in any status, used to resolve a scanned or entered barcode.
     *
     * Callers must check [Product.isPublished] before selling the result — an unpublished product
     * is returned so the counter can be told why it can't be, not so it can be added anyway.
     */
    suspend fun findByCode(code: String): Product? = dao.findByCode(code.trim())

    /** Every product [code] matches, for callers that must not guess between two of them. */
    suspend fun findAllByCode(code: String): List<Product> = dao.findAllByCode(code.trim())

    /** Updates the local copies of [products], e.g. fresh stock right after an order. */
    suspend fun upsert(products: List<Product>) = dao.upsertAll(products)

    fun count(): Flow<Int> = dao.count()
}
