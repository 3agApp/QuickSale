package me.sourov.quicksale.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
import me.sourov.quicksale.data.settings.CheckoutConfig
import me.sourov.quicksale.data.settings.CheckoutConfigRepository
import me.sourov.quicksale.data.settings.OrderSettingsRepository
import me.sourov.quicksale.data.settings.PaymentGateway
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.ShippingOption
import java.math.BigDecimal
import java.math.RoundingMode

/** A product picked for the order, with its chosen quantity. */
data class CartLine(val product: Product, val quantity: Int) {
    /** Line subtotal as a [BigDecimal]; treats an unparsable price as zero. */
    val lineTotal: BigDecimal
        get() = (product.price.toBigDecimalOrNull() ?: BigDecimal.ZERO) * quantity.toBigDecimal()
}

/**
 * The running totals shown while building the order. Tax is a local estimate from the store's
 * standard rate — WooCommerce recalculates authoritatively when the order is created.
 */
data class TotalsPreview(
    val subtotal: BigDecimal = BigDecimal.ZERO,
    /** Shipping charge as entered (gross), or null when no shipping is selected. */
    val shipping: BigDecimal? = null,
    /** Estimated tax, or null when the store has no usable tax configuration. */
    val tax: BigDecimal? = null,
    /** True when [tax] is already contained in [total] (tax-inclusive store pricing). */
    val taxIncluded: Boolean = false,
    val taxLabel: String = "Tax",
    val total: BigDecimal = BigDecimal.ZERO,
)

/** Outcome of placing an order: the store's order id plus the totals it calculated. */
sealed interface PlaceResult {
    data class Placed(
        val remoteId: Long,
        val total: String,
        val totalTax: String,
        val shippingTotal: String,
        val discountTotal: String,
    ) : PlaceResult
}

class NewOrderViewModel(
    private val customerId: Long,
    customerRepository: CustomerRepository,
    private val productRepository: ProductRepository,
    private val settingsRepository: SettingsRepository,
    private val orderSettingsRepository: OrderSettingsRepository,
    checkoutConfigRepository: CheckoutConfigRepository,
) : ViewModel() {

    val customer: StateFlow<Customer?> = customerRepository.customer(customerId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val checkout: StateFlow<CheckoutConfig> = checkoutConfigRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, CheckoutConfig())

    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    val itemCount: StateFlow<Int> = _lines
        .map { lines -> lines.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    /** The operator's explicit gateway pick; null falls back to the store's first gateway. */
    private val _gatewayChoice = MutableStateFlow<PaymentGateway?>(null)
    val selectedGateway: StateFlow<PaymentGateway?> =
        combine(_gatewayChoice, checkout) { choice, config ->
            choice ?: config.gateways.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _shippingChoice = MutableStateFlow<ShippingOption?>(null)
    val selectedShipping: StateFlow<ShippingOption?> = _shippingChoice.asStateFlow()

    private val _shippingCost = MutableStateFlow("")
    val shippingCost: StateFlow<String> = _shippingCost.asStateFlow()

    private val _couponCode = MutableStateFlow("")
    val couponCode: StateFlow<String> = _couponCode.asStateFlow()

    val totals: StateFlow<TotalsPreview> =
        combine(_lines, _shippingChoice, _shippingCost, checkout) { lines, shipping, cost, config ->
            previewTotals(lines, shipping, cost, config)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TotalsPreview())

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

    fun selectGateway(gateway: PaymentGateway) { _gatewayChoice.value = gateway }

    /** Picks a shipping method (or null for no shipping) and pre-fills its configured cost. */
    fun selectShipping(option: ShippingOption?) {
        _shippingChoice.value = option
        _shippingCost.value = option?.cost?.toBigDecimalOrNull()?.toPlainString()
            ?: if (option != null) "0" else ""
    }

    fun setShippingCost(value: String) { _shippingCost.value = value }

    fun setCouponCode(value: String) { _couponCode.value = value }

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
                val config = checkout.value
                try {
                    val api = WooCommerceApi(settings)
                    val order = api.createOrder(
                        customerId = customer.id,
                        lineItems = current.map { WooCommerceApi.LineItem(it.product.id, it.quantity) },
                        status = status.slug,
                        setPaid = status.setPaid,
                        paymentMethod = selectedGateway.value,
                        billingJson = customer.billingJson,
                        shippingJson = customer.shippingJson,
                        shipping = shippingSelection(config),
                        couponCode = _couponCode.value,
                    )
                    refreshOrderedProducts(api, current)
                    _placed.value = PlaceResult.Placed(
                        remoteId = order.id,
                        total = order.total,
                        totalTax = order.totalTax,
                        shippingTotal = order.shippingTotal,
                        discountTotal = order.discountTotal,
                    )
                } catch (e: Exception) {
                    _message.value = "Couldn't place order: ${e.message}"
                }
            } finally {
                _placing.value = false
            }
        }
    }

    /**
     * Builds the shipping line for the order request. WooCommerce treats `shipping_lines.total`
     * as a NET amount and adds tax on top, so on tax-inclusive stores the entered (gross) cost is
     * converted to net first — otherwise the customer would be charged more than web checkout.
     */
    private fun shippingSelection(config: CheckoutConfig): WooCommerceApi.ShippingSelection? {
        val option = _shippingChoice.value ?: return null
        val gross = _shippingCost.value.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val rate = config.standardTaxRatePercent
        val net = if (config.taxesEnabled && config.pricesIncludeTax && option.taxable && rate != null) {
            gross.divide(BigDecimal.ONE + BigDecimal(rate.toString()).movePointLeft(2), 2, RoundingMode.HALF_UP)
        } else {
            gross.setScale(2, RoundingMode.HALF_UP)
        }
        return WooCommerceApi.ShippingSelection(option.methodId, option.title, net.toPlainString())
    }

    /** Pulls fresh copies of the ordered products so local stock reflects the sale. Non-fatal. */
    private suspend fun refreshOrderedProducts(api: WooCommerceApi, lines: List<CartLine>) {
        runCatching {
            val fresh = coroutineScope {
                lines.map { line -> async { api.fetchProduct(line.product.id) } }.awaitAll()
            }
            productRepository.upsert(fresh)
        }
    }

    fun consumeMessage() { _message.value = null }

    private fun previewTotals(
        lines: List<CartLine>,
        shipping: ShippingOption?,
        shippingCost: String,
        config: CheckoutConfig,
    ): TotalsPreview {
        val subtotal = lines.fold(BigDecimal.ZERO) { acc, line -> acc + line.lineTotal }
        val shippingAmount = if (shipping != null) {
            shippingCost.toBigDecimalOrNull() ?: BigDecimal.ZERO
        } else {
            null
        }
        var total = subtotal + (shippingAmount ?: BigDecimal.ZERO)
        var tax: BigDecimal? = null
        val ratePercent = config.standardTaxRatePercent?.takeIf { config.taxesEnabled }
        if (ratePercent != null) {
            val rate = BigDecimal(ratePercent.toString()).movePointLeft(2)
            if (config.pricesIncludeTax) {
                tax = total - total.divide(BigDecimal.ONE + rate, 2, RoundingMode.HALF_UP)
            } else {
                tax = (total * rate).setScale(2, RoundingMode.HALF_UP)
                total += tax
            }
        }
        return TotalsPreview(
            subtotal = subtotal,
            shipping = shippingAmount,
            tax = tax,
            taxIncluded = config.pricesIncludeTax,
            taxLabel = config.taxLabel,
            total = total,
        )
    }

    companion object {
        fun factory(
            customerId: Long,
            customerRepository: CustomerRepository,
            productRepository: ProductRepository,
            settingsRepository: SettingsRepository,
            orderSettingsRepository: OrderSettingsRepository,
            checkoutConfigRepository: CheckoutConfigRepository,
        ) = viewModelFactory {
            initializer {
                NewOrderViewModel(
                    customerId = customerId,
                    customerRepository = customerRepository,
                    productRepository = productRepository,
                    settingsRepository = settingsRepository,
                    orderSettingsRepository = orderSettingsRepository,
                    checkoutConfigRepository = checkoutConfigRepository,
                )
            }
        }
    }
}
