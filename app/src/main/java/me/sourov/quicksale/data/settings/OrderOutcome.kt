package me.sourov.quicksale.data.settings

/**
 * What state an order is created in, decided by the payment method chosen at the till rather than
 * by a setting in this app.
 *
 * QuickSale used to apply one status to every order it placed, picked once in Settings. That was a
 * standing guess: the same shop takes card at the stand and invoices its wholesale accounts, and
 * whichever status was chosen was wrong for one of them — an invoice sale marked paid, or a card
 * sale left waiting for money already in the till.
 *
 * The gateway is the thing that actually knows, so the app follows it. WooCommerce's REST API never
 * runs a gateway's `process_payment()` — that only happens at web checkout — so the rules the core
 * gateways apply there are mirrored here, matching what the website would have done with the same
 * payment method:
 *
 * - bank transfer and cheque leave the order [AWAITING_TRANSFER] (`on-hold`), unpaid, until the
 *   money arrives;
 * - cash on delivery goes straight to [PAY_ON_DELIVERY] (`processing`), also unpaid, because the
 *   goods move now and the money later;
 * - every other gateway takes payment at the point of sale, so the order is sent as [PAID].
 *
 * [PAID] deliberately names no status. `set_paid` makes WooCommerce run `payment_complete()`
 * itself, which picks `processing` or `completed` from the store's own rules — the
 * `woocommerce_payment_complete_order_status` filter, and whether the order's products need any
 * processing at all. That choice belongs to the store, and this is how it gets to make it.
 */
enum class OrderOutcome(
    /** The status slug to request, or null to send none and let the store decide. */
    val status: String?,
    /** Drives `set_paid`: true records a payment date and runs the store's paid-status rules. */
    val setPaid: Boolean,
    /** What the counter is told will happen, shown beside the payment method at checkout. */
    val summary: String,
) {
    AWAITING_TRANSFER("on-hold", false, "On hold until the payment arrives"),
    PAY_ON_DELIVERY("processing", false, "Processing, to be paid on delivery"),
    PAID(null, true, "Paid — the store marks it processing or completed");

    companion object {
        /**
         * The outcome for [gateway], by the same rules WooCommerce's own gateways use.
         *
         * A gateway this doesn't recognise is treated as taking payment now, which is what every
         * card, wallet and counter-cash gateway does; a store with no gateways configured at all
         * lands here too, since nothing is going to collect the money later.
         */
        fun forGateway(gateway: PaymentGateway?): OrderOutcome =
            when (gateway?.id?.lowercase()) {
                "bacs", "cheque" -> AWAITING_TRANSFER
                "cod" -> PAY_ON_DELIVERY
                else -> PAID
            }
    }
}
