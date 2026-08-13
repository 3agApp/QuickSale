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
 * Persists the shop's per-country [AddressForms], both shapes, so an address can be entered
 * offline against the same field definitions the shop's checkout would render.
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
        put("billing_countries", forms.sellToCountries.toJson())
        put("forms", forms.forms.toJson())
        put("billing_forms", forms.billingForms.toJson())
    }.toString()

    private fun decode(json: String): AddressForms? = runCatching {
        val obj = JSONObject(json)
        AddressForms(
            defaultCountry = obj.optString("default_country"),
            countries = obj.optJSONObject("countries").toStringMap(),
            forms = obj.optJSONObject("forms").toFormMap(),
            // Both absent from anything written before the billing shape was stored. Left empty
            // rather than filled from the shipping side, so [AddressForms] is the one place that
            // decides what to fall back to.
            sellToCountries = obj.optJSONObject("billing_countries").toStringMap(),
            billingForms = obj.optJSONObject("billing_forms").toFormMap(),
        )
    }.getOrNull()

    @JvmName("formsToJson")
    private fun Map<String, List<AddressField>>.toJson(): JSONObject = JSONObject().apply {
        forEach { (country, fields) ->
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
    }

    private fun JSONObject?.toFormMap(): Map<String, List<AddressField>> {
        if (this == null) return emptyMap()
        return buildMap {
            keys().forEach { country ->
                val array = optJSONArray(country) ?: return@forEach
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
        }
    }

    private fun JSONObject?.toStringMap(): Map<String, String> {
        if (this == null) return emptyMap()
        return buildMap { keys().forEach { key -> put(key, optString(key)) } }
    }

    private fun Map<String, String>.toJson(): JSONObject =
        JSONObject().also { json -> forEach { (key, value) -> json.put(key, value) } }
}
