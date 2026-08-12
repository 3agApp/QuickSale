package me.sourov.quicksale.ui.orders

import me.sourov.quicksale.data.remote.WooApiException

/**
 * A refusal from the store, turned into something a person at the counter can act on.
 *
 * [headline] is ours and stable; [detail] is the store's own message, which carries the specific
 * reason ("… is still awaiting approval …", the fields a country rejected) and is translated into
 * the site's language. Both are shown — the headline says what kind of problem it is, the detail
 * says what to do about it.
 *
 * Branching happens on the error `code`, never the message: messages are localised and follow the
 * shop's organization vocabulary, so the same refusal reads "Branch" on one shop and "Campus" on
 * another.
 */
data class OrderError(
    val headline: String,
    val detail: String,
    /** True when re-checking the snapshot might change the answer. */
    val suggestsSync: Boolean = false,
) {
    val combined: String get() = if (detail.isBlank()) headline else "$headline — $detail"

    companion object {
        fun from(error: Throwable): OrderError = when (error) {
            is WooApiException -> fromApi(error)
            else -> OrderError(
                headline = "Couldn't place the order",
                detail = error.message.orEmpty(),
            )
        }

        private fun fromApi(error: WooApiException): OrderError = when (error.code) {
            "woap_rest_cannot_purchase" -> OrderError(
                headline = "This member can't place orders right now",
                detail = error.message,
                // The store just re-checked and disagreed with the local snapshot.
                suggestsSync = true,
            )

            "woap_rest_shipping_destination" -> OrderError(
                headline = "The store wouldn't deliver there",
                detail = error.message,
                suggestsSync = true,
            )

            "woap_rest_shipping_address" -> OrderError(
                headline = "The delivery address needs fixing",
                detail = error.message,
            )

            "woocommerce_rest_invalid_customer_id" -> OrderError(
                headline = "The store doesn't recognise this member",
                detail = error.message,
                suggestsSync = true,
            )

            else -> OrderError(
                headline = "The store refused the order",
                detail = error.message,
            )
        }
    }
}
