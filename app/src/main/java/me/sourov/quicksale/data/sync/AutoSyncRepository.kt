package me.sourov.quicksale.data.sync

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
import java.util.concurrent.TimeUnit

/** How often the app re-syncs on its own while it's open. */
enum class SyncInterval(val minutes: Int, val label: String) {
    EVERY_15(15, "Every 15 minutes"),
    EVERY_30(30, "Every 30 minutes"),
    HOURLY(60, "Every hour"),
    EVERY_4_HOURS(240, "Every 4 hours"),
    TWICE_DAILY(720, "Every 12 hours");

    val millis: Long get() = TimeUnit.MINUTES.toMillis(minutes.toLong())

    companion object {
        val DEFAULT = EVERY_30

        fun fromMinutes(minutes: Int?): SyncInterval =
            entries.firstOrNull { it.minutes == minutes } ?: DEFAULT
    }
}

/**
 * When the app should sync without being asked.
 *
 * The snapshot route is built for exactly this: every page carries an ETag, so an interval poll
 * against an unchanged store costs a hash comparison rather than a payload.
 */
data class AutoSyncSettings(
    val enabled: Boolean = true,
    val interval: SyncInterval = SyncInterval.DEFAULT,
    /** Sync on launch when the local copy is already older than [interval]. */
    val syncOnLaunch: Boolean = true,
)

class AutoSyncRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val ENABLED = booleanPreferencesKey("auto_sync_enabled")
        val INTERVAL_MINUTES = intPreferencesKey("auto_sync_interval_minutes")
        val ON_LAUNCH = booleanPreferencesKey("auto_sync_on_launch")
    }

    val settings: Flow<AutoSyncSettings> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs ->
            AutoSyncSettings(
                enabled = prefs[Keys.ENABLED] ?: true,
                interval = SyncInterval.fromMinutes(prefs[Keys.INTERVAL_MINUTES]),
                syncOnLaunch = prefs[Keys.ON_LAUNCH] ?: true,
            )
        }

    suspend fun setEnabled(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ENABLED] = enabled }
    }

    suspend fun setInterval(interval: SyncInterval) {
        dataStore.edit { prefs -> prefs[Keys.INTERVAL_MINUTES] = interval.minutes }
    }

    suspend fun setSyncOnLaunch(enabled: Boolean) {
        dataStore.edit { prefs -> prefs[Keys.ON_LAUNCH] = enabled }
    }
}
