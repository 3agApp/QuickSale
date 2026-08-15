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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.local.stepPackSize
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.settings.SettingsRepository
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One line of an order being edited.
 *
 * [itemId] is the WooCommerce line item id when this line already existed on the order, and null
 * for a product added during this edit — that distinction is what [OrderDetailViewModel.saveChanges]
 * turns into an add/update/remove [WooCommerceApi.LineItem]. [localKey] is a Compose-stable
 * identity for the row that survives quantity edits and doesn't depend on [itemId] being present.
 */
data class EditableLine(
    val localKey: Long,
    val itemId: Long?,
    val productId: Long,
    val name: String,
    val sku: String,
    val unitPrice: String,
    val quantity: Int,
    /**
     * The product's pack size and case step, copied off the catalog when the line is built — an
     * order's own line items carry neither, and a line that doesn't know them steps the order onto
     * quantities the store won't sell. Both default to 1, which is what a product the catalog no
     * longer holds has to be treated as: one unit, one at a time.
     */
    val packSize: Int = 1,
    val quantityStep: Int = 1,
) {
    /** Line subtotal as a [BigDecimal]; treats an unparsable price as zero. */
    val lineTotal: BigDecimal
        get() = (unitPrice.toBigDecimalOrNull() ?: BigDecimal.ZERO) * quantity.toBigDecimal()

    /**
     * This line moved [steps] of its case step, snapped onto a quantity the store actually sells —
     * the same arithmetic the till uses. A result of 0 means the line has dropped below one pack,
     * and the caller takes it off the order.
     */
    fun stepped(steps: Int): EditableLine =
        copy(quantity = stepPackSize(quantity, steps, packSize, quantityStep))
}

/**
 * One order: its stamps and totals as WooCommerce holds them, plus — for an order still
 * [WooCommerceApi.OrderDetail.isEditable] — a working copy of its line items that can be changed
 * and saved back.
 *
 * Editing a placed order is a smaller tool than building one: no delivery or payment
 * re-negotiation here, just add, remove, and change quantity on whatever WooCommerce already
 * billed. Quantities move in the product's own pack size, exactly as they do at the till — an
 * order the counter could never have *built* off the lattice is one the store will refuse when it
 * is saved, and refuse at the end, after the correction has already been promised to the customer.
 * The store recalculates totals and stock the same way it does for any other order edit — this
 * view model only ever sends what changed.
 */
class OrderDetailViewModel(
    private val orderId: Long,
    private val settingsRepository: SettingsRepository,
    private val productRepository: ProductRepository,
    organizationRepository: OrganizationRepository,
) : ViewModel() {

    private val _order = MutableStateFlow<WooCommerceApi.OrderDetail?>(null)
    val order: StateFlow<WooCommerceApi.OrderDetail?> = _order.asStateFlow()

    /**
     * The person who placed this order, looked up locally from the id the order carries.
     *
     * Null is an ordinary answer rather than a fault — the buyer may have been taken off the
     * account since, or this device may not have synced them yet — and the screen falls back to
     * the billing contact rather than leaving the question unanswered.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val placedBy: StateFlow<Member?> = _order
        .flatMapLatest { order ->
            val userId = order?.customerId ?: 0L
            if (userId <= 0L) flowOf(null) else organizationRepository.memberByUserId(userId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _editing = MutableStateFlow(false)
    val editing: StateFlow<Boolean> = _editing.asStateFlow()

    private val _workingLines = MutableStateFlow<List<EditableLine>>(emptyList())
    val workingLines: StateFlow<List<EditableLine>> = _workingLines.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val searchResults: StateFlow<List<Product>> = _query
        .flatMapLatest { q -> if (q.isBlank()) flowOf(emptyList()) else productRepository.search(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<OrderError?>(null)
    val error: StateFlow<OrderError?> = _error.asStateFlow()

    private var nextLocalKey = -1L

    init {
        refresh()
        // Hardware scans only mutate the order once the counter has explicitly asked to edit it —
        // browsing a finished order shouldn't be one stray scan away from changing it.
        viewModelScope.launch {
            ScannerHub.scans.collect { code -> if (_editing.value) handleCode(code.trim()) }
        }
    }

    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                val api = WooCommerceApi(settings)
                _order.value = api.fetchOrder(orderId)
            } catch (e: Exception) {
                _error.value = OrderError.from(e)
            } finally {
                _loading.value = false
            }
        }
    }

    /**
     * Opens the working copy, with each line's pack size read from the local catalog — an order's
     * line items carry the product's id and price but nothing about how it may be ordered, and the
     * +/− buttons need that before the first tap. A product this device hasn't got (deleted from
     * the store, or never synced) keeps the 1/1 default and steps one unit at a time.
     */
    fun startEditing() {
        val current = _order.value ?: return
        viewModelScope.launch {
            _workingLines.value = current.lineItems.map { item ->
                val product = productRepository.byId(item.productId)
                EditableLine(
                    localKey = item.id,
                    itemId = item.id,
                    productId = item.productId,
                    name = item.name,
                    sku = item.sku,
                    unitPrice = item.price,
                    quantity = item.quantity,
                    packSize = product?.packSize ?: 1,
                    quantityStep = product?.quantityStep ?: 1,
                )
            }
            _editing.value = true
        }
    }

    fun cancelEditing() {
        _editing.value = false
        _workingLines.value = emptyList()
        _query.value = ""
    }

    fun onQueryChange(value: String) { _query.value = value }

    /**
     * Adds a product to the working lines, or puts another case on it when it's already there —
     * the same pack size the till would have rung up.
     */
    fun addProduct(product: Product) {
        _workingLines.value = _workingLines.value.let { current ->
            val index = current.indexOfFirst { it.productId == product.id }
            if (index >= 0) {
                current.toMutableList().also { lines ->
                    lines[index] = lines[index].stepped(+1)
                }
            } else {
                current + EditableLine(
                    localKey = nextLocalKey--,
                    itemId = null,
                    productId = product.id,
                    name = product.name,
                    sku = product.sku,
                    unitPrice = product.price,
                    quantity = product.packSize,
                    packSize = product.packSize,
                    quantityStep = product.quantityStep,
                )
            }
        }
    }

    fun addFromSearch(product: Product) {
        addProduct(product)
        _query.value = ""
        _message.value = "Added ${product.name}"
    }

    fun submitTyped() {
        val code = _query.value.trim()
        if (code.isEmpty()) return
        viewModelScope.launch { handleCode(code) }
    }

    private suspend fun handleCode(code: String) {
        if (code.isEmpty()) return
        val product = productRepository.findByCode(code)
        when {
            product == null -> {
                _query.value = code
                _message.value = "No product matches \"$code\""
            }
            !product.isPublished -> {
                _query.value = ""
                _message.value = "${product.name} is ${product.statusLabel} on the store, so it can't be added"
            }
            else -> {
                addProduct(product)
                _query.value = ""
                _message.value = "Added ${product.name}"
            }
        }
    }

    fun increment(localKey: Long) = changeQuantity(localKey, +1)
    fun decrement(localKey: Long) = changeQuantity(localKey, -1)

    /**
     * Whether a held − may keep running, or has reached the last case before the line would come
     * off the order.
     *
     * Dropping the line entirely is what the bin button is for — a hold should stop at one pack
     * rather than run a product off the order on its way past. Asked through [EditableLine.stepped]
     * rather than compared against the pack size, so it can't drift from what − actually does.
     */
    fun canStepDown(localKey: Long): Boolean =
        _workingLines.value.firstOrNull { it.localKey == localKey }?.stepped(-1)?.quantity?.let { it > 0 } == true

    /**
     * Moves a line by [steps] of its own case step, never off it. Falling below one pack takes the
     * line off the order rather than leaving a quantity the store won't sell.
     */
    private fun changeQuantity(localKey: Long, steps: Int) {
        _workingLines.value = _workingLines.value.mapNotNull { line ->
            if (line.localKey != localKey) line else line.stepped(steps).takeIf { it.quantity > 0 }
        }
    }

    fun remove(localKey: Long) {
        _workingLines.value = _workingLines.value.filterNot { it.localKey == localKey }
    }

    /**
     * Saves the working lines as a diff against what the order had when editing started: removed
     * lines are sent with `quantity = 0`, changed quantities are sent by id, and new lines are sent
     * without one — see [WooCommerceApi.updateOrderLineItems]. A line nobody touched is never
     * resent, so the store never re-prices something that didn't change.
     *
     * Quantity changes and new lines both carry an explicit subtotal/total: WooCommerce only
     * auto-prices a line when it's created, so a quantity-only update to an existing line would
     * otherwise leave that line — and the order total — at its old price.
     */
    fun saveChanges() {
        if (_saving.value) return
        val original = _order.value ?: return
        val originalById = original.lineItems.associateBy { it.id }
        val edited = _workingLines.value
        val editedById = edited.filter { it.itemId != null }.associateBy { it.itemId!! }

        val diff = buildList {
            originalById.keys.filterNot { it in editedById }.forEach { removedId ->
                val item = originalById.getValue(removedId)
                add(WooCommerceApi.LineItem(productId = item.productId, quantity = 0, id = removedId))
            }
            editedById.forEach { (id, line) ->
                val was = originalById[id] ?: return@forEach
                if (was.quantity != line.quantity) {
                    val lineTotal = line.lineTotal.setScale(2, RoundingMode.HALF_UP).toPlainString()
                    add(
                        WooCommerceApi.LineItem(
                            productId = line.productId,
                            quantity = line.quantity,
                            id = id,
                            subtotal = lineTotal,
                            total = lineTotal,
                        ),
                    )
                }
            }
            edited.filter { it.itemId == null }.forEach { line ->
                val lineTotal = line.lineTotal.setScale(2, RoundingMode.HALF_UP).toPlainString()
                add(
                    WooCommerceApi.LineItem(
                        productId = line.productId,
                        quantity = line.quantity,
                        subtotal = lineTotal,
                        total = lineTotal,
                    ),
                )
            }
        }

        if (diff.isEmpty()) {
            cancelEditing()
            return
        }

        _saving.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                val api = WooCommerceApi(settings)
                _order.value = api.updateOrderLineItems(original.id, diff)
                _editing.value = false
                _workingLines.value = emptyList()
            } catch (e: Exception) {
                _error.value = OrderError.from(e)
            } finally {
                _saving.value = false
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    fun consumeError() { _error.value = null }

    companion object {
        fun factory(
            orderId: Long,
            settingsRepository: SettingsRepository,
            productRepository: ProductRepository,
            organizationRepository: OrganizationRepository,
        ) = viewModelFactory {
            initializer {
                OrderDetailViewModel(
                    orderId = orderId,
                    settingsRepository = settingsRepository,
                    productRepository = productRepository,
                    organizationRepository = organizationRepository,
                )
            }
        }
    }
}
