package me.sourov.quicksale.data.settings

/**
 * The status every order this app places is created in, chosen once in Settings.
 *
 * The status used to follow the payment method, mirroring what each core gateway does at web
 * checkout, and then it was pinned to on-hold for everyone. Neither survives contact with two shops
 * that work differently: one rings up a sale that is finished the moment the customer walks away,
 * the other invoices wholesale accounts and wants a person at the shop to confirm the money first.
 * That is a property of the shop, not of the order, so it is a setting rather than a rule.
 *
 * What is *not* offered is anything that marks the order paid — see [OrderOutcome.SET_PAID].
 * Whoever is holding the terminal knows what was handed over, not whether it cleared.
 */
enum class NewOrderStatus(
    /** The WooCommerce order status slug sent with the create-order request. */
    val slug: String,
    val title: String,
    val subtitle: String,
    /** What the counter is told will happen, shown beside the payment method at checkout. */
    val checkoutSummary: String,
) {
    /**
     * The default: the order is live work for the shop the moment it is rung up.
     *
     * Also the status woo-kontor-sync-pro pushes on, so an order placed at a stand reaches Kontor
     * at the next run without anyone touching it in admin.
     */
    PROCESSING(
        slug = "processing",
        title = "Processing",
        subtitle = "The order is live for the shop to fulfil as soon as it is placed, and a store " +
            "with Kontor sync pushes it across on the next run.",
        checkoutSummary = "Placed as processing, whatever the payment method",
    ),

    /**
     * For the stand whose orders someone at the shop checks before they count. Nothing reaches
     * Kontor on the strength of a tap at a stand: the sync only picks up `processing` and
     * `completed`, so the order sits until a person moves it on.
     */
    ON_HOLD(
        slug = "on-hold",
        title = "On hold",
        subtitle = "The order waits for someone at the shop to confirm it. Kontor sync leaves it " +
            "alone until they do.",
        checkoutSummary = "On hold for the shop to confirm, whatever the payment method",
    );

    companion object {
        /**
         * What a device gets until someone chooses otherwise, including every install that
         * predates this setting.
         */
        val DEFAULT = PROCESSING

        /** Resolves a stored slug back to a status, falling back to [DEFAULT]. */
        fun fromSlug(value: String?): NewOrderStatus =
            entries.firstOrNull { it.slug == value } ?: DEFAULT
    }
}
