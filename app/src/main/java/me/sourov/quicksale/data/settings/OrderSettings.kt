package me.sourov.quicksale.data.settings

/**
 * The WooCommerce order status applied to orders placed from QuickSale, configurable in Settings.
 *
 * [setPaid] drives the `set_paid` flag in the create-order request: paid statuses record a payment
 * date, while pending leaves the order awaiting payment.
 */
enum class OrderStatus(val slug: String, val label: String, val setPaid: Boolean) {
    PROCESSING("processing", "Processing (paid)", true),
    PENDING("pending", "Pending payment", false),
    COMPLETED("completed", "Completed", true);

    companion object {
        /** Resolves a stored slug back to an [OrderStatus], defaulting to [PROCESSING]. */
        fun fromSlug(slug: String?): OrderStatus =
            entries.firstOrNull { it.slug == slug } ?: PROCESSING
    }
}
