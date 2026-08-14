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
 * What this device is set to, in the three states a launch actually has.
 *
 * Two of them used to be the same null, and the cost of that was a device set up weeks ago opening
 * on "What is this device for?" — because the honest answer for the first frames is not "nobody has
 * answered", it is "we haven't looked yet", and only one of those is a question worth asking.
 */
sealed interface DeviceModeState {
    /** The stored value hasn't been read back yet. Nothing may be drawn on this. */
    data object Loading : DeviceModeState

    /** Read, and never answered — a genuine first run. */
    data object Unset : DeviceModeState

    data class Chosen(val mode: DeviceMode) : DeviceModeState
}

/**
 * Reads and writes this device's [DeviceMode], stored in the shared settings DataStore.
 *
 * Prefer [state] wherever a screen is deciding what to *show*: it distinguishes a device that has
 * never been set up from one whose setting is still coming off disk. [mode] stays for the places
 * that only want the value and have somewhere sensible to sit while it arrives.
 */
class DeviceModeRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val MODE = stringPreferencesKey("device_mode")
    }

    val mode: Flow<DeviceMode?> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> DeviceMode.fromName(prefs[Keys.MODE]) }

    /**
     * The same setting, but able to say "not read yet".
     *
     * Never emits [DeviceModeState.Loading] — that is the value a collector holds *before* the
     * first emission, so the first thing this sends is already an answer.
     */
    val state: Flow<DeviceModeState> = mode
        .map { chosen -> chosen?.let(DeviceModeState::Chosen) ?: DeviceModeState.Unset }

    suspend fun update(mode: DeviceMode) {
        dataStore.edit { prefs -> prefs[Keys.MODE] = mode.name }
    }
}
