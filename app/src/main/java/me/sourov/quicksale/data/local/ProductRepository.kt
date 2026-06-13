package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

class ProductRepository(private val dao: ProductDao) {

    fun pagingSource(query: String): PagingSource<Int, Product> =
        dao.pagingSource(query.trim())

    fun countMatching(query: String): Flow<Int> =
        dao.countMatching(query.trim())

    fun product(id: Long): Flow<Product?> = dao.observeById(id)

    fun count(): Flow<Int> = dao.count()
}
