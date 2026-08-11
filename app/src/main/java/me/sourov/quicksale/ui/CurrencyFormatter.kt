package me.sourov.quicksale.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.sourov.quicksale.data.settings.StoreCurrency
import java.math.BigDecimal

/**
 * In-memory mirror of the store's currency so the plain [me.sourov.quicksale.ui.products.asPrice]
 * formatter (used in both composable and non-composable code, including the label renderer) can
 * read it synchronously.
 *
 * Backed by Compose snapshot state, so price text recomposes when the store's format changes. The
 * source of truth is persisted in [me.sourov.quicksale.data.settings.CurrencyRepository];
 * [QuickSaleApp] keeps this mirror current by collecting that flow.
 */
object CurrencyFormatter {

    var currency: StoreCurrency by mutableStateOf(StoreCurrency())
        private set

    fun update(currency: StoreCurrency) {
        this.currency = currency
    }

    /** The store's own rendering of [amount], or "—" when there is no price to show. */
    fun format(amount: String): String = currency.format(amount) ?: NO_PRICE

    fun format(amount: BigDecimal): String = currency.format(amount)

    private const val NO_PRICE = "—"
}
