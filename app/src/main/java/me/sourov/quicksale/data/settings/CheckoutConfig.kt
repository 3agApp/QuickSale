package me.sourov.quicksale.data.settings

import java.math.BigDecimal
import java.math.RoundingMode

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

/**
 * The tax fraction shipping carries on this store, or null when the entered charge already *is*
 * what goes on the order.
 *
 * Three things all have to be true for a conversion to be owed: the store charges tax at all, it
 * enters prices inclusive of it, and this particular method is taxable. Otherwise the operator's
 * number and WooCommerce's number are the same number.
 */
private fun CheckoutConfig.shippingTaxFraction(taxable: Boolean): BigDecimal? {
    if (!taxesEnabled || !pricesIncludeTax || !taxable) return null
    val percent = standardTaxRatePercent ?: return null
    return BigDecimal(percent.toString()).movePointLeft(2)
}

/**
 * The net charge to post for a [gross] amount the operator quoted the customer.
 *
 * WooCommerce treats `shipping_lines.total` as net and adds tax on top, so on a tax-inclusive store
 * the gross figure has to come down first — otherwise the customer is charged more through the till
 * than the same delivery costs on the website.
 */
fun CheckoutConfig.shippingNet(gross: BigDecimal, taxable: Boolean): BigDecimal {
    val fraction = shippingTaxFraction(taxable) ?: return gross.setScale(2, RoundingMode.HALF_UP)
    return gross.divide(BigDecimal.ONE + fraction, 2, RoundingMode.HALF_UP)
}

/**
 * The gross amount a [net] charge already on an order corresponds to — [shippingNet] backwards.
 *
 * Editing a placed order needs this: the order holds net, the operator reads and types gross, and
 * showing them the stored figure unconverted would quote a delivery cheaper than it is.
 */
fun CheckoutConfig.shippingGross(net: BigDecimal, taxable: Boolean): BigDecimal {
    val fraction = shippingTaxFraction(taxable) ?: return net.setScale(2, RoundingMode.HALF_UP)
    return (net * (BigDecimal.ONE + fraction)).setScale(2, RoundingMode.HALF_UP)
}
