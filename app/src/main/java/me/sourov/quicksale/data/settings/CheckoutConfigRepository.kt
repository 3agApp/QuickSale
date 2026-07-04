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
 * Persists the store's [CheckoutConfig] (as JSON in the shared settings DataStore) so the order
 * screen can offer payment methods, shipping and tax hints while offline. Refreshed on sync.
 */
class CheckoutConfigRepository(private val dataStore: DataStore<Preferences>) {

    private object Keys {
        val CONFIG = stringPreferencesKey("checkout_config_json")
    }

    val config: Flow<CheckoutConfig> = dataStore.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map { prefs -> prefs[Keys.CONFIG]?.let(::decode) ?: CheckoutConfig() }

    suspend fun setConfig(config: CheckoutConfig) {
        dataStore.edit { prefs -> prefs[Keys.CONFIG] = encode(config) }
    }

    private fun encode(config: CheckoutConfig): String = JSONObject().apply {
        put("taxesEnabled", config.taxesEnabled)
        put("pricesIncludeTax", config.pricesIncludeTax)
        config.standardTaxRatePercent?.let { put("standardTaxRatePercent", it) }
        put("taxLabel", config.taxLabel)
        put("gateways", JSONArray().apply {
            config.gateways.forEach { gateway ->
                put(JSONObject().put("id", gateway.id).put("title", gateway.title))
            }
        })
        put("shippingOptions", JSONArray().apply {
            config.shippingOptions.forEach { option ->
                put(
                    JSONObject()
                        .put("zoneName", option.zoneName)
                        .put("methodId", option.methodId)
                        .put("title", option.title)
                        .put("cost", option.cost)
                        .put("taxable", option.taxable)
                )
            }
        })
    }.toString()

    private fun decode(json: String): CheckoutConfig? = runCatching {
        val obj = JSONObject(json)
        CheckoutConfig(
            taxesEnabled = obj.optBoolean("taxesEnabled"),
            pricesIncludeTax = obj.optBoolean("pricesIncludeTax"),
            standardTaxRatePercent =
                if (obj.has("standardTaxRatePercent")) obj.optDouble("standardTaxRatePercent") else null,
            taxLabel = obj.optString("taxLabel").ifBlank { "Tax" },
            gateways = obj.optJSONArray("gateways").toObjectList { gateway ->
                PaymentGateway(id = gateway.optString("id"), title = gateway.optString("title"))
            },
            shippingOptions = obj.optJSONArray("shippingOptions").toObjectList { option ->
                ShippingOption(
                    zoneName = option.optString("zoneName"),
                    methodId = option.optString("methodId"),
                    title = option.optString("title"),
                    cost = option.optString("cost"),
                    taxable = option.optBoolean("taxable", true),
                )
            },
        )
    }.getOrNull()

    private fun <T> JSONArray?.toObjectList(map: (JSONObject) -> T): List<T> {
        if (this == null) return emptyList()
        return buildList {
            for (i in 0 until length()) {
                optJSONObject(i)?.let { add(map(it)) }
            }
        }
    }
}
