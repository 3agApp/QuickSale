package me.sourov.quicksale.data.update

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class AppUpdatePreferences(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val SKIPPED_VERSION_TAG = stringPreferencesKey("skipped_update_tag")
    }

    val skippedVersionTag: Flow<String?> = dataStore.data.map { prefs ->
        prefs[Keys.SKIPPED_VERSION_TAG]?.takeIf { it.isNotBlank() }
    }

    suspend fun skipVersion(tagName: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SKIPPED_VERSION_TAG] = tagName
        }
    }
}
