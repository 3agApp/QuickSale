package me.sourov.quicksale.data.settings

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

/**
 * Persists the shop's per-country shipping [AddressForms] so a one-off delivery address can be
 * entered offline against the same field definitions the shop's checkout would render.
 * Refreshed alongside the organization snapshot.
 */
class AddressFormRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val FORMS = stringPreferencesKey("address_forms_json")
    }

    val forms: Flow<AddressForms> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[Keys.FORMS]?.let(::decode) ?: AddressForms() }

    suspend fun setForms(forms: AddressForms) {
        dataStore.edit { prefs -> prefs[Keys.FORMS] = encode(forms) }
    }

    private fun encode(forms: AddressForms): String = JSONObject().apply {
        put("default_country", forms.defaultCountry)
        put("countries", forms.countries.toJson())
        put("forms", JSONObject().apply {
            forms.forms.forEach { (country, fields) ->
                put(country, JSONArray().apply {
                    fields.forEach { field ->
                        put(JSONObject().apply {
                            put("name", field.name)
                            put("label", field.label)
                            put("required", field.required)
                            put("hidden", field.hidden)
                            put("type", field.type)
                            if (field.options.isNotEmpty()) put("options", field.options.toJson())
                        })
                    }
                })
            }
        })
    }.toString()

    private fun decode(json: String): AddressForms? = runCatching {
        val obj = JSONObject(json)
        val formsJson = obj.optJSONObject("forms")
        AddressForms(
            defaultCountry = obj.optString("default_country"),
            countries = obj.optJSONObject("countries").toStringMap(),
            forms = buildMap {
                formsJson?.keys()?.forEach { country ->
                    val array = formsJson.optJSONArray(country) ?: return@forEach
                    val fields = buildList(array.length()) {
                        for (i in 0 until array.length()) {
                            val field = array.optJSONObject(i) ?: continue
                            add(
                                AddressField(
                                    name = field.optString("name"),
                                    label = field.optString("label"),
                                    required = field.optBoolean("required"),
                                    hidden = field.optBoolean("hidden"),
                                    type = field.optString("type").ifBlank { "text" },
                                    options = field.optJSONObject("options").toStringMap(),
                                )
                            )
                        }
                    }
                    if (fields.isNotEmpty()) put(country, fields)
                }
            },
        )
    }.getOrNull()

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return buildMap { keys().forEach { key -> put(key, optString(key)) } }
    }

    private fun Map<String, String>.toJson(): JSONObject =
        JSONObject().also { json -> forEach { (key, value) -> json.put(key, value) } }
}
