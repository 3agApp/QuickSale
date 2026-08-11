package me.sourov.quicksale.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.ProductRepository

class ProductsViewModel(private val repository: ProductRepository) : ViewModel() {

    private val _query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val products: Flow<PagingData<Product>> = _query
        .flatMapLatest { q ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                repository.pagingSource(q)
            }.flow
        }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val matchingCount: StateFlow<Int> = _query
        .flatMapLatest { q -> repository.countMatching(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /**
     * The unpublished product this query is the exact code of, when the list has nothing to show
     * for it — otherwise null.
     *
     * A scan on this tab becomes the search query, and an unpublished product is not searchable, so
     * without this the till would answer a perfectly good barcode with "No matches" and send
     * whoever scanned it looking for a fault in the scanner or the sync. The match is exact (EAN or
     * SKU), so it only ever fires for a scanned or fully typed code, never mid-search.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val unpublishedMatch: StateFlow<Product?> = _query
        .mapLatest { q ->
            if (q.isBlank()) null else repository.findByCode(q)?.takeUnless { it.isPublished }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun setQuery(value: String) { _query.value = value }

    companion object {
        fun factory(repository: ProductRepository) = viewModelFactory {
            initializer { ProductsViewModel(repository) }
        }
    }
}
