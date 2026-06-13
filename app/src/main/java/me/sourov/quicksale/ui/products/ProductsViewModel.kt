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

    fun setQuery(value: String) { _query.value = value }

    companion object {
        fun factory(repository: ProductRepository) = viewModelFactory {
            initializer { ProductsViewModel(repository) }
        }
    }
}
