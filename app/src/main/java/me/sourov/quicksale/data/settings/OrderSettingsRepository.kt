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

/** Reads and writes the default [OrderStatus], stored in the shared settings DataStore. */
class OrderSettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val STATUS = stringPreferencesKey("default_order_status")
    }

    val status: Flow<OrderStatus> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> OrderStatus.fromSlug(prefs[Keys.STATUS]) }

    suspend fun update(status: OrderStatus) {
        dataStore.edit { prefs -> prefs[Keys.STATUS] = status.slug }
    }
}
