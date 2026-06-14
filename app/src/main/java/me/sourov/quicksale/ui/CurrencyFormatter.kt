package me.sourov.quicksale.ui

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import me.sourov.quicksale.data.settings.StoreCurrency

/**
 * In-memory mirror of the store's currency symbol so the plain [String.asPrice] formatter
 * (used in both composable and non-composable code) can read it synchronously.
 *
 * Backed by Compose snapshot state, so price text recomposes when the symbol changes. The
 * source of truth is persisted in [me.sourov.quicksale.data.settings.CurrencyRepository];
 * [QuickSaleApp] keeps this mirror current by collecting that flow.
 */
object CurrencyFormatter {

    var symbol: String by mutableStateOf(StoreCurrency.DEFAULT_SYMBOL)
        private set

    fun update(symbol: String) {
        this.symbol = symbol.trim().ifBlank { StoreCurrency.DEFAULT_SYMBOL }
    }
}
