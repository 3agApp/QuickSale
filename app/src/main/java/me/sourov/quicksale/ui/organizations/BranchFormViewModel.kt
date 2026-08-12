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
 * Adding or editing one of a company's saved branches.
 *
 * This is the *persistent* address editor — the counterpart to the delivery form on checkout, which
 * only ever affects the order in hand. A branch saved here is what every future order starts from,
 * and what the shop's own checkout offers the customer on the website.
 */
class BranchFormViewModel(
    private val organizationId: Long,
    /** Null when adding; the branch being edited otherwise. */
    private val existing: OrgLocation?,
    organizationRepository: OrganizationRepository,
    addressFormRepository: AddressFormRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val isEditing: Boolean get() = existing != null

    val addressForms: StateFlow<AddressForms> = addressFormRepository.forms
        .stateIn(viewModelScope, SharingStarted.Eagerly, AddressForms())

    /** Whether this company has any branch at all — the first one is default whether asked or not. */
    private val branchCount = organizationRepository.locations(organizationId)
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
        // A new branch starts on the shop's own base country, the same preselection the checkout
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
        val branchName = _name.value.trim()
        if (branchName.isBlank()) {
            _fieldErrors.value = _fieldErrors.value + ("name" to "A branch needs a name")
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
                // Only the fields this country's form defines, so nothing stray is posted under a
                // name this country's form never had.
                val payload = fields.value.associate { field ->
                    field.name to _values.value[field.name].orEmpty()
                } + ("country" to _country.value.ifBlank { addressForms.value.defaultCountry })
                // The first branch is the one every order starts from whether or not the box was
                // ticked; an account whose only branch isn't default has no sensible default.
                val default = _isDefault.value || branchCount.value.isEmpty()

                if (existing == null) {
                    api.createLocation(organizationId, branchName, default, payload)
                } else {
                    api.updateLocation(organizationId, existing.id, branchName, default, payload)
                }
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
                BranchFormViewModel(
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
