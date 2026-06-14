package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {

    fun pagingSource(query: String): PagingSource<Int, Product> =
        dao.pagingSource(query.trim())

    fun countMatching(query: String): Flow<Int> =
        dao.countMatching(query.trim())

    fun product(id: Long): Flow<Product?> = dao.observeById(id)

    /** Up to 50 products matching the query by name or SKU, for the order picker. */
    fun search(query: String): Flow<List<Product>> = dao.search(query.trim())

    /** Exact SKU match, used to add a scanned/entered barcode straight to the cart. */
    suspend fun findBySku(sku: String): Product? = dao.findBySku(sku.trim())

    fun count(): Flow<Int> = dao.count()
}
