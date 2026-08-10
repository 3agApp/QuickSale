package me.sourov.quicksale.data.sync

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import org.json.JSONObject
import java.io.IOException

/**
 * Remembers the `ETag` each synced page last returned, so the next poll can send `If-None-Match`
 * and let an unchanged page answer `304` with no body.
 *
 * Page ETags are stored keyed by page number; the address form has a single ETag of its own.
 */
class SyncEtagRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val ORGANIZATION_PAGES = stringPreferencesKey("etag_organization_pages")
        val ADDRESS_FORM = stringPreferencesKey("etag_address_form")
    }

    /** Stored page ETags, keyed by 1-based page number. Empty when nothing has synced yet. */
    suspend fun organizationPageEtags(): Map<Int, String> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[Keys.ORGANIZATION_PAGES]?.let(::decode) ?: emptyMap() }
        .first()

    suspend fun setOrganizationPageEtags(etags: Map<Int, String>) {
        dataStore.edit { prefs -> prefs[Keys.ORGANIZATION_PAGES] = encode(etags) }
    }

    suspend fun addressFormEtag(): String? = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[Keys.ADDRESS_FORM]?.takeIf { it.isNotBlank() } }
        .first()

    suspend fun setAddressFormEtag(etag: String?) {
        dataStore.edit { prefs -> prefs[Keys.ADDRESS_FORM] = etag.orEmpty() }
    }

    /** Forgets every stored ETag, forcing the next sync to pull full bodies. */
    suspend fun clear() {
        dataStore.edit { prefs ->
            prefs.remove(Keys.ORGANIZATION_PAGES)
            prefs.remove(Keys.ADDRESS_FORM)
        }
    }

    private fun encode(etags: Map<Int, String>): String = JSONObject().apply {
        etags.forEach { (page, etag) -> put(page.toString(), etag) }
    }.toString()

    private fun decode(json: String): Map<Int, String> = runCatching {
        val obj = JSONObject(json)
        buildMap {
            obj.keys().forEach { key ->
                val page = key.toIntOrNull() ?: return@forEach
                obj.optString(key).takeIf { it.isNotBlank() }?.let { put(page, it) }
            }
        }
    }.getOrDefault(emptyMap())
}
