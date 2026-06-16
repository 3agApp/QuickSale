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

/** Tracks when each [SyncTarget] was last synced. */
class SyncMetaRepository(private val dataStore: DataStore<Preferences>) {

    private fun keyFor(target: SyncTarget) = longPreferencesKey("last_sync_${target.name}")

    /** Epoch millis of the last successful sync for [target], or 0 if never. */
    fun lastSyncMillis(target: SyncTarget): Flow<Long> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { it[keyFor(target)] ?: 0L }

    suspend fun setLastSync(target: SyncTarget, millis: Long) {
        dataStore.edit { it[keyFor(target)] = millis }
    }
}
