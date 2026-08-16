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
import me.sourov.quicksale.data.local.CartCustomerRecord
import me.sourov.quicksale.data.local.CartLineRecord
import me.sourov.quicksale.data.local.CartRepository
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.local.packSizeNote
import me.sourov.quicksale.data.local.stepPackSize
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.settings.AddressField
import me.sourov.quicksale.data.settings.AddressFormRepository
import me.sourov.quicksale.data.settings.AddressForms
import me.sourov.quicksale.data.settings.CheckoutConfig
import me.sourov.quicksale.data.settings.CheckoutConfigRepository
import me.sourov.quicksale.data.settings.OrderOutcome
import me.sourov.quicksale.data.settings.PaymentGateway
import me.sourov.quicksale.data.settings.BackorderRepository
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.data.settings.shippingNet
import java.math.BigDecimal
import java.math.RoundingMode

/** A product picked for the order, with its chosen quantity. */
data class CartLine(val product: Product, val quantity: Int) {
    /** Line subtotal as a [BigDecimal]; treats an unparsable price as zero. */
    val lineTotal: BigDecimal
        get() = (product.price.toBigDecimalOrNull() ?: BigDecimal.ZERO) * quantity.toBigDecimal()

    /**
     * How many units of this line the store hasn't got — 0 when it can supply the lot, and 0 for a
     * product it doesn't count at all. This is the number the line warns with, so it is stated as
     * the shortfall rather than the stock level: "2 more than the shop has" is what the operator
     * has to tell the customer.
     */
    val beyondStock: Int
        get() = product.availableStock?.let { (quantity - it).coerceAtLeast(0) } ?: 0

    /** Whether − still leaves a line, rather than taking the product off the order. */
    val canStepDown: Boolean get() = quantity > 1

    /** How this quantity disagrees with the store's pack rule, or null when it sits on it. */
    val packSizeNote: String?
        get() = packSizeNote(quantity, product.packSize, product.quantityStep)

    /**
     * This line one unit smaller, whatever the product's pack size — 0 means it belongs off the
     * order.
     *
     * − is the one place the lattice is not enforced. A pack rule describes what the *store* will
     * sell, and the counter regularly has to say something else: a damaged unit pulled out of a
     * case, a visitor who wants five. Refusing that in the app doesn't make the shortfall go away,
     * it just moves the argument to the till — so the quantity is allowed and [packSizeNote] says
     * plainly that it is off the rule.
     */
    fun lowered(): CartLine = copy(quantity = quantity - 1)

    /**
     * This line moved [steps] of the product's order step, snapped onto a quantity the store
     * actually sells. A result of 0 means the line has fallen below the product's pack size.
     */
    fun stepped(steps: Int): CartLine =
        copy(quantity = stepPackSize(quantity, steps, product.packSize, product.quantityStep))

    companion object {
        /**
         * A line read back from the saved cart, priced against the catalog as it stands now.
         *
         * The quantity is kept exactly as it was left. It used to be snapped up onto the current
         * pack size, from back when the till could not produce an off-lattice quantity at all —
         * now that − can, a cart that came back rounded would be quietly undoing a deliberate
         * correction. Whatever it is, [packSizeNote] says so on the line.
         */
        fun restored(product: Product, storedQuantity: Int): CartLine =
            CartLine(product, storedQuantity.coerceAtLeast(1))
    }
}

/**
 * Where this order is going.
 *
 * There is one address form, not a choice between a saved location and a typed address. Picking a
 * location *fills* the form; the operator may then correct a house number without that correction
 * being written back to the location. What the request carries follows from whether anything was
 * corrected — see [SellViewModel.destination].
 */
data class DeliveryState(
    /** False for a walk-out sale: no location, no shipping lines, stamped with location `0`. */
    val enabled: Boolean = true,
    /** The location the form was filled from, or null when nothing has been picked. */
    val locationId: Long? = null,
    /** True once the form no longer matches [locationId]'s saved address. */
    val edited: Boolean = false,
)

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

/** Outcome of placing an order: the store's order id plus the totals and stamps it returned. */
sealed interface PlaceResult {
    data class Placed(
        val remoteId: Long,
        val total: String,
        val totalTax: String,
        val shippingTotal: String,
        val discountTotal: String,
        val organizationName: String,
        val locationName: String,
    ) : PlaceResult
}

/**
 * Why the Place order button is unavailable, or null when it isn't. [fatal] marks the reasons no
 * amount of tapping around fixes — the member or the organization simply may not buy.
 */
data class PlaceBlocker(val reason: String, val fatal: Boolean)

/** Who an order is for: one member of one organization. Null until the operator picks one. */
data class Customer(val organizationId: Long, val memberUserId: Long)

/**
 * The till: a standing cart that scans products first and learns who they are for later.
 *
 * The customer is state, not a constructor argument, because at a fair the visitor is standing at
 * the product — the scan has to happen before anyone has typed a company name. WooCommerce only
 * needs a `customer_id` when the order is *created*, so attaching it at checkout keeps the store's
 * B2B rule intact while removing five taps from the front of the job.
 *
 * One instance is scoped to the Sell tab and shared with the checkout page, so the cart survives
 * the hop to checkout and the trip back to add the thing the customer remembered at the till.
 */
class SellViewModel(
    private val organizationRepository: OrganizationRepository,
    private val productRepository: ProductRepository,
    private val cartRepository: CartRepository,
    private val settingsRepository: SettingsRepository,
    checkoutConfigRepository: CheckoutConfigRepository,
    addressFormRepository: AddressFormRepository,
    backorderRepository: BackorderRepository,
) : ViewModel() {

    /**
     * Whether selling past the shop's count is permitted. Read as state rather than awaited per
     * scan, because the guard sits in the middle of the fastest path in the app.
     */
    val allowBackorders: StateFlow<Boolean> = backorderRepository.allowed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BackorderRepository.DEFAULT_ALLOWED)

    private val _customer = MutableStateFlow<Customer?>(null)
    val customer: StateFlow<Customer?> = _customer.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val organization: StateFlow<Organization?> = _customer
        .flatMapLatest { who ->
            if (who == null) flowOf(null) else organizationRepository.organization(who.organizationId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    @OptIn(ExperimentalCoroutinesApi::class)
    val member: StateFlow<Member?> = _customer
        .flatMapLatest { who ->
            if (who == null) {
                flowOf(null)
            } else {
                organizationRepository.member(who.organizationId, who.memberUserId)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Only the locations this member is allowed to choose, default first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val locations: StateFlow<List<OrgLocation>> = member
        .flatMapLatest { current ->
            if (current == null) flowOf(emptyList()) else organizationRepository.locationsFor(current)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Every location the organization has, ignoring the member's access list.
     *
     * `location_access` limits what a member may *choose*, not what the company owns — so the
     * company sheet, which is a view of the account rather than of this order, shows all of them.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val allLocations: StateFlow<List<OrgLocation>> = _customer
        .flatMapLatest { who ->
            if (who == null) flowOf(emptyList()) else organizationRepository.locations(who.organizationId)
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val checkout: StateFlow<CheckoutConfig> = checkoutConfigRepository.config
        .stateIn(viewModelScope, SharingStarted.Eagerly, CheckoutConfig())

    val addressForms: StateFlow<AddressForms> = addressFormRepository.forms
        .stateIn(viewModelScope, SharingStarted.Eagerly, AddressForms())

    private val _lines = MutableStateFlow<List<CartLine>>(emptyList())
    val lines: StateFlow<List<CartLine>> = _lines.asStateFlow()

    val itemCount: StateFlow<Int> = _lines
        .map { lines -> lines.sumOf { it.quantity } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    private val _deliveryEnabled = MutableStateFlow(true)

    private val _locationId = MutableStateFlow<Long?>(null)

    private val _addressCountry = MutableStateFlow("")
    val addressCountry: StateFlow<String> = _addressCountry.asStateFlow()

    private val _addressValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val addressValues: StateFlow<Map<String, String>> = _addressValues.asStateFlow()

    /** Whether the delivery form still matches the location it was filled from. */
    private val addressEdited: StateFlow<Boolean> =
        combine(_locationId, _addressValues, locations) { locationId, values, available ->
            val location = available.firstOrNull { it.id == locationId } ?: return@combine false
            !location.matchesAddress(values)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val delivery: StateFlow<DeliveryState> =
        combine(_deliveryEnabled, _locationId, addressEdited, ::DeliveryState)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DeliveryState())

    /** The operator's explicit gateway pick; null falls back to the store's first gateway. */
    private val _gatewayChoice = MutableStateFlow<PaymentGateway?>(null)
    val selectedGateway: StateFlow<PaymentGateway?> =
        combine(_gatewayChoice, checkout) { choice, config ->
            choice ?: config.gateways.firstOrNull()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** The operator's explicit method pick; null falls back to the store's first method. */
    private val _shippingChoice = MutableStateFlow<ShippingOption?>(null)

    /**
     * The method this order ships by, or null when it isn't being shipped at all.
     *
     * There is no "no shipping" entry in the list to pick: the delivery switch is what says the
     * customer is carrying it away, so shipping follows that switch and the two can't disagree.
     */
    val selectedShipping: StateFlow<ShippingOption?> =
        combine(_shippingChoice, checkout, _deliveryEnabled) { choice, config, delivering ->
            if (delivering) choice ?: config.shippingOptions.firstOrNull() else null
        }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** A cost typed over the method's own; null means the method's configured cost still stands. */
    private val _shippingCostEdit = MutableStateFlow<String?>(null)
    val shippingCost: StateFlow<String> =
        combine(_shippingCostEdit, selectedShipping) { typed, option ->
            typed ?: option?.cost?.toBigDecimalOrNull()?.toPlainString() ?: "0"
        }.stateIn(viewModelScope, SharingStarted.Eagerly, "")

    private val _couponCode = MutableStateFlow("")
    val couponCode: StateFlow<String> = _couponCode.asStateFlow()

    val totals: StateFlow<TotalsPreview> =
        combine(_lines, selectedShipping, shippingCost, checkout) { lines, shipping, cost, config ->
            previewTotals(lines, shipping, cost, config)
        }.stateIn(viewModelScope, SharingStarted.Eagerly, TotalsPreview())

    /** The visible fields of the delivery form for the chosen country, in WooCommerce's order. */
    val addressFields: StateFlow<List<AddressField>> =
        combine(addressForms, _addressCountry) { forms, country ->
            forms.fieldsFor(country.ifBlank { forms.defaultCountry })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Required address fields still empty, by label. Client-side validation stops here
     * deliberately: the store validates every typed address with its own per-country rules —
     * postcode format, states from the country's list — and its answers are the authoritative ones.
     */
    val missingAddressFields: StateFlow<List<String>> =
        combine(addressFields, _addressValues, _deliveryEnabled) { fields, values, enabled ->
            if (!enabled) {
                emptyList()
            } else {
                fields.filter { it.required && values[it.name].orEmpty().isBlank() }.map { it.label }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Who this order is for and what's in it — grouped so [blocker] stays a typed combine. */
    private data class Buyer(
        val customer: Customer?,
        val organization: Organization?,
        val member: Member?,
        val lines: List<CartLine>,
    )

    private val buyer = combine(_customer, organization, member, _lines, ::Buyer)

    /** Why the order can't be placed yet, or null when it can. */
    val blocker: StateFlow<PlaceBlocker?> = combine(
        buyer,
        _deliveryEnabled,
        missingAddressFields,
        addressFields,
        allowBackorders,
    ) { who, deliveryEnabled, missing, fields, backordersAllowed ->
        placeBlocker(
            customer = who.customer,
            organization = who.organization,
            member = who.member,
            lines = who.lines,
            deliveryEnabled = deliveryEnabled,
            missingFields = missing,
            addressFieldCount = fields.size,
            allowBackorders = backordersAllowed,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

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

    private val _placing = MutableStateFlow(false)
    val placing: StateFlow<Boolean> = _placing.asStateFlow()

    private val _placed = MutableStateFlow<PlaceResult?>(null)
    val placed: StateFlow<PlaceResult?> = _placed.asStateFlow()

    init {
        // Bring back whatever was in the cart when the app was last killed, before anything else
        // touches it. Everything about each product — its price, its pack size, and so the quantity
        // that line is allowed to hold — comes from the catalog as it is now, not as it was.
        viewModelScope.launch {
            val (storedLines, storedCustomer) = cartRepository.load()
            if (storedLines.isNotEmpty() || storedCustomer != null) {
                val restored = storedLines.mapNotNull { record ->
                    // A product deleted from the store since the scan simply drops out of the
                    // cart — it can no longer be ordered, and carrying it would only fail at
                    // Place order.
                    productRepository.byId(record.productId)?.let { CartLine.restored(it, record.quantity) }
                }
                // Only fill an untouched cart: the operator may have scanned something in the
                // moment between the screen appearing and this read returning, and that scan is
                // the newer intent.
                if (_lines.value.isEmpty()) _lines.value = restored
                if (_customer.value == null && storedCustomer?.organizationId != null &&
                    storedCustomer.memberUserId != null
                ) {
                    _customer.value =
                        Customer(storedCustomer.organizationId, storedCustomer.memberUserId)
                }
            }
            // Started unconditionally, and only once the read above has returned: skipping it when
            // there was nothing to restore is what left the very first cart unsaved.
            persistOnChange()
        }

        // Fill the delivery form from the member's default location, so the common case needs no
        // taps at all: the address is already the one this order was always going to.
        //
        // Collected rather than awaited once, because the customer can now change mid-cart — when
        // it does, the location held here belongs to the previous account and has to be replaced.
        viewModelScope.launch {
            locations.collect { available ->
                if (available.isNotEmpty() && available.none { it.id == _locationId.value }) {
                    selectLocation((available.firstOrNull { it.isDefault } ?: available.first()).id)
                }
            }
        }
        // An account with no locations still needs a form; start it on the shop's own base country.
        viewModelScope.launch {
            val forms = addressForms.first { !it.isEmpty }
            if (_addressCountry.value.isBlank()) selectAddressCountry(forms.defaultCountry)
        }
    }

    /**
     * Attaches this cart to a member of an organization, or detaches it when [customer] is null.
     *
     * The delivery form is cleared rather than carried over: the previous account's location is not a
     * plausible default for this one, and a stale address silently attached to the wrong company is
     * exactly the mistake that survives all the way to a delivery van.
     */
    fun selectCustomer(customer: Customer?) {
        if (_customer.value == customer) return
        _customer.value = customer
        _locationId.value = null
        _addressValues.value = emptyMap()
        _addressCountry.value = addressForms.value.defaultCountry
    }

    fun onQueryChange(value: String) { _query.value = value }

    /** Empties the cart. The customer, if one was chosen, is dropped with it. */
    fun clearCart() {
        _lines.value = emptyList()
        _query.value = ""
        selectCustomer(null)
    }

    fun selectGateway(gateway: PaymentGateway) { _gatewayChoice.value = gateway }

    fun setDeliveryEnabled(enabled: Boolean) { _deliveryEnabled.value = enabled }

    /**
     * Fills the delivery form from a saved location.
     *
     * The location is copied into the form, not referenced by it — which is what lets the operator
     * correct a house number for one order without that correction reaching the company's records.
     */
    fun selectLocation(locationId: Long) {
        val location = locations.value.firstOrNull { it.id == locationId } ?: return
        _locationId.value = locationId
        val fields = location.toAddressFields()
        _addressCountry.value = fields["country"].orEmpty()
        _addressValues.value = fields
    }

    /** Puts the location's own address back, discarding this order's edits. */
    fun resetAddressToLocation() { _locationId.value?.let(::selectLocation) }

    fun selectAddressCountry(code: String) {
        _addressCountry.value = code
        // Field definitions differ per country, so values whose field no longer exists are dropped
        // rather than posted under a name this country's form never had.
        val allowed = addressForms.value.fieldsFor(code).map { it.name }.toSet()
        _addressValues.value = _addressValues.value.filterKeys { it in allowed } + ("country" to code)
    }

    fun setAddressField(name: String, value: String) {
        _addressValues.value = _addressValues.value + (name to value)
    }

    /** Picks a shipping method; anything typed over the last one's cost gives way to this one's. */
    fun selectShipping(option: ShippingOption) {
        _shippingChoice.value = option
        _shippingCostEdit.value = null
    }

    fun setShippingCost(value: String) { _shippingCostEdit.value = value }

    fun setCouponCode(value: String) { _couponCode.value = value }

    /**
     * Why [product] may not go up to [quantity], or null when it may.
     *
     * Only ever refuses when backorders are switched off — with them on, going past the shelf is
     * the point, and the shortfall is reported rather than blocked. A product the store doesn't
     * count has no ceiling to breach.
     */
    private fun stockRefusal(product: Product, quantity: Int): String? {
        if (allowBackorders.value) return null
        val available = product.availableStock ?: return null
        if (quantity <= available) return null
        return if (available == 0) {
            "${product.name} is out of stock"
        } else {
            "Only $available of ${product.name} in stock"
        }
    }

    /**
     * Rings [product] up and returns what to tell the operator — added, added-but-short, or why not.
     *
     * A first scan rings up the product's pack size rather than a single unit, and a repeat scan
     * adds another case — so a product the store only sells in sixes never enters the order as a
     * quantity WooCommerce would refuse.
     *
     * Both callers route through here so the stock check can't be walked around by reaching the
     * cart another way, and so "Added X" is written once rather than at every entrance.
     */
    private fun ringUp(product: Product): String {
        val current = _lines.value
        val index = current.indexOfFirst { it.product.id == product.id }
        val existing = current.getOrNull(index)

        // Already in the cart means another case on the line; otherwise a new line at pack size.
        val next = existing?.stepped(+1) ?: CartLine(product, product.packSize)

        stockRefusal(product, next.quantity)?.let { return it }

        _lines.value = if (existing != null) {
            current.toMutableList().also { it[index] = next }
        } else {
            current + next
        }

        return if (next.beyondStock > 0) {
            "Added ${product.name} — ${next.beyondStock} beyond stock"
        } else {
            "Added ${product.name}"
        }
    }

    fun addFromSearch(product: Product) {
        val message = ringUp(product)
        _query.value = ""
        _message.value = message
    }

    /** Handles the search field's "go" action (also how a typed/pasted barcode is submitted). */
    fun submitTyped() {
        val code = _query.value.trim()
        if (code.isEmpty()) return
        viewModelScope.launch { handleCode(code) }
    }

    /**
     * Mirrors every later change to the cart onto disk.
     *
     * Started only after the restore above has finished, so an empty starting state can never be
     * written over a cart that is still being read back.
     */
    private fun persistOnChange() {
        viewModelScope.launch {
            combine(_lines, _customer) { lines, who -> lines to who }
                .collect { (lines, who) ->
                    cartRepository.save(
                        lines = lines.mapIndexed { index, line ->
                            CartLineRecord(
                                productId = line.product.id,
                                quantity = line.quantity,
                                // The list is already in scan order; the index preserves it.
                                addedAtMillis = index.toLong(),
                            )
                        },
                        customer = who?.let {
                            CartCustomerRecord(
                                organizationId = it.organizationId,
                                memberUserId = it.memberUserId,
                            )
                        },
                    )
                }
        }
    }

    /**
     * A hardware scan, from the Sell screen only.
     *
     * Collected by the composable rather than here, because this view model outlives the tab: a
     * subscription in `init` would keep ringing products into the cart while the operator was on
     * the Print tab printing labels, or on Products looking something up.
     */
    fun onScan(code: String) {
        viewModelScope.launch { handleCode(code.trim()) }
    }

    /**
     * Resolves a scanned or typed [code]: an exact EAN or SKU match is added straight to the cart;
     * otherwise the code becomes the search query so the operator can pick a matching product.
     */
    private suspend fun handleCode(code: String) {
        if (code.isEmpty()) return
        val product = productRepository.findByCode(code)
        when {
            product == null -> {
                _query.value = code
                _message.value = "No product matches \"$code\""
            }
            // The product exists but the store hasn't published it. Saying so is the whole reason
            // the lookup ignores status: "no product matches" would send the operator hunting for a
            // catalog or scanner fault that isn't there, when the fix is one click on the website.
            !product.isPublished -> {
                _query.value = ""
                _message.value = "${product.name} is ${product.statusLabel} on the store, so it can't be ordered"
            }
            else -> {
                val message = ringUp(product)
                _query.value = ""
                _message.value = message
            }
        }
    }

    fun increment(productId: Long) {
        val line = _lines.value.firstOrNull { it.product.id == productId } ?: return
        stockRefusal(line.product, line.stepped(+1).quantity)?.let {
            _message.value = it
            return
        }
        changeQuantity(productId) { it.stepped(+1) }
    }

    /** − comes down one unit at a time, on or off the pack size — see [CartLine.lowered]. */
    fun decrement(productId: Long) = changeQuantity(productId, CartLine::lowered)

    /**
     * Whether a held − may keep running, read live rather than from the last composition.
     *
     * A hold is for bringing a quantity down; taking the product off the order is a separate
     * decision, and it should cost a deliberate tap on the bin rather than arriving as the tail end
     * of a hold nobody released in time. At the fastest the repeat reaches, a frame of stale state
     * is a line already gone — so this reads the cart as it stands, not as it was last drawn.
     */
    fun canStepDown(productId: Long): Boolean =
        _lines.value.firstOrNull { it.product.id == productId }?.canStepDown == true

    /**
     * Applies [move] to one line. A move that lands on 0 takes the product off the order — that is
     * the only quantity the cart won't hold.
     */
    private fun changeQuantity(productId: Long, move: (CartLine) -> CartLine) {
        _lines.value = _lines.value.mapNotNull { line ->
            if (line.product.id != productId) line else move(line).takeIf { it.quantity > 0 }
        }
    }

    fun remove(productId: Long) {
        _lines.value = _lines.value.filterNot { it.product.id == productId }
    }

    fun placeOrder() {
        if (_placing.value) return
        blocker.value?.let {
            _message.value = it.reason
            return
        }
        val member = member.value ?: return
        val current = _lines.value
        if (current.isEmpty()) return

        _placing.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                if (!settings.isConfigured) {
                    _message.value = "Connect your store in Settings to place orders"
                    return@launch
                }
                val config = checkout.value
                // Recorded on the order, but it decides nothing here: every order is created on
                // hold and unpaid for the shop to confirm — see [OrderOutcome].
                val gateway = selectedGateway.value
                try {
                    val api = WooCommerceApi(settings)
                    val order = api.createOrder(
                        // The member's WordPress user id is what makes this the member's order.
                        customerId = member.userId,
                        lineItems = current.map { WooCommerceApi.LineItem(it.product.id, it.quantity) },
                        destination = destination(),
                        paymentMethod = gateway,
                        shipping = shippingSelection(config),
                        couponCode = _couponCode.value,
                    )
                    refreshOrderedProducts(api, current)
                    // Read the fallback names off the cart before emptying it — clearing detaches
                    // the customer, and the confirmation still has to say who the order was for.
                    val result = PlaceResult.Placed(
                        remoteId = order.id,
                        total = order.total,
                        totalTax = order.totalTax,
                        shippingTotal = order.shippingTotal,
                        discountTotal = order.discountTotal,
                        organizationName = order.organizationName
                            .ifBlank { organization.value?.name.orEmpty() },
                        locationName = order.locationName.ifBlank { chosenLocationName() },
                    )
                    // The cart used to die with its back-stack entry; this one lives on the Sell
                    // tab, so it has to be emptied here or the next customer walks up to the last
                    // customer's order still on screen — one tap from being sold twice.
                    clearCart()
                    _placed.value = result
                } catch (e: Exception) {
                    // The cart is deliberately left intact so the operator can fix whatever the
                    // store objected to — or simply try again — without re-scanning everything.
                    _error.value = OrderError.from(e)
                }
            } finally {
                _placing.value = false
            }
        }
    }

    /**
     * Translates the delivery form into what the order request carries.
     *
     * An untouched form is posted as its location's ID: the store resolves that against the member's
     * access list and stamps the location's name on the order, which is both cheaper and more
     * truthful than re-posting an address the store already holds. Only an *edited* form becomes a
     * typed address — and it is sent even for an account that forbids custom shipping, so the
     * refusal the operator sees is the store's own reason rather than a guess made here.
     */
    private fun destination(): WooCommerceApi.Destination {
        if (!_deliveryEnabled.value) return WooCommerceApi.Destination.None
        val locationId = _locationId.value
        if (locationId != null && !addressEdited.value) {
            return WooCommerceApi.Destination.Location(locationId)
        }
        return WooCommerceApi.Destination.OneOff(
            // Only the fields this country's form actually defines, so nothing stray is posted.
            fields = addressFields.value.associate { field ->
                field.name to _addressValues.value[field.name].orEmpty()
            } + ("country" to currentCountry()),
        )
    }

    private fun currentCountry(): String =
        _addressCountry.value.ifBlank { addressForms.value.defaultCountry }

    /** The location name to show on the confirmation when the store doesn't stamp one itself. */
    private fun chosenLocationName(): String {
        if (!_deliveryEnabled.value || addressEdited.value) return ""
        val locationId = _locationId.value ?: return ""
        return locations.value.firstOrNull { it.id == locationId }?.name.orEmpty()
    }

    private fun placeBlocker(
        customer: Customer?,
        organization: Organization?,
        member: Member?,
        lines: List<CartLine>,
        deliveryEnabled: Boolean,
        missingFields: List<String>,
        addressFieldCount: Int,
        allowBackorders: Boolean,
    ): PlaceBlocker? {
        if (lines.isEmpty()) return PlaceBlocker("Add at least one product", false)
        // Checked here as well as at the scan, because a cart can outlast the setting: rung up
        // while backorders were on, then still standing when someone turns them off.
        if (!allowBackorders) {
            lines.firstOrNull { it.beyondStock > 0 }?.let { short ->
                val available = short.product.availableStock ?: 0
                return PlaceBlocker(
                    if (available == 0) {
                        "${short.product.name} is out of stock"
                    } else {
                        "Only $available of ${short.product.name} in stock"
                    },
                    false,
                )
            }
        }
        // No customer yet is the normal state of a cart being filled, not a fault. It stops the
        // order being placed and nothing else — the scanning half of the screen stays live.
        if (customer == null) return PlaceBlocker("Choose who this order is for", false)
        if (organization == null || member == null) return PlaceBlocker("Loading this account…", false)
        if (!organization.orgStatus.canTrade) {
            return PlaceBlocker(
                "${organization.name} is ${organization.orgStatus.label.lowercase()} and can't order",
                fatal = true,
            )
        }
        // The store's own resolved answer, used as given rather than re-derived from role/status.
        if (!member.canPlaceOrders) {
            return PlaceBlocker("${member.name} isn't allowed to place orders", fatal = true)
        }
        if (deliveryEnabled) {
            // With no synced form there are no fields to fill, so "nothing missing" would be a
            // false pass — the request would carry an empty shipping block for the store to reject.
            if (addressFieldCount == 0) {
                return PlaceBlocker("Sync accounts to enter an address", false)
            }
            if (missingFields.isNotEmpty()) {
                return PlaceBlocker("Fill in ${missingFields.joinToString(", ")}", false)
            }
        }
        return null
    }

    /**
     * Builds the shipping line for the order request. WooCommerce treats `shipping_lines.total`
     * as a NET amount and adds tax on top, so on tax-inclusive stores the entered (gross) cost is
     * converted to net first — otherwise the customer would be charged more than web checkout.
     */
    private fun shippingSelection(config: CheckoutConfig): WooCommerceApi.ShippingSelection? {
        val option = selectedShipping.value ?: return null
        val gross = shippingCost.value.toBigDecimalOrNull() ?: BigDecimal.ZERO
        val net = config.shippingNet(gross, option.taxable)
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

    fun consumeError() { _error.value = null }

    /**
     * Clears the placed-order result once the confirmation has been navigated to.
     *
     * The view model outlives the order now, so a result left standing would send the *next* trip
     * to the checkout straight back to the last order's confirmation screen.
     */
    fun consumePlaced() { _placed.value = null }

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
            organizationRepository: OrganizationRepository,
            productRepository: ProductRepository,
            cartRepository: CartRepository,
            settingsRepository: SettingsRepository,
            checkoutConfigRepository: CheckoutConfigRepository,
            addressFormRepository: AddressFormRepository,
            backorderRepository: BackorderRepository,
        ) = viewModelFactory {
            initializer {
                SellViewModel(
                    organizationRepository = organizationRepository,
                    productRepository = productRepository,
                    cartRepository = cartRepository,
                    settingsRepository = settingsRepository,
                    checkoutConfigRepository = checkoutConfigRepository,
                    addressFormRepository = addressFormRepository,
                    backorderRepository = backorderRepository,
                )
            }
        }
    }
}
