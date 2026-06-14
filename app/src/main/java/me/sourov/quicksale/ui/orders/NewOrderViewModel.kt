package me.sourov.quicksale.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.local.Customer
import me.sourov.quicksale.data.local.CustomerRepository
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.settings.OrderSettingsRepository
import me.sourov.quicksale.data.settings.SettingsRepository
import java.math.BigDecimal

/** A product picked for the order, with its chosen quantity. */
data class CartLine(val product: Product, val quantity: Int) {
    /** Line subtotal as a [BigDecimal]; treats an unparsable price as zero. */
    val lineTotal: BigDecimal
        get() = (product.price.toBigDecimalOrNull() ?: BigDecimal.ZERO) * quantity.toBigDecimal()
}

/** Outcome of placing an order: a successful WooCommerce order [remoteId], used to drive navigation. */
sealed interface PlaceResult {
    data class Placed(val remoteId: Long) : PlaceResult
}

class NewOrderViewModel(
    private val customerId: Long,
    customerRepository: CustomerRepository,
    private val productRepository: ProductRepository,
    private val settingsRepository: SettingsRepository,
    private val orderSettingsRepository: OrderSettingsRepository,
) : ViewModel() {

    val customer: StateFlow<Customer?> = customerRepository.customer(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    val itemCount: StateFlow<Int> = _lines
        .map { lines -> lines.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val total: StateFlow<BigDecimal> = _lines
        .map { lines -> lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.lineTotal } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, BigDecimal.ZERO)

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Product>> = _query
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else productRepository.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _placing = MutableStateFlow(false)
    val placing: StateFlow<Boolean> = _placing.asStateFlow()

    private val _placed = MutableStateFlow<PlaceResult?>(null)
    val placed: StateFlow<PlaceResult?> = _placed.asStateFlow()

    init {
        // Hardware scans (broadcast intents OR keyboard/HID, per Settings) all arrive via ScannerHub,
        // independent of which field is focused. Add the scanned product straight to the order.
        viewModelScope.launch {
            ScannerHub.scans.collect { code -> handleCode(code.trim()) }
        }
    }

    fun onQueryChange(value: String) { _query.value = value }

    /** Adds a product to the cart, or bumps its quantity if already present. */
    fun addProduct(product: Product) {
        _lines.value = _lines.value.let { current ->
            val index = current.indexOfFirst { it.product.id == product.id }
            if (index >= 0) {
                current.toMutableList().also { it[index] = it[index].copy(quantity = it[index].quantity + 1) }
            } else {
                current + CartLine(product, 1)
            }
        }
    }

    fun addFromSearch(product: Product) {
        addProduct(product)
        _query.value = ""
        _message.value = "Added ${product.name}"
    }

    /** Handles the search field's "go" action (also how a typed/pasted barcode is submitted). */
    fun submitTyped() {
        val code = _query.value.trim()
        if (code.isEmpty()) return
        viewModelScope.launch { handleCode(code) }
    }

    /**
     * Resolves a scanned or typed [code]: an exact SKU match is added straight to the cart; otherwise
     * the code becomes the search query so the operator can pick a matching product.
     */
    private suspend fun handleCode(code: String) {
        if (code.isEmpty()) return
        val product = productRepository.findBySku(code)
        if (product != null) {
            addProduct(product)
            _query.value = ""
            _message.value = "Added ${product.name}"
        } else {
            _query.value = code
            _message.value = "No product matches \"$code\""
        }
    }

    fun increment(productId: Long) = changeQuantity(productId, +1)
    fun decrement(productId: Long) = changeQuantity(productId, -1)

    private fun changeQuantity(productId: Long, delta: Int) {
        _lines.value = _lines.value.mapNotNull { line ->
            if (line.product.id != productId) {
                line
            } else {
                val quantity = line.quantity + delta
                if (quantity <= 0) null else line.copy(quantity = quantity)
            }
        }
    }

    fun remove(productId: Long) {
        _lines.value = _lines.value.filterNot { it.product.id == productId }
    }

    fun placeOrder() {
        if (_placing.value) return
        val customer = customer.value
        val current = _lines.value
        if (customer == null) {
            _message.value = "Customer not loaded yet"
            return
        }
        if (current.isEmpty()) {
            _message.value = "Add at least one product"
            return
        }

        _placing.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                if (!settings.isConfigured) {
                    _message.value = "Connect your store in Settings to place orders"
                    return@launch
                }
                val status = orderSettingsRepository.status.first()
                try {
                    val remoteId = WooCommerceApi(settings).createOrder(
                        customerId = customer.id,
                        lineItems = current.map { WooCommerceApi.LineItem(it.product.id, it.quantity) },
                        status = status.slug,
                        setPaid = status.setPaid,
                    )
                    _placed.value = PlaceResult.Placed(remoteId)
                } catch (e: Exception) {
                    _message.value = "Couldn't place order: ${e.message}"
                }
            } finally {
                _placing.value = false
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    companion object {
        fun factory(
            customerId: Long,
            customerRepository: CustomerRepository,
            productRepository: ProductRepository,
            settingsRepository: SettingsRepository,
            orderSettingsRepository: OrderSettingsRepository,
        ) = viewModelFactory {
            initializer {
                NewOrderViewModel(
                    customerId = customerId,
                    customerRepository = customerRepository,
                    productRepository = productRepository,
                    settingsRepository = settingsRepository,
                    orderSettingsRepository = orderSettingsRepository,
                )
            }
        }
    }
}
