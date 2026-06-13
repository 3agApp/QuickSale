package me.sourov.quicksale.data.local

import androidx.paging.PagingSource
import kotlinx.coroutines.flow.Flow

class CustomerRepository(private val dao: CustomerDao) {

    fun pagingSource(query: String): PagingSource<Int, Customer> = dao.pagingSource(query.trim())

    fun countMatching(query: String): Flow<Int> = dao.countMatching(query.trim())

    fun count(): Flow<Int> = dao.count()
}
