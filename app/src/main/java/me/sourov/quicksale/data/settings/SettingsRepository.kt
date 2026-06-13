package me.sourov.quicksale.data.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** App-wide DataStore instance for storing connection settings. */
val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "store_settings")

/**
 * Reads and writes the [StoreSettings] backing the WooCommerce connection.
 */
class SettingsRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SITE_URL = stringPreferencesKey("site_url")
        val CONSUMER_KEY = stringPreferencesKey("consumer_key")
        val CONSUMER_SECRET = stringPreferencesKey("consumer_secret")
    }

    val settings: Flow<StoreSettings> = dataStore.data
        .catch { error ->
            // DataStore surfaces read failures as IOException; fall back to defaults.
            if (error is IOException) emit(emptyPreferences()) else throw error
        }
        .map { prefs ->
            StoreSettings(
                siteUrl = prefs[Keys.SITE_URL].orEmpty(),
                consumerKey = prefs[Keys.CONSUMER_KEY].orEmpty(),
                consumerSecret = prefs[Keys.CONSUMER_SECRET].orEmpty(),
            )
        }

    suspend fun update(settings: StoreSettings) {
        dataStore.edit { prefs ->
            prefs[Keys.SITE_URL] = settings.siteUrl.trim()
            prefs[Keys.CONSUMER_KEY] = settings.consumerKey.trim()
            prefs[Keys.CONSUMER_SECRET] = settings.consumerSecret.trim()
        }
    }
}
