package me.sourov.quicksale.data.remote

import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.settings.CheckoutConfig
import me.sourov.quicksale.data.settings.CurrencyPosition
import me.sourov.quicksale.data.settings.OrderOutcome
import me.sourov.quicksale.data.settings.PaymentGateway
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.data.settings.StoreCurrency
import me.sourov.quicksale.data.settings.StoreSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * WooCommerce's own `/wc/v3` routes: the catalog, the store's checkout configuration, and orders.
 *
 * Orders go through here rather than a plugin-specific route — the organization layer is a handful
 * of extra fields on WooCommerce's standard order endpoint, so line items, tax, coupons and stock
 * all behave exactly as they do for any other order.
 */
class WooCommerceApi(settings: StoreSettings) {

    private val http = WooHttp(settings)

    /**
     * The address images are rewritten against — see [throughSiteProxy]. Held rather than read per
     * product: it can't change without a new [WooCommerceApi], since the connection did too.
     */
    private val siteUrl = settings.siteUrl

    data class Page<T>(val items: List<T>, val totalPages: Int)

    /**
     * A page of the catalog, in every status the store holds it in.
     *
     * No `status` filter is sent, and WooCommerce's own default for this route is `any` — drafts,
     * pending and private products all come down. That is on purpose: the till keeps a faithful
     * copy of the shop, and [Product.isPublished] decides what the counter may do with each row.
     */
    suspend fun fetchProducts(page: Int, perPage: Int = 100): Page<Product> {
        val response = http.get(
            path = "wc/v3/products",
            query = mapOf("page" to page.toString(), "per_page" to perPage.toString()),
        )
        val array = JSONArray(response.body)
        val items = buildList(array.length()) {
            for (i in 0 until array.length()) add(array.getJSONObject(i).toProduct())
        }
        return Page(items, response.totalPages)
    }

    /** A single fresh product (e.g. to refresh local stock right after an order). */
    suspend fun fetchProduct(id: Long): Product =
        JSONObject(http.get("wc/v3/products/$id").body).toProduct()

    /**
     * Reads the store's current currency, and how the store writes prices with it.
     *
     * Two routes, because WooCommerce splits the fact: `/wc/v3/data/currencies/current` names the
     * currency and its symbol, while the separators, decimal count and symbol position live in
     * `/wc/v3/settings/general` alongside every other shop option. The second read is optional —
     * a store that refuses it still gets the right symbol, formatted WooCommerce's own defaults.
     */
    suspend fun fetchCurrency(): StoreCurrency {
        val json = JSONObject(http.get("wc/v3/data/currencies/current").body)
        val base = StoreCurrency(
            code = json.optString("code"),
            // WooCommerce sometimes returns the symbol as an HTML entity (e.g. "&#36;").
            symbol = json.optString("symbol").decodeHtmlEntities().ifBlank { StoreCurrency.DEFAULT_SYMBOL },
        )
        val general = runCatching { JSONArray(body("wc/v3/settings/general")).settingsMap() }
            .getOrNull() ?: return base
        val defaults = StoreCurrency()
        return base.copy(
            position = CurrencyPosition.fromSlug(general["woocommerce_currency_pos"]),
            // Grouping with nothing at all is a setting a store really does choose, so an empty
            // value is taken at face value; only a key the store never sent falls back.
            thousandSeparator = general["woocommerce_price_thousand_sep"] ?: defaults.thousandSeparator,
            decimalSeparator = general["woocommerce_price_decimal_sep"]?.takeIf { it.isNotEmpty() }
                ?: defaults.decimalSeparator,
            decimals = general["woocommerce_price_num_decimals"]?.toIntOrNull()
                ?: defaults.decimals,
        )
    }

    /**
     * A product line to send when creating or updating an order.
     *
     * [id] is the WooCommerce line item id and is only ever set when editing an existing order:
     * present with a positive [quantity] it updates that line, present with `quantity = 0` it
     * removes the line, and absent it adds a new line. Order creation never sets it.
     */
    data class LineItem(
        val productId: Long,
        val quantity: Int,
        val id: Long? = null,
        val subtotal: String? = null,
        val total: String? = null,
    )

    /** A shipping charge to attach to an order. [total] is the net (pre-tax) amount. */
    data class ShippingSelection(val methodId: String, val methodTitle: String, val total: String)

    /**
     * Where an order is being delivered.
     *
     * The store decides whether an order needs a destination at all by looking at its shipping
     * lines, not its products — so a walk-out sale is [None] and is stamped with location `0`.
     */
    sealed interface Destination {
        /** No delivery: no shipping lines, no location. */
        data object None : Destination

        /** One of the organization's saved locations, resolved server-side against the member. */
        data class Location(val id: Long) : Destination

        /**
         * A typed address, allowed only when the organization permits custom shipping. The map is
         * keyed by the field names the address form supplied, and the store validates it with the
         * same rules its checkout applies.
         */
        data class OneOff(val fields: Map<String, String>) : Destination
    }

    /** Totals WooCommerce calculated for a newly created order (tax included where configured). */
    data class CreatedOrder(
        val id: Long,
        val total: String,
        val totalTax: String,
        val shippingTotal: String,
        val discountTotal: String,
        val organizationName: String,
        val locationName: String,
    )

    /**
     * Creates an order and returns the totals the store calculated (the store is authoritative for
     * tax, and re-runs every organization rule regardless of what the app believes).
     *
     * [customerId] must be the member's WordPress `user_id` from the organization snapshot — that
     * is what makes the order the member's, putting it in their *My orders*, their organization's
     * order list and the customer emails. The API key only identifies the till.
     *
     * No billing block is sent: the store writes the organization's own billing address over
     * anything posted, so sending one changes nothing.
     *
     * Every order is created on hold and unpaid, whatever [paymentMethod] says — see [OrderOutcome].
     *
     * @param couponCode optional coupon the store validates and applies server-side.
     * @throws WooApiException with `woap_rest_cannot_purchase`, `woap_rest_shipping_destination`
     *   or `woap_rest_shipping_address` when the store refuses. No order exists after a refusal.
     */
    suspend fun createOrder(
        customerId: Long,
        lineItems: List<LineItem>,
        destination: Destination,
        paymentMethod: PaymentGateway? = null,
        shipping: ShippingSelection? = null,
        couponCode: String? = null,
    ): CreatedOrder {
        val payload = JSONObject().apply {
            put("customer_id", customerId)
            // Both sent on every order, unconditionally. `set_paid` is the one that matters: left
            // to WooCommerce it would run `payment_complete()` and carry the order past the hold.
            put("status", OrderOutcome.STATUS)
            put("set_paid", OrderOutcome.SET_PAID)
            paymentMethod?.let {
                put("payment_method", it.id)
                put("payment_method_title", it.title)
            }
            when (destination) {
                is Destination.Location -> put("woap_location_id", destination.id)
                is Destination.OneOff -> put("shipping", JSONObject().apply {
                    destination.fields.forEach { (name, value) -> put(name, value) }
                })
                Destination.None -> Unit
            }
            put("line_items", JSONArray().apply {
                lineItems.forEach { item ->
                    put(JSONObject().apply {
                        item.id?.let { put("id", it) }
                        put("product_id", item.productId)
                        put("quantity", item.quantity)
                    })
                }
            })
            shipping?.let {
                put("shipping_lines", JSONArray().put(JSONObject().apply {
                    put("method_id", it.methodId)
                    put("method_title", it.methodTitle)
                    put("total", it.total)
                }))
            }
            couponCode?.takeIf { it.isNotBlank() }?.let {
                put("coupon_lines", JSONArray().put(JSONObject().put("code", it.trim())))
            }
        }

        val order = JSONObject(http.post("wc/v3/orders", payload).body)
        return CreatedOrder(
            id = order.optLong("id"),
            total = order.optString("total"),
            totalTax = order.optString("total_tax"),
            shippingTotal = order.optString("shipping_total"),
            discountTotal = order.optString("discount_total"),
            // Read-only stamps the plugin adds; both are snapshots taken at order time.
            organizationName = order.optString("woap_organization_name").decodeHtmlEntities(),
            locationName = order.optString("woap_location_name").decodeHtmlEntities(),
        )
    }

    /** One row of an order list: enough to show and sort without fetching every line item. */
    data class OrderSummary(
        val id: Long,
        val number: String,
        val status: String,
        val dateCreatedGmt: String,
        val total: String,
        val customerId: Long,
        val organizationName: String,
        val locationName: String,
        val itemCount: Int,
    )

    /** One product on an order, as WooCommerce billed it — not the till's current catalog copy. */
    data class OrderLineItem(
        val id: Long,
        val productId: Long,
        val name: String,
        val sku: String,
        val quantity: Int,
        val price: String,
        val total: String,
    )

    /**
     * One of an order's two addresses, as WooCommerce holds it.
     *
     * Kept as separate fields rather than a pre-joined block because the counter is usually reading
     * one line out loud — a phone number, a postcode — and because which lines are worth showing
     * differs between the two: billing carries the contact details, delivery rarely does.
     */
    data class OrderAddress(
        val firstName: String,
        val lastName: String,
        val company: String,
        val address1: String,
        val address2: String,
        val city: String,
        val state: String,
        val postcode: String,
        val country: String,
        val email: String,
        val phone: String,
    ) {
        val name: String
            get() = listOf(firstName, lastName).filter { it.isNotBlank() }.joinToString(" ")

        /**
         * True when the store sent this address empty, which is what a counter sale looks like —
         * nothing was delivered, so there is nothing to print.
         */
        val isEmpty: Boolean
            get() = listOf(
                firstName, lastName, company, address1, address2, city, state, postcode, country,
            ).all { it.isBlank() }

        /**
         * The street block, one line per line of an envelope.
         *
         * State and postcode join the town the way most of the world writes them; the country sits
         * on its own. Blank fields are dropped rather than left as empty rows, so a Swiss address
         * with no state doesn't print a gap where one would be.
         */
        val streetLines: List<String>
            get() = buildList {
                if (address1.isNotBlank()) add(address1)
                if (address2.isNotBlank()) add(address2)
                val town = listOf(postcode, city).filter { it.isNotBlank() }.joinToString(" ")
                val region = listOf(town, state).filter { it.isNotBlank() }.joinToString(", ")
                if (region.isNotBlank()) add(region)
                if (country.isNotBlank()) add(country)
            }

        companion object {
            val EMPTY = OrderAddress("", "", "", "", "", "", "", "", "", "", "")
        }
    }

    /** A full order: its totals, stamps and every line item on it. */
    data class OrderDetail(
        val id: Long,
        val number: String,
        val status: String,
        val dateCreatedGmt: String,
        val customerId: Long,
        val total: String,
        val totalTax: String,
        val shippingTotal: String,
        val discountTotal: String,
        val organizationName: String,
        val locationName: String,
        val lineItems: List<OrderLineItem>,
        val billing: OrderAddress = OrderAddress.EMPTY,
        val shipping: OrderAddress = OrderAddress.EMPTY,
        /** How the order is to be paid, in the store's own words — e.g. "Pay by invoice". */
        val paymentMethodTitle: String = "",
        /** Whatever the customer asked for in writing, when the order carries a note. */
        val customerNote: String = "",
    ) {
        /**
         * Whether the counter may still add or remove products.
         *
         * Once an order leaves `pending`/`processing` it has typically shipped, been paid out, or
         * been cancelled/refunded — states nothing here should be re-editing after the fact.
         */
        val isEditable: Boolean get() = status in EDITABLE_STATUSES

        private companion object {
            val EDITABLE_STATUSES = setOf("pending", "processing")
        }
    }

    /**
     * Orders newest first, optionally narrowed to one customer or one organization.
     *
     * `woap_organization` is the accounts plugin's own filter on WooCommerce's order route. It is
     * what an organization's history should be read through: asking each member separately and
     * merging is one request per member, and it silently loses any order placed by a member who has
     * since been removed from the account.
     *
     * With neither filter this is the whole store's recent order feed, which is what the Orders tab
     * shows.
     */
    suspend fun fetchOrders(
        customerId: Long? = null,
        organizationId: Long? = null,
        page: Int = 1,
        perPage: Int = 20,
    ): Page<OrderSummary> {
        val response = http.get(
            path = "wc/v3/orders",
            query = buildMap {
                customerId?.let { put("customer", it.toString()) }
                organizationId?.let { put("woap_organization", it.toString()) }
                put("page", page.toString())
                put("per_page", perPage.toString())
                put("orderby", "date")
                put("order", "desc")
            },
        )
        val array = JSONArray(response.body)
        val items = buildList(array.length()) {
            for (i in 0 until array.length()) add(array.getJSONObject(i).toOrderSummary())
        }
        return Page(items, response.totalPages)
    }

    /** One order, with every line item on it. */
    suspend fun fetchOrder(id: Long): OrderDetail =
        JSONObject(http.get("wc/v3/orders/$id").body).toOrderDetail()

    /**
     * Adds, changes or removes products on an order still open enough to edit
     * ([OrderDetail.isEditable]).
     *
     * [lineItems] is a diff, not the full order: an item with an [LineItem.id] and a positive
     * quantity updates that line, one with an id and `quantity = 0` removes it, and one with no id
     * adds a new line — see [LineItem]. Sending only what changed means a line nobody touched is
     * never re-priced by a round trip through this call.
     *
     * [LineItem.subtotal]/[LineItem.total] must be sent for quantity changes on an *existing* line:
     * WooCommerce only auto-prices a line item when it's created, so an update that sends quantity
     * alone changes the count but leaves the line — and the order total — at its old price.
     */
    suspend fun updateOrderLineItems(id: Long, lineItems: List<LineItem>): OrderDetail {
        val payload = JSONObject().put(
            "line_items",
            JSONArray().apply {
                lineItems.forEach { item ->
                    put(JSONObject().apply {
                        item.id?.let { put("id", it) }
                        put("product_id", item.productId)
                        put("quantity", item.quantity)
                        item.subtotal?.let { put("subtotal", it) }
                        item.total?.let { put("total", it) }
                    })
                }
            },
        )
        return JSONObject(http.patch("wc/v3/orders/$id", payload).body).toOrderDetail()
    }

    private fun JSONObject.toOrderSummary(): OrderSummary = OrderSummary(
        id = optLong("id"),
        number = optString("number"),
        status = optString("status"),
        dateCreatedGmt = optString("date_created_gmt"),
        total = optString("total"),
        customerId = optLong("customer_id"),
        organizationName = optString("woap_organization_name").decodeHtmlEntities(),
        locationName = optString("woap_location_name").decodeHtmlEntities(),
        itemCount = optJSONArray("line_items")?.length() ?: 0,
    )

    private fun JSONObject.toOrderDetail(): OrderDetail = OrderDetail(
        id = optLong("id"),
        number = optString("number"),
        status = optString("status"),
        dateCreatedGmt = optString("date_created_gmt"),
        customerId = optLong("customer_id"),
        total = optString("total"),
        totalTax = optString("total_tax"),
        shippingTotal = optString("shipping_total"),
        discountTotal = optString("discount_total"),
        organizationName = optString("woap_organization_name").decodeHtmlEntities(),
        locationName = optString("woap_location_name").decodeHtmlEntities(),
        lineItems = optJSONArray("line_items").toOrderLineItems(),
        billing = optJSONObject("billing").toOrderAddress(),
        shipping = optJSONObject("shipping").toOrderAddress(),
        paymentMethodTitle = optString("payment_method_title").decodeHtmlEntities(),
        customerNote = optString("customer_note").decodeHtmlEntities(),
    )

    /**
     * Reads one of the order's addresses. A missing block is an empty address, not an error: a
     * counter sale carries no delivery address at all, and WooCommerce simply omits it.
     */
    private fun JSONObject?.toOrderAddress(): OrderAddress {
        if (this == null) return OrderAddress.EMPTY
        return OrderAddress(
            firstName = optString("first_name").decodeHtmlEntities(),
            lastName = optString("last_name").decodeHtmlEntities(),
            company = optString("company").decodeHtmlEntities(),
            address1 = optString("address_1").decodeHtmlEntities(),
            address2 = optString("address_2").decodeHtmlEntities(),
            city = optString("city").decodeHtmlEntities(),
            state = optString("state").decodeHtmlEntities(),
            postcode = optString("postcode"),
            country = optString("country"),
            email = optString("email"),
            phone = optString("phone"),
        )
    }

    private fun JSONArray?.toOrderLineItems(): List<OrderLineItem> {
        if (this == null) return emptyList()
        return buildList(length()) {
            for (i in 0 until length()) {
                val item = getJSONObject(i)
                add(
                    OrderLineItem(
                        id = item.optLong("id"),
                        productId = item.optLong("product_id"),
                        name = item.optString("name").decodeHtmlEntities(),
                        sku = item.optString("sku"),
                        quantity = item.optInt("quantity"),
                        price = item.optString("price"),
                        total = item.optString("total"),
                    ),
                )
            }
        }
    }

    /**
     * Reads the store's checkout behaviour: enabled payment gateways, enabled shipping methods
     * across all zones, and tax settings. Each section degrades independently so a store without
     * (say) shipping zones still yields its gateways.
     */
    suspend fun fetchCheckoutConfig(): CheckoutConfig {
        val general = runCatching { JSONArray(body("wc/v3/settings/general")).settingsMap() }
            .getOrDefault(emptyMap())
        val taxesEnabled = general["woocommerce_calc_taxes"] == "yes"
        // "CH:BL" → "CH"; used to pick the tax rate that applies at the store's base.
        val baseCountry = general["woocommerce_default_country"].orEmpty().substringBefore(":")

        var pricesIncludeTax = false
        var ratePercent: Double? = null
        var taxLabel = "Tax"
        if (taxesEnabled) {
            runCatching {
                val tax = JSONArray(body("wc/v3/settings/tax")).settingsMap()
                pricesIncludeTax = tax["woocommerce_prices_include_tax"] == "yes"
            }
            runCatching {
                val rates = JSONArray(body("wc/v3/taxes", mapOf("per_page" to "100")))
                var chosen: JSONObject? = null
                for (i in 0 until rates.length()) {
                    val rate = rates.getJSONObject(i)
                    val taxClass = rate.optString("class")
                    if (taxClass.isNotBlank() && taxClass != "standard") continue
                    if (chosen == null) chosen = rate
                    if (rate.optString("country").equals(baseCountry, ignoreCase = true)) {
                        chosen = rate
                        break
                    }
                }
                chosen?.let {
                    ratePercent = it.optString("rate").toDoubleOrNull()
                    taxLabel = it.optString("name").ifBlank { "Tax" }
                }
            }
        }

        val gateways = runCatching {
            val array = JSONArray(body("wc/v3/payment_gateways"))
            buildList {
                for (i in 0 until array.length()) {
                    val gateway = array.getJSONObject(i)
                    if (!gateway.optBoolean("enabled")) continue
                    val id = gateway.optString("id")
                    if (id.isBlank()) continue
                    add(PaymentGateway(id, gateway.optString("title").decodeHtmlEntities().ifBlank { id }))
                }
            }
        }.getOrDefault(emptyList())

        val shippingOptions = runCatching {
            val zones = JSONArray(body("wc/v3/shipping/zones"))
            buildList {
                for (i in 0 until zones.length()) {
                    val zone = zones.getJSONObject(i)
                    val methods = JSONArray(body("wc/v3/shipping/zones/${zone.optLong("id")}/methods"))
                    for (j in 0 until methods.length()) {
                        val method = methods.getJSONObject(j)
                        if (!method.optBoolean("enabled")) continue
                        val methodSettings = method.optJSONObject("settings")
                        add(
                            ShippingOption(
                                zoneName = zone.optString("name"),
                                methodId = method.optString("method_id"),
                                title = methodSettings.settingValue("title")
                                    .ifBlank { method.optString("title") },
                                cost = methodSettings.settingValue("cost"),
                                taxable = methodSettings.settingValue("tax_status")
                                    .ifBlank { "taxable" } == "taxable",
                            )
                        )
                    }
                }
            }
        }.getOrDefault(emptyList())

        return CheckoutConfig(
            taxesEnabled = taxesEnabled,
            pricesIncludeTax = pricesIncludeTax,
            standardTaxRatePercent = ratePercent,
            taxLabel = taxLabel,
            gateways = gateways,
            shippingOptions = shippingOptions,
        )
    }

    private suspend fun body(path: String, query: Map<String, String> = emptyMap()): String =
        http.get(path, query).body

    /** `[{id, value}, …]` settings arrays → `id → value` map. */
    private fun JSONArray.settingsMap(): Map<String, String> = buildMap {
        for (i in 0 until length()) {
            optJSONObject(i)?.let { put(it.optString("id"), it.optString("value")) }
        }
    }

    /** Reads `settings.<key>.value` from a shipping method's settings object. */
    private fun JSONObject?.settingValue(key: String): String =
        this?.optJSONObject(key)?.optString("value").orEmpty()

    private fun JSONObject.toProduct(): Product {
        val firstImage = optJSONArray("images")
            ?.takeIf { it.length() > 0 }
            ?.getJSONObject(0)
            ?.optString("src")
            ?.takeIf { it.isNotBlank() }
            // WordPress writes media URLs against the store's internal address, which on a proxied
            // store no device can resolve. Routed here, once, so the DB holds a URL that loads.
            ?.throughSiteProxy(siteUrl)
        val categoryNames = optJSONArray("categories").namesList("name")
        return Product(
            id = optLong("id"),
            name = optString("name").decodeHtmlEntities(),
            brand = readBrand(),
            sku = optString("sku"),
            ean = readBarcodeNumber(),
            price = optString("price"),
            regularPrice = optString("regular_price"),
            salePrice = optString("sale_price"),
            msrp = readMsrp(),
            stockStatus = optString("stock_status", "instock"),
            stockQuantity = if (isNull("stock_quantity")) null else optInt("stock_quantity"),
            imageUrl = firstImage,
            categories = categoryNames.joinToString(", "),
            description = optString("short_description").ifBlank { optString("description") }.stripHtml(),
            minOrderQuantity = readQuantityRule("min_order_quantity"),
            orderQuantityStep = readQuantityRule("order_quantity_step"),
            // The whole catalog is synced whatever its status — the route defaults to `status=any`
            // and is deliberately left that way, so a scan of a product that exists but isn't live
            // can say so instead of reporting no such product. What the status gates is search and
            // ordering, and that gate lives in the queries, not in what gets fetched.
            status = optString("status").ifBlank { Product.STATUS_PUBLISHED },
        )
    }

    /**
     * A pack-size rule (`min_order_quantity` / `order_quantity_step`), or 1 when the store has none.
     *
     * Like `msrp`, these come from a plugin rather than WooCommerce core, so the key may be missing,
     * null, empty, or left at a zero the store never cleaned up — and every one of those means the
     * product has no rule. One is the honest answer for all of them: a product with no minimum is
     * sold one at a time, which is exactly what a minimum of one says.
     */
    private fun JSONObject.readQuantityRule(key: String): Int {
        val value = opt(key) ?: return 1
        if (value == JSONObject.NULL) return 1
        // Stores send these as numbers or as numeric strings; a decimal is floored to whole units.
        return value.toString().trim().toDoubleOrNull()?.toInt()?.coerceAtLeast(1) ?: 1
    }

    /**
     * The product's manufacturer's suggested retail price, or blank when it has none.
     *
     * `msrp` is added by a plugin rather than WooCommerce core, so the key may be missing entirely
     * and its value may be null — both mean the same thing here, and both print nothing. A store
     * that has no MSRP for a particular product commonly leaves a zero behind rather than removing
     * the key, so a zero is read as "none" too: "MSRP 0.00" on a shelf label is worse than no line
     * at all. The value is kept as the store's own string so it formats like every other price.
     */
    private fun JSONObject.readMsrp(): String {
        val value = opt("msrp") ?: return ""
        if (value == JSONObject.NULL) return ""
        val text = value.toString().trim()
        if (text.isBlank() || text.toDoubleOrNull() == 0.0) return ""
        return text
    }

    /**
     * The product's brand, or blank when the store files it under none.
     *
     * Brands are a taxonomy, so the product carries a `brands` array — normally one entry, and when
     * there are several the first is the one a label has room for. Stores that keep the brand as a
     * product *attribute* rather than the taxonomy are read next: the attribute is named for what
     * it holds, so a "Brand" or "Marke" attribute is the same fact under a different key, and
     * reading it beats printing no brand at all.
     */
    private fun JSONObject.readBrand(): String {
        optJSONArray("brands").namesList("name").firstOrNull()
            ?.let { return it.decodeHtmlEntities() }
        val attributes = optJSONArray("attributes") ?: return ""
        for (i in 0 until attributes.length()) {
            val attribute = attributes.optJSONObject(i) ?: continue
            if (attribute.optString("name").trim().lowercase() !in BRAND_ATTRIBUTE_NAMES) continue
            val options = attribute.optJSONArray("options") ?: continue
            for (j in 0 until options.length()) {
                val option = options.optString(j).trim()
                if (option.isNotBlank()) return option.decodeHtmlEntities()
            }
        }
        return ""
    }

    /**
     * The product's barcode number.
     *
     * WooCommerce 9.2 added `global_unique_id` — one field holding whichever of GTIN, UPC, EAN or
     * ISBN the product carries — and that is preferred. Stores still on a barcode plugin keep the
     * number in product meta instead, so the keys those plugins write are checked next, in the
     * order a store is likeliest to have them.
     */
    private fun JSONObject.readBarcodeNumber(): String {
        optString("global_unique_id").trim().takeIf { it.isNotBlank() }?.let { return it }
        val meta = optJSONArray("meta_data") ?: return ""
        val values = buildMap {
            for (i in 0 until meta.length()) {
                val entry = meta.optJSONObject(i) ?: continue
                val key = entry.optString("key")
                if (key.isBlank() || entry.isNull("value")) continue
                // Meta values are free-form; only a scalar can be a barcode.
                val value = entry.opt("value") ?: continue
                if (value is JSONObject || value is JSONArray) continue
                putIfAbsent(key, value.toString().trim())
            }
        }
        return BARCODE_META_KEYS.firstNotNullOfOrNull { key ->
            values[key]?.takeIf { it.isNotBlank() }
        }.orEmpty()
    }

    /**
     * Names pulled out of an array of objects, decoded.
     *
     * WordPress serves taxonomy names HTML-encoded, so a category the shop calls "Basteln & Co."
     * arrives as `Basteln &amp; Co.` and was being printed that way on the product screen.
     */
    private fun JSONArray?.namesList(key: String): List<String> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.optString(key)?.takeIf { it.isNotBlank() }
                    ?.let { add(it.decodeHtmlEntities()) }
            }
        }
    }

    private companion object {
        /** Attribute names a store uses for the brand when it doesn't use the brands taxonomy. */
        val BRAND_ATTRIBUTE_NAMES = setOf("brand", "brands", "marke", "hersteller", "manufacturer")

        /** Product meta keys the common WooCommerce barcode/GTIN plugins store the number under. */
        val BARCODE_META_KEYS = listOf(
            "_wpm_gtin_code",
            "_ean",
            "ean",
            "_barcode",
            "barcode",
            "_gtin",
            "gtin",
            "_upc",
            "upc",
        )
    }
}
