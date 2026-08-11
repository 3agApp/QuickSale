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
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.settings.AddressField
import me.sourov.quicksale.data.settings.AddressFormRepository
import me.sourov.quicksale.data.settings.AddressForms
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

    /**
     * This line moved [steps] of the product's order step, snapped onto a quantity the store
     * actually sells. A result of 0 means the line has fallen below the product's pack size.
     */
    fun stepped(steps: Int): CartLine =
        copy(quantity = product.snapOrderQuantity(quantity + steps * product.orderQuantityStep.coerceAtLeast(1)))
}

/**
 * Where this order is going. Mirrors what the store accepts: a saved location resolved by ID, a
 * typed one-off address, or nothing at all for a walk-out sale.
 */
sealed interface DeliveryChoice {
    data object None : DeliveryChoice
    data class AtLocation(val locationId: Long) : DeliveryChoice
    data object OneOffAddress : DeliveryChoice
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

class NewOrderViewModel(
    private val organizationId: Long,
    private val memberUserId: Long,
    organizationRepository: OrganizationRepository,
    private val productRepository: ProductRepository,
    private val settingsRepository: SettingsRepository,
    private val orderSettingsRepository: OrderSettingsRepository,
    checkoutConfigRepository: CheckoutConfigRepository,
    addressFormRepository: AddressFormRepository,
) : ViewModel() {

    val organization: StateFlow<Organization?> = organizationRepository.organization(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val member: StateFlow<Member?> = organizationRepository.member(organizationId, memberUserId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Only the locations this member is allowed to choose, default first. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val locations: StateFlow<List<OrgLocation>> = member
        .flatMapLatest { current ->
            if (current == null) flowOf(emptyList()) else organizationRepository.locationsFor(current)
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

    private val _delivery = MutableStateFlow<DeliveryChoice>(DeliveryChoice.None)
    val delivery: StateFlow<DeliveryChoice> = _delivery.asStateFlow()

    private val _oneOffCountry = MutableStateFlow("")
    val oneOffCountry: StateFlow<String> = _oneOffCountry.asStateFlow()

    private val _oneOffValues = MutableStateFlow<Map<String, String>>(emptyMap())
    val oneOffValues: StateFlow<Map<String, String>> = _oneOffValues.asStateFlow()

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

    /** The visible fields of the one-off form for the chosen country, in WooCommerce's order. */
    val oneOffFields: StateFlow<List<AddressField>> =
        combine(addressForms, _oneOffCountry) { forms, country ->
            forms.fieldsFor(country.ifBlank { forms.defaultCountry })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Required one-off fields still empty, by label. Client-side validation stops here
     * deliberately: the store validates every one-off address with its own per-country rules —
     * postcode format, states from the country's list — and its answers are the authoritative ones.
     */
    val missingOneOffFields: StateFlow<List<String>> =
        combine(oneOffFields, _oneOffValues, _delivery) { fields, values, delivery ->
            if (delivery !is DeliveryChoice.OneOffAddress) {
                emptyList()
            } else {
                fields.filter { it.required && values[it.name].orEmpty().isBlank() }.map { it.label }
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Who this order is for and what's in it — grouped so [blocker] stays a typed combine. */
    private data class Buyer(
        val organization: Organization?,
        val member: Member?,
        val lines: List<CartLine>,
    )

    private val buyer = combine(organization, member, _lines, ::Buyer)

    /** Why the order can't be placed yet, or null when it can. */
    val blocker: StateFlow<PlaceBlocker?> = combine(
        buyer,
        _delivery,
        _shippingChoice,
        missingOneOffFields,
        oneOffFields,
    ) { who, delivery, shipping, missing, fields ->
        placeBlocker(
            organization = who.organization,
            member = who.member,
            lines = who.lines,
            delivery = delivery,
            shipping = shipping,
            missingFields = missing,
            oneOffFieldCount = fields.size,
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
        // Hardware scans (broadcast intents OR keyboard/HID, per Settings) all arrive via ScannerHub,
        // independent of which field is focused. Add the scanned product straight to the order.
        viewModelScope.launch {
            ScannerHub.scans.collect { code -> handleCode(code.trim()) }
        }
        // Preselect the member's default location so the common case needs no taps.
        viewModelScope.launch {
            val available = locations.first { it.isNotEmpty() }
            if (_delivery.value is DeliveryChoice.None) {
                val preferred = available.firstOrNull { it.isDefault } ?: available.first()
                _delivery.value = DeliveryChoice.AtLocation(preferred.id)
            }
        }
        // Start the one-off form on the shop's own base country.
        viewModelScope.launch {
            val forms = addressForms.first { !it.isEmpty }
            if (_oneOffCountry.value.isBlank()) selectOneOffCountry(forms.defaultCountry)
        }
    }

    fun onQueryChange(value: String) { _query.value = value }

    fun selectGateway(gateway: PaymentGateway) { _gatewayChoice.value = gateway }

    fun selectDelivery(choice: DeliveryChoice) { _delivery.value = choice }

    fun selectOneOffCountry(code: String) {
        _oneOffCountry.value = code
        // Field definitions differ per country, so values whose field no longer exists are dropped
        // rather than posted under a name this country's form never had.
        val allowed = addressForms.value.fieldsFor(code).map { it.name }.toSet()
        _oneOffValues.value = _oneOffValues.value.filterKeys { it in allowed } + ("country" to code)
    }

    fun setOneOffField(name: String, value: String) {
        _oneOffValues.value = _oneOffValues.value + (name to value)
    }

    /** Picks a shipping method (or null for no shipping) and pre-fills its configured cost. */
    fun selectShipping(option: ShippingOption?) {
        _shippingChoice.value = option
        _shippingCost.value = option?.cost?.toBigDecimalOrNull()?.toPlainString()
            ?: if (option != null) "0" else ""
    }

    fun setShippingCost(value: String) { _shippingCost.value = value }

    fun setCouponCode(value: String) { _couponCode.value = value }

    /**
     * Adds a product to the cart, or bumps its quantity if already present.
     *
     * A first scan rings up the product's pack size rather than a single unit, and a repeat scan
     * adds another case — so a product the store only sells in sixes never enters the order as a
     * quantity WooCommerce would refuse.
     */
    fun addProduct(product: Product) {
        _lines.value = _lines.value.let { current ->
            val index = current.indexOfFirst { it.product.id == product.id }
            if (index >= 0) {
                current.toMutableList().also { lines ->
                    lines[index] = lines[index].stepped(+1)
                }
            } else {
                current + CartLine(product, product.snapOrderQuantity(product.minOrderQuantity))
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
                addProduct(product)
                _query.value = ""
                _message.value = "Added ${product.name}"
            }
        }
    }

    fun increment(productId: Long) = changeQuantity(productId, +1)
    fun decrement(productId: Long) = changeQuantity(productId, -1)

    /**
     * Moves a line by [steps] of the product's own order step, never off it. Dropping below the
     * product's minimum removes the line rather than leaving a quantity the store won't sell.
     */
    private fun changeQuantity(productId: Long, steps: Int) {
        _lines.value = _lines.value.mapNotNull { line ->
            if (line.product.id != productId) line else line.stepped(steps).takeIf { it.quantity > 0 }
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
                val status = orderSettingsRepository.status.first()
                val config = checkout.value
                try {
                    val api = WooCommerceApi(settings)
                    val order = api.createOrder(
                        // The member's WordPress user id is what makes this the member's order.
                        customerId = member.userId,
                        lineItems = current.map { WooCommerceApi.LineItem(it.product.id, it.quantity) },
                        status = status.slug,
                        setPaid = status.setPaid,
                        destination = destination(),
                        paymentMethod = selectedGateway.value,
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
                        organizationName = order.organizationName
                            .ifBlank { organization.value?.name.orEmpty() },
                        locationName = order.locationName.ifBlank { chosenLocationName() },
                    )
                } catch (e: Exception) {
                    _error.value = OrderError.from(e)
                }
            } finally {
                _placing.value = false
            }
        }
    }

    /** Translates the operator's delivery choice into what the order request carries. */
    private fun destination(): WooCommerceApi.Destination = when (val choice = _delivery.value) {
        is DeliveryChoice.AtLocation -> WooCommerceApi.Destination.Location(choice.locationId)

        DeliveryChoice.OneOffAddress -> WooCommerceApi.Destination.OneOff(
            // Only the fields this country's form actually defines, so nothing stray is posted.
            fields = oneOffFields.value.associate { field ->
                field.name to _oneOffValues.value[field.name].orEmpty()
            } + ("country" to currentCountry()),
        )

        DeliveryChoice.None -> WooCommerceApi.Destination.None
    }

    private fun currentCountry(): String =
        _oneOffCountry.value.ifBlank { addressForms.value.defaultCountry }

    private fun chosenLocationName(): String {
        val choice = _delivery.value as? DeliveryChoice.AtLocation ?: return ""
        return locations.value.firstOrNull { it.id == choice.locationId }?.name.orEmpty()
    }

    private fun placeBlocker(
        organization: Organization?,
        member: Member?,
        lines: List<CartLine>,
        delivery: DeliveryChoice,
        shipping: ShippingOption?,
        missingFields: List<String>,
        oneOffFieldCount: Int,
    ): PlaceBlocker? {
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
        if (lines.isEmpty()) return PlaceBlocker("Add at least one product", false)
        // WooCommerce decides an order "needs delivery" from its shipping lines, not its products.
        if (shipping != null && delivery is DeliveryChoice.None) {
            return PlaceBlocker("Choose where this order is going", false)
        }
        if (delivery is DeliveryChoice.OneOffAddress) {
            if (!organization.allowCustomShipping) {
                return PlaceBlocker("${organization.name} only accepts its saved locations", false)
            }
            // With no synced form there are no fields to fill, so "nothing missing" would be a
            // false pass — the request would carry an empty shipping block for the store to reject.
            if (oneOffFieldCount == 0) {
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

    fun consumeError() { _error.value = null }

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
            organizationId: Long,
            memberUserId: Long,
            organizationRepository: OrganizationRepository,
            productRepository: ProductRepository,
            settingsRepository: SettingsRepository,
            orderSettingsRepository: OrderSettingsRepository,
            checkoutConfigRepository: CheckoutConfigRepository,
            addressFormRepository: AddressFormRepository,
        ) = viewModelFactory {
            initializer {
                NewOrderViewModel(
                    organizationId = organizationId,
                    memberUserId = memberUserId,
                    organizationRepository = organizationRepository,
                    productRepository = productRepository,
                    settingsRepository = settingsRepository,
                    orderSettingsRepository = orderSettingsRepository,
                    checkoutConfigRepository = checkoutConfigRepository,
                    addressFormRepository = addressFormRepository,
                )
            }
        }
    }
}
