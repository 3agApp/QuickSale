package me.sourov.quicksale.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** The store's display currency, fetched from WooCommerce on sync. */
data class StoreCurrency(val code: String = "", val symbol: String = DEFAULT_SYMBOL) {
    companion object {
        const val DEFAULT_SYMBOL = "$"
    }
}

/**
 * Persists the WooCommerce store currency so prices render with the right symbol even offline.
 * Refreshed on each sync; the symbol is mirrored into the UI via [me.sourov.quicksale.ui.CurrencyFormatter].
 */
class CurrencyRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val CODE = stringPreferencesKey("currency_code")
        val SYMBOL = stringPreferencesKey("currency_symbol")
    }

    val currency: Flow<StoreCurrency> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            StoreCurrency(
                code = prefs[Keys.CODE].orEmpty(),
                symbol = prefs[Keys.SYMBOL]?.takeIf { it.isNotBlank() } ?: StoreCurrency.DEFAULT_SYMBOL,
            )
        }

    suspend fun setCurrency(currency: StoreCurrency) {
        dataStore.edit { prefs ->
            prefs[Keys.CODE] = currency.code
            prefs[Keys.SYMBOL] = currency.symbol
        }
    }
}
