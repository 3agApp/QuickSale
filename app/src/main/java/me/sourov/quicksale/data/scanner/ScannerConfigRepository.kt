package me.sourov.quicksale.data.scanner

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Reads and writes the [ScannerConfig], stored in the shared settings DataStore. */
class ScannerConfigRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val MODE = stringPreferencesKey("scanner_mode")
        val PRESET = stringPreferencesKey("scanner_preset")
        val ACTION = stringPreferencesKey("scanner_action")
        val EXTRA = stringPreferencesKey("scanner_extra_key")
    }

    val config: Flow<ScannerConfig> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            val mode = runCatching { ScannerMode.valueOf(prefs[Keys.MODE] ?: "") }
                .getOrDefault(ScannerMode.KEYBOARD)
            ScannerConfig(
                mode = mode,
                presetId = prefs[Keys.PRESET] ?: ScannerPreset.AUTO_DETECT.id,
                action = prefs[Keys.ACTION].orEmpty(),
                extraKey = prefs[Keys.EXTRA].orEmpty(),
            )
        }

    suspend fun update(config: ScannerConfig) {
        dataStore.edit { prefs ->
            prefs[Keys.MODE] = config.mode.name
            prefs[Keys.PRESET] = config.presetId
            prefs[Keys.ACTION] = config.action.trim()
            prefs[Keys.EXTRA] = config.extraKey.trim()
        }
    }
}
