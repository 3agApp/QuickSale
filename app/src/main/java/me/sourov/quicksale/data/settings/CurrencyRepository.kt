package me.sourov.quicksale.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode

/** Where the currency symbol sits relative to the amount, named as WooCommerce names it. */
enum class CurrencyPosition(val slug: String) {
    LEFT("left"),
    RIGHT("right"),
    LEFT_SPACE("left_space"),
    RIGHT_SPACE("right_space");

    companion object {
        fun fromSlug(slug: String?): CurrencyPosition =
            entries.firstOrNull { it.slug == slug } ?: LEFT
    }
}

/**
 * The store's display currency *and* the way it writes prices, fetched from WooCommerce on sync.
 *
 * Both halves matter: a till that renders "€ 1234.5" next to a website showing "1.234,50 €" reads
 * as a different shop to the person holding the label. Everything here comes from the store's own
 * settings, so the app never decides how a price looks.
 */
data class StoreCurrency(
    val code: String = "",
    val symbol: String = DEFAULT_SYMBOL,
    val position: CurrencyPosition = CurrencyPosition.LEFT,
    /** May be empty — plenty of stores group nothing. */
    val thousandSeparator: String = ",",
    val decimalSeparator: String = ".",
    val decimals: Int = 2,
) {

    /**
     * Formats a price string as the store's website would, or null when it carries no price at all.
     *
     * Prices arrive from WooCommerce as plain decimal strings ("1234.5"), never pre-formatted, so
     * this is where they get their separators. A non-empty value that isn't a number is passed
     * through with the symbol rather than dropped: whatever the store put there, showing it beats
     * showing nothing.
     */
    fun format(amount: String): String? {
        val trimmed = amount.trim()
        if (trimmed.isEmpty()) return null
        val number = trimmed.toBigDecimalOrNull() ?: return withSymbol(trimmed)
        return format(number)
    }

    fun format(amount: BigDecimal): String {
        val rounded = amount.setScale(decimals.coerceIn(0, MAX_DECIMALS), RoundingMode.HALF_UP)
        val plain = rounded.abs().toPlainString()
        val whole = group(plain.substringBefore('.'))
        val fraction = plain.substringAfter('.', missingDelimiterValue = "")
        val digits = if (fraction.isEmpty()) whole else "$whole$decimalSeparator$fraction"
        // WooCommerce writes the sign ahead of the whole thing, symbol included ("-$5.00").
        val sign = if (rounded.signum() < 0) "-" else ""
        return sign + withSymbol(digits)
    }

    private fun withSymbol(digits: String): String = when (position) {
        CurrencyPosition.LEFT -> "$symbol$digits"
        CurrencyPosition.RIGHT -> "$digits$symbol"
        CurrencyPosition.LEFT_SPACE -> "$symbol$NBSP$digits"
        CurrencyPosition.RIGHT_SPACE -> "$digits$NBSP$symbol"
    }

    /** Splits the integer part into groups of three, left-padded group first ("1|234|567"). */
    private fun group(whole: String): String {
        if (thousandSeparator.isEmpty() || whole.length <= GROUP_SIZE) return whole
        val head = whole.length % GROUP_SIZE
        val groups = buildList {
            if (head > 0) add(whole.substring(0, head))
            for (start in head until whole.length step GROUP_SIZE) {
                add(whole.substring(start, start + GROUP_SIZE))
            }
        }
        return groups.joinToString(thousandSeparator)
    }

    companion object {
        const val DEFAULT_SYMBOL = "$"

        /** WooCommerce's own ceiling on `woocommerce_price_num_decimals`, and plenty. */
        private const val MAX_DECIMALS = 6
        private const val GROUP_SIZE = 3

        /** WooCommerce separates a spaced symbol with `&nbsp;`, so the price never wraps in two. */
        private const val NBSP = "\u00A0"
    }
}

/**
 * Persists the WooCommerce store currency so prices render like the website even offline.
 * Refreshed on each sync; mirrored into the UI via [me.sourov.quicksale.ui.CurrencyFormatter].
 */
class CurrencyRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val CODE = stringPreferencesKey("currency_code")
        val SYMBOL = stringPreferencesKey("currency_symbol")
        val POSITION = stringPreferencesKey("currency_position")
        val THOUSAND_SEPARATOR = stringPreferencesKey("currency_thousand_separator")
        val DECIMAL_SEPARATOR = stringPreferencesKey("currency_decimal_separator")
        val DECIMALS = intPreferencesKey("currency_decimals")
    }

    val currency: Flow<StoreCurrency> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val defaults = StoreCurrency()
            StoreCurrency(
                code = prefs[Keys.CODE].orEmpty(),
                symbol = prefs[Keys.SYMBOL]?.takeIf { it.isNotBlank() } ?: defaults.symbol,
                position = prefs[Keys.POSITION]?.let(CurrencyPosition::fromSlug) ?: defaults.position,
                // An empty thousand separator is a real setting, so it is stored and read as one —
                // only a key that was never written falls back to the default.
                thousandSeparator = prefs[Keys.THOUSAND_SEPARATOR] ?: defaults.thousandSeparator,
                decimalSeparator = prefs[Keys.DECIMAL_SEPARATOR]?.takeIf { it.isNotEmpty() }
                    ?: defaults.decimalSeparator,
                decimals = prefs[Keys.DECIMALS] ?: defaults.decimals,
            )
        }

    suspend fun setCurrency(currency: StoreCurrency) {
        dataStore.edit { prefs ->
            prefs[Keys.CODE] = currency.code
            prefs[Keys.SYMBOL] = currency.symbol
            prefs[Keys.POSITION] = currency.position.slug
            prefs[Keys.THOUSAND_SEPARATOR] = currency.thousandSeparator
            prefs[Keys.DECIMAL_SEPARATOR] = currency.decimalSeparator
            prefs[Keys.DECIMALS] = currency.decimals
        }
    }
}
