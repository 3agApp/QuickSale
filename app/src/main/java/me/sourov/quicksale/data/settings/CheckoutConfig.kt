package me.sourov.quicksale.data.settings

/** A payment gateway the connected store has enabled at checkout. */
data class PaymentGateway(val id: String, val title: String)

/** An enabled shipping method from one of the store's shipping zones. */
data class ShippingOption(
    val zoneName: String,
    val methodId: String,
    val title: String,
    /** Cost as configured on the store; blank means free (e.g. local pickup). */
    val cost: String,
    /** Whether the store applies tax to this method's cost. */
    val taxable: Boolean,
) {
    val label: String
        get() = if (zoneName.isBlank()) title else "$title — $zoneName"
}

/**
 * Store-wide checkout behaviour, fetched from the connected WooCommerce store on each product
 * sync. Nothing here is specific to one shop: gateways, shipping methods and tax rules always
 * reflect whatever store the app is currently connected to.
 */
data class CheckoutConfig(
    val taxesEnabled: Boolean = false,
    /** True when the store enters product prices inclusive of tax. */
    val pricesIncludeTax: Boolean = false,
    /** Standard-class tax rate (percent) for the store's base country, when configured. */
    val standardTaxRatePercent: Double? = null,
    /** Display name of that tax rate (e.g. "MwSt.", "VAT"). */
    val taxLabel: String = "Tax",
    val gateways: List<PaymentGateway> = emptyList(),
    val shippingOptions: List<ShippingOption> = emptyList(),
)
