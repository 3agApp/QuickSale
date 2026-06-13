package me.sourov.quicksale.ui.products

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.ProductRepository

class ProductDetailViewModel(
    repository: ProductRepository,
    productId: Long,
) : ViewModel() {

    val product: StateFlow<Product?> = repository.product(productId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(repository: ProductRepository, productId: Long) = viewModelFactory {
            initializer { ProductDetailViewModel(repository, productId) }
        }
    }
}
