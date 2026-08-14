package me.sourov.quicksale.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/**
 * Whether this till may sell more of a product than the store has, stored in the shared settings
 * DataStore.
 *
 * Default is **allowed**, because that is what a distributor's counter is for: at a fair the
 * customer is ordering for delivery, and a shelf count that ran out this morning is not a reason to
 * refuse the sale. Turning it off is for the stand that ships from what is physically on the table.
 *
 * Allowed does not mean silent — the counter is still told, on the line and at the scan. What this
 * setting changes is whether being told is the end of it.
 */
class BackorderRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val ALLOWED = booleanPreferencesKey("allow_backorders")
    }

    val allowed: Flow<Boolean> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[Keys.ALLOWED] ?: DEFAULT_ALLOWED }

    suspend fun setAllowed(value: Boolean) {
        dataStore.edit { it[Keys.ALLOWED] = value }
    }

    companion object {
        const val DEFAULT_ALLOWED = true
    }
}
