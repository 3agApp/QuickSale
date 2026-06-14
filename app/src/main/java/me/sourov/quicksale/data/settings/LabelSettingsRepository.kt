package me.sourov.quicksale.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Reads and writes [LabelSettings], stored in the shared settings DataStore. */
class LabelSettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SHOW_NAME = booleanPreferencesKey("label_show_name")
        val SHOW_BARCODE = booleanPreferencesKey("label_show_barcode")
        val SHOW_SKU = booleanPreferencesKey("label_show_sku")
        val SHOW_PRICE = booleanPreferencesKey("label_show_price")
        val COPIES = intPreferencesKey("label_copies")
        val SPACING = intPreferencesKey("label_spacing")
    }

    val settings: Flow<LabelSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val defaults = LabelSettings()
            LabelSettings(
                showName = prefs[Keys.SHOW_NAME] ?: defaults.showName,
                showBarcode = prefs[Keys.SHOW_BARCODE] ?: defaults.showBarcode,
                showSku = prefs[Keys.SHOW_SKU] ?: defaults.showSku,
                showPrice = prefs[Keys.SHOW_PRICE] ?: defaults.showPrice,
                copies = prefs[Keys.COPIES] ?: defaults.copies,
                spacing = prefs[Keys.SPACING] ?: defaults.spacing,
            )
        }

    suspend fun setShowName(value: Boolean) = edit(Keys.SHOW_NAME, value)
    suspend fun setShowBarcode(value: Boolean) = edit(Keys.SHOW_BARCODE, value)
    suspend fun setShowSku(value: Boolean) = edit(Keys.SHOW_SKU, value)
    suspend fun setShowPrice(value: Boolean) = edit(Keys.SHOW_PRICE, value)

    suspend fun setCopies(value: Int) =
        edit(Keys.COPIES, value.coerceIn(LabelSettings.MIN_COPIES, LabelSettings.MAX_COPIES))

    suspend fun setSpacing(value: Int) =
        edit(Keys.SPACING, value.coerceIn(LabelSettings.MIN_SPACING, LabelSettings.MAX_SPACING))

    private suspend fun <T> edit(key: Preferences.Key<T>, value: T) {
        dataStore.edit { it[key] = value }
    }
}
