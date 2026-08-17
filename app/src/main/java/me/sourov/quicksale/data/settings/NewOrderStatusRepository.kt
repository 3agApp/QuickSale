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

/**
 * Reads and writes the [NewOrderStatus] this till places orders in, stored in the shared settings
 * DataStore.
 *
 * The key is deliberately not the `default_order_status` an older version of this setting wrote:
 * that one could hold `pending` or `completed`, statuses no longer offered, and a device upgrading
 * across both changes should land on today's default rather than on a choice made against a menu
 * that no longer exists.
 */
class NewOrderStatusRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val STATUS = stringPreferencesKey("new_order_status")
    }

    val status: Flow<NewOrderStatus> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> NewOrderStatus.fromSlug(prefs[Keys.STATUS]) }

    suspend fun setStatus(status: NewOrderStatus) {
        dataStore.edit { prefs -> prefs[Keys.STATUS] = status.slug }
    }
}
