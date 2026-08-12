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
 * Reads and writes this device's [DeviceMode], stored in the shared settings DataStore.
 *
 * Emits null until the operator has chosen one, which is what puts the first-run picker on screen.
 * A null is "not asked yet", not "no mode" — nothing else in the app treats it as a value.
 */
class DeviceModeRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val MODE = stringPreferencesKey("device_mode")
    }

    val mode: Flow<DeviceMode?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> DeviceMode.fromName(prefs[Keys.MODE]) }

    suspend fun update(mode: DeviceMode) {
        dataStore.edit { prefs -> prefs[Keys.MODE] = mode.name }
    }
}
