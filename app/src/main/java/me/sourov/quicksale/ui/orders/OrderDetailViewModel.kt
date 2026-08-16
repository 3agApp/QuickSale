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
import me.sourov.quicksale.data.local.packSizeNote
import me.sourov.quicksale.data.local.stepPackSize
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.settings.CheckoutConfig
import me.sourov.quicksale.data.settings.CheckoutConfigRepository
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.data.settings.shippingGross
import me.sourov.quicksale.data.settings.shippingNet
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

    /** How this quantity disagrees with the store's pack rule, or null when it sits on it. */
    val packSizeNote: String?
        get() = packSizeNote(quantity, packSize, quantityStep)

    /**
     * This line one unit smaller, whatever its pack size — 0 means it belongs off the order.
     *
     * The same rule the till's − follows, and for the same reason: a case with a damaged unit in
     * it, or a customer who wants five, is an ordinary thing to have to record. Correcting a
     * placed order is if anything the *more* likely place to need it, since that is where a
     * short delivery gets put right. [packSizeNote] says the quantity is off the rule; nothing
     * refuses it, because nothing on the store side does either.
     */
    fun lowered(): EditableLine = copy(quantity = quantity - 1)

    /**
     * This line moved [steps] of its case step, snapped onto a quantity the store actually sells —
     * the same arithmetic the till uses. A result of 0 means the line has dropped below one pack,
     * and the caller takes it off the order.
     */
    fun stepped(steps: Int): EditableLine =
        copy(quantity = stepPackSize(quantity, steps, packSize, quantityStep))
}

/**
 * The shipping on an order being edited: which method carries it, and what it charges.
 *
 * [cost] is **gross**, as the operator quoted it and as the till's own cost field reads — the order
 * itself holds net, and [OrderDetailViewModel.startEditing] grosses it up on the way in. [taxable]
 * is carried because that conversion depends on it and the order's own shipping line doesn't say;
 * it comes from the matching store method, defaulting to taxable when there is no match.
 */
data class EditableShipping(
    val methodId: String,
    val methodTitle: String,
    val cost: String,
    val taxable: Boolean,
)

/**
 * One order: its stamps and totals as WooCommerce holds them, plus — for an order still
 * [WooCommerceApi.OrderDetail.isEditable] — a working copy of its line items that can be changed
 * and saved back.
 *
 * Editing a placed order is a smaller tool than building one: no address or payment
 * re-negotiation here, just the products, their quantities, and what the delivery costs — the
 * things a counter gets wrong and has to put right while the customer is still standing there.
 *
 * Quantities behave exactly as they do at the till, down to the − that steps one unit and the note
 * that says so. This screen used to hold + and − both to the case, on the belief that the store
 * refused a save off the pack size; reading the Kontor plugin settled that it does not — its
 * quantity rule is enforced on the storefront cart alone, and it exempts order screens on purpose.
 * There was never a reason for a correction here to be coarser than the sale it corrects.
 *
 * The store recalculates totals and stock the same way it does for any other order edit — this
 * view model only ever sends what changed.
 */
class OrderDetailViewModel(
    private val orderId: Long,
    private val settingsRepository: SettingsRepository,
    private val productRepository: ProductRepository,
    organizationRepository: OrganizationRepository,
    checkoutConfigRepository: CheckoutConfigRepository,
) : ViewModel() {

    /** The store's shipping methods and tax rules, as the last sync left them. */
    val checkout: StateFlow<CheckoutConfig> = checkoutConfigRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, CheckoutConfig())

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

    private val _workingShipping = MutableStateFlow<EditableShipping?>(null)
    val workingShipping: StateFlow<EditableShipping?> = _workingShipping.asStateFlow()

    /**
     * Whether the shipping controls have been touched during this edit.
     *
     * The save is a diff, and shipping is the one part of it that cannot be diffed by comparing
     * values: the order holds net, the screen shows gross, and rounding through that conversion and
     * back can move an untouched charge by a cent. So an untouched shipping line is never sent at
     * all, which is both cheaper and the only way to be sure the app never re-prices a delivery
     * nobody asked it to.
     */
    private val _shippingEdited = MutableStateFlow(false)

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
            _workingShipping.value = current.shippingLine?.let { line ->
                val taxable = matchingOption(line)?.taxable ?: true
                val net = line.total.toBigDecimalOrNull() ?: BigDecimal.ZERO
                EditableShipping(
                    methodId = line.methodId,
                    methodTitle = line.methodTitle,
                    cost = checkout.value.shippingGross(net, taxable).toPlainString(),
                    taxable = taxable,
                )
            }
            _shippingEdited.value = false
            _editing.value = true
        }
    }

    /**
     * The store method an order's shipping line came from, when the catalog still knows it.
     *
     * Matched on the method id *and* the title, because a store with several zones offers the same
     * `flat_rate` in each and only the title tells them apart. A line with no match — a zone that
     * has since been renamed or removed — is kept and shown as it stands rather than dropped.
     */
    private fun matchingOption(line: WooCommerceApi.OrderShippingLine): ShippingOption? =
        checkout.value.shippingOptions.firstOrNull {
            it.methodId == line.methodId && it.title == line.methodTitle
        }

    /** Puts a store method on the order, with its configured cost as the starting figure. */
    fun selectShipping(option: ShippingOption) {
        _workingShipping.value = EditableShipping(
            methodId = option.methodId,
            methodTitle = option.title,
            cost = option.cost.toBigDecimalOrNull()?.toPlainString() ?: "0",
            taxable = option.taxable,
        )
        _shippingEdited.value = true
    }

    fun setShippingCost(value: String) {
        val current = _workingShipping.value ?: return
        _workingShipping.value = current.copy(cost = value)
        _shippingEdited.value = true
    }

    /** Takes shipping off the order — for a delivery the customer decided to collect after all. */
    fun removeShipping() {
        _workingShipping.value = null
        _shippingEdited.value = true
    }

    fun cancelEditing() {
        _editing.value = false
        _workingLines.value = emptyList()
        _workingShipping.value = null
        _shippingEdited.value = false
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

    /** + adds a case, exactly as it does at the till. */
    fun increment(localKey: Long) = changeQuantity(localKey) { it.stepped(+1) }

    /** − comes down one unit at a time, on or off the pack size — see [EditableLine.lowered]. */
    fun decrement(localKey: Long) = changeQuantity(localKey, EditableLine::lowered)

    /**
     * Whether a held − may keep running, or has reached the last unit before the line would come
     * off the order. Dropping the line entirely is what the bin button is for.
     */
    fun canStepDown(localKey: Long): Boolean =
        (_workingLines.value.firstOrNull { it.localKey == localKey }?.quantity ?: 0) > 1

    /**
     * Applies [move] to one line. A move that lands on 0 takes the product off the order — that is
     * the only quantity the working copy won't hold.
     */
    private fun changeQuantity(localKey: Long, move: (EditableLine) -> EditableLine) {
        _workingLines.value = _workingLines.value.mapNotNull { line ->
            if (line.localKey != localKey) line else move(line).takeIf { it.quantity > 0 }
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

        val shipping = shippingChange(original)

        if (diff.isEmpty() && shipping == null) {
            cancelEditing()
            return
        }

        _saving.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                val api = WooCommerceApi(settings)
                _order.value = api.updateOrder(original.id, diff, shipping)
                _editing.value = false
                _workingLines.value = emptyList()
                _workingShipping.value = null
                _shippingEdited.value = false
            } catch (e: Exception) {
                _error.value = OrderError.from(e)
            } finally {
                _saving.value = false
            }
        }
    }

    /**
     * What to send for shipping, or null to leave the order's own line alone.
     *
     * Gated on the operator having touched it at all — see [_shippingEdited]. The gross figure on
     * screen becomes the net one WooCommerce stores on the way out, by the same conversion the till
     * uses, so a delivery costs the customer the same whether it was priced at the counter or
     * corrected afterwards.
     */
    private fun shippingChange(original: WooCommerceApi.OrderDetail): WooCommerceApi.ShippingChange? {
        if (!_shippingEdited.value) return null
        val existingId = original.shippingLine?.id
        val edited = _workingShipping.value
            ?: return existingId?.let { WooCommerceApi.ShippingChange.Remove(it) }
        val gross = edited.cost.toBigDecimalOrNull() ?: BigDecimal.ZERO
        return WooCommerceApi.ShippingChange.Set(
            lineId = existingId,
            methodId = edited.methodId,
            methodTitle = edited.methodTitle,
            total = checkout.value.shippingNet(gross, edited.taxable).toPlainString(),
        )
    }

    fun consumeMessage() { _message.value = null }

    fun consumeError() { _error.value = null }

    companion object {
        fun factory(
            orderId: Long,
            settingsRepository: SettingsRepository,
            productRepository: ProductRepository,
            organizationRepository: OrganizationRepository,
            checkoutConfigRepository: CheckoutConfigRepository,
        ) = viewModelFactory {
            initializer {
                OrderDetailViewModel(
                    orderId = orderId,
                    settingsRepository = settingsRepository,
                    productRepository = productRepository,
                    organizationRepository = organizationRepository,
                    checkoutConfigRepository = checkoutConfigRepository,
                )
            }
        }
    }
}
