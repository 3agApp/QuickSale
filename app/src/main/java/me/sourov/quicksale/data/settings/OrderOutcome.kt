package me.sourov.quicksale.data.settings

/**
 * The state every order this app places is created in: on hold, and never marked paid.
 *
 * The status used to follow the payment method, mirroring what each core gateway does at web
 * checkout — bank transfer and cheque on hold, cash on delivery straight to processing, everything
 * else paid outright. That made the till the thing that decided the shop had been paid, which is
 * not what a terminal at a fair is for: whoever is holding it knows what was handed over, not
 * whether it cleared.
 *
 * So the app records the payment method on the order and stops there. Someone at the shop confirms
 * the money and moves the order on — and that same move is what releases it to the ERP, since
 * woo-kontor-sync-pro only pushes `processing` and `completed`. Nothing reaches Kontor on the
 * strength of a tap at a stand.
 */
object OrderOutcome {

    /** The status every order is created in, whatever was used to pay for it. */
    const val STATUS = "on-hold"

    /**
     * Never true. WooCommerce's `set_paid` runs `payment_complete()`, which stamps a payment date
     * and moves the order to processing or completed — the two things this rule exists to prevent.
     */
    const val SET_PAID = false

    /** What the counter is told will happen, shown beside the payment method at checkout. */
    const val SUMMARY = "On hold for the shop to confirm, whatever the payment method"
}
