package me.sourov.quicksale.ui.organizations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.remote.WoapApi
import me.sourov.quicksale.data.remote.WooApiException
import me.sourov.quicksale.data.settings.AddressField
import me.sourov.quicksale.data.settings.AddressFormRepository
import me.sourov.quicksale.data.settings.AddressForms
import me.sourov.quicksale.data.settings.SettingsRepository

/**
 * Adding or editing one of a company's saved locations.
 *
 * This is the *persistent* address editor — the counterpart to the delivery form on checkout, which
 * only ever affects the order in hand. A location saved here is what every future order starts from,
 * and what the shop's own checkout offers the customer on the website.
 */
class LocationFormViewModel(
    private val organizationId: Long,
    /** Null when adding; the location being edited otherwise. */
    private val existing: OrgLocation?,
    private val organizationRepository: OrganizationRepository,
    addressFormRepository: AddressFormRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val isEditing: Boolean get() = existing != null

    val addressForms: StateFlow<AddressForms> = addressFormRepository.forms
        .stateIn(viewModelScope, SharingStarted.Eagerly, AddressForms())

    /** Whether this company has any location at all — the first one is default whether asked or not. */
    private val existingLocations = organizationRepository.locations(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _name = MutableStateFlow(existing?.name.orEmpty())
    val name: StateFlow<String> = _name.asStateFlow()

    private val _isDefault = MutableStateFlow(existing?.isDefault == true)
    val isDefault: StateFlow<Boolean> = _isDefault.asStateFlow()

    private val _country = MutableStateFlow(existing?.country.orEmpty())
    val country: StateFlow<String> = _country.asStateFlow()

    private val _values = MutableStateFlow(existing?.toAddressFields() ?: emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    val fields: StateFlow<List<AddressField>> =
        combine(addressForms, _country) { forms, code ->
            forms.fieldsFor(code.ifBlank { forms.defaultCountry })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    /** The store's own reasons for refusing individual fields, from `data.params`. */
    private val _fieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val fieldErrors: StateFlow<Map<String, String>> = _fieldErrors.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        // A new location starts on the shop's own base country, the same preselection the checkout
        // form uses, so the right fields are on screen before anything is typed.
        if (existing == null) {
            viewModelScope.launch {
                val forms = addressForms.first { !it.isEmpty }
                if (_country.value.isBlank()) selectCountry(forms.defaultCountry)
            }
        }
    }

    fun setName(value: String) {
        _name.value = value
        _fieldErrors.value = _fieldErrors.value - "name"
    }

    fun setDefault(value: Boolean) { _isDefault.value = value }

    fun selectCountry(code: String) {
        _country.value = code
        // Fields a country doesn't have are dropped rather than sent — the same as the shop's own
        // forms do, where a state posted for a country with no states is not data.
        val allowed = addressForms.value.fieldsFor(code).map { it.name }.toSet()
        _values.value = _values.value.filterKeys { it in allowed } + ("country" to code)
    }

    fun setField(name: String, value: String) {
        _values.value = _values.value + (name to value)
        _fieldErrors.value = _fieldErrors.value - name
    }

    /** Required fields still empty, by label. The store applies the real rules on top of this. */
    fun missingFields(): List<String> =
        fields.value.filter { it.required && _values.value[it.name].orEmpty().isBlank() }
            .map { it.label }

    fun save() {
        if (_saving.value) return
        val locationName = _name.value.trim()
        if (locationName.isBlank()) {
            _fieldErrors.value = _fieldErrors.value + ("name" to "A location needs a name")
            return
        }
        _saving.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                if (!settings.isConfigured) {
                    _error.value = "Connect your store in Settings first"
                    return@launch
                }
                val api = WoapApi(settings)
                // What the store already held, with the edited fields over the top. Posting only
                // the *visible* fields blanks whatever this shop marks hidden — `company` on most
                // shipping forms — and a country change still prunes what it should, because
                // [selectCountry] filters _values.
                val payload = _values.value +
                    ("country" to _country.value.ifBlank { addressForms.value.defaultCountry })
                // The first location is the one every order starts from whether or not the box was
                // ticked; an account whose only location isn't default has no sensible default.
                val default = _isDefault.value || existingLocations.value.isEmpty()

                val saved = if (existing == null) {
                    api.createLocation(organizationId, locationName, default, payload)
                } else {
                    api.updateLocation(organizationId, existing.id, locationName, default, payload)
                }
                // Applied locally straight away, from the row the store returned rather than from
                // what was typed: a new location has to be in the picker for the order being built
                // right now, not after the next snapshot comes down.
                organizationRepository.saveLocation(saved)
                _saved.value = true
            } catch (e: WooApiException) {
                // A validation refusal names the fields; mark those and keep the message for the
                // rest, rather than showing one banner over a fourteen-field address.
                _fieldErrors.value = e.params
                _error.value = if (e.params.isEmpty()) e.message else null
            } catch (e: Exception) {
                _error.value = e.message ?: "The store couldn't be reached"
            } finally {
                _saving.value = false
            }
        }
    }

    private val _deleting = MutableStateFlow(false)
    val deleting: StateFlow<Boolean> = _deleting.asStateFlow()

    /**
     * Set once a delete has gone through and the store reported the company can no longer ship
     * anywhere — worth repeating, because the store allows removing the last location and an
     * account with none can only be sold to over the counter.
     */
    private val _shippingLost = MutableStateFlow(false)
    val shippingLost: StateFlow<Boolean> = _shippingLost.asStateFlow()

    /** Removes this location from the company, and with it every member's access to it. */
    fun delete() {
        val location = existing ?: return
        if (_deleting.value) return
        _deleting.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                if (!settings.isConfigured) {
                    _error.value = "Connect your store in Settings first"
                    return@launch
                }
                val removal = WoapApi(settings).deleteLocation(organizationId, location.id)
                organizationRepository.deleteLocation(location.id)
                _shippingLost.value = !removal.organizationCanShip
                // The caller closes on [saved]; a delete is as finished as a save.
                _saved.value = true
            } catch (e: WooApiException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "The store couldn't be reached"
            } finally {
                _deleting.value = false
            }
        }
    }


    /**
     * Clears the finished flag once the caller has acted on it.
     *
     * This view model is keyed and so outlives the sheet that shows it. Left set, the flag fired
     * again the instant the sheet was reopened and closed it before it had drawn — the row's edit
     * button simply stopped working after the first save.
     */
    fun consumeSaved() { _saved.value = false }

    fun consumeError() { _error.value = null }

    companion object {
        fun factory(
            organizationId: Long,
            existing: OrgLocation?,
            organizationRepository: OrganizationRepository,
            addressFormRepository: AddressFormRepository,
            settingsRepository: SettingsRepository,
        ) = viewModelFactory {
            initializer {
                LocationFormViewModel(
                    organizationId = organizationId,
                    existing = existing,
                    organizationRepository = organizationRepository,
                    addressFormRepository = addressFormRepository,
                    settingsRepository = settingsRepository,
                )
            }
        }
    }
}
