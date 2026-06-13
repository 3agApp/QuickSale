package me.sourov.quicksale.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

/** Tracks when data was last synced. */
class SyncMetaRepository(private val dataStore: DataStore<Preferences>) {

    private val lastSyncKey = longPreferencesKey("last_sync_millis")

    /** Epoch millis of the last successful sync, or 0 if never. */
    val lastSyncMillis: Flow<Long> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[lastSyncKey] ?: 0L }

    suspend fun setLastSync(millis: Long) {
        dataStore.edit { it[lastSyncKey] = millis }
    }
}
