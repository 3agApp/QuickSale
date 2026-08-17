package me.sourov.quicksale.data.settings

/**
 * What holds for every order this app places, whatever else is configured.
 *
 * The status is a setting — see [NewOrderStatus]. Being *paid* is not, and the difference is the
 * point: which queue an order joins is the shop's own workflow, while marking money received is the
 * till deciding the shop's books. Whoever is holding the terminal knows what was handed over, not
 * whether it cleared, so the app records the payment method on the order and stops there.
 */
object OrderOutcome {

    /**
     * Never true. WooCommerce's `set_paid` runs `payment_complete()`, which stamps a payment date
     * and moves the order to processing or completed regardless of the status sent with it — so it
     * would both overrule the setting and put a payment on the books from a tap at a stand.
     */
    const val SET_PAID = false
}
