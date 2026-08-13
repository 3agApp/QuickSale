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
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.remote.WoapApi
import me.sourov.quicksale.data.remote.WooApiException
import me.sourov.quicksale.data.settings.AddressField
import me.sourov.quicksale.data.settings.AddressFormRepository
import me.sourov.quicksale.data.settings.AddressForms
import me.sourov.quicksale.data.settings.SettingsRepository
import org.json.JSONObject

/**
 * Editing a company's own details: what it is called, where it is billed, and whether an order may
 * be sent anywhere other than its saved locations.
 *
 * Status is not here. Moving an account between pending, active, suspended and rejected is what
 * sends the shop's approval and rejection mail, so the route refuses it on this call and the app
 * keeps it as a deliberate, separately-confirmed action.
 *
 * Tax ID is not here either, for a duller reason: the sync snapshot leaves it out, so the app has
 * no current value to show. A field that starts blank on a company that has one is a field that
 * quietly erases it.
 */
class OrganizationFormViewModel(
    private val organization: Organization,
    private val organizationRepository: OrganizationRepository,
    addressFormRepository: AddressFormRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val addressForms: StateFlow<AddressForms> = addressFormRepository.forms
        .stateIn(viewModelScope, SharingStarted.Eagerly, AddressForms())

    /** The billing block as the store last reported it, which is what the form starts from. */
    private val savedBilling: Map<String, String> = runCatching {
        val json = JSONObject(organization.billingJson)
        buildMap {
            json.keys().forEach { key -> put(key, json.optString(key)) }
        }
    }.getOrDefault(emptyMap())

    private val _name = MutableStateFlow(organization.name)
    val name: StateFlow<String> = _name.asStateFlow()

    private val _email = MutableStateFlow(organization.email)
    val email: StateFlow<String> = _email.asStateFlow()

    private val _allowCustomShipping = MutableStateFlow(organization.allowCustomShipping)
    val allowCustomShipping: StateFlow<Boolean> = _allowCustomShipping.asStateFlow()

    private val _country = MutableStateFlow(organization.country)
    val country: StateFlow<String> = _country.asStateFlow()

    private val _values = MutableStateFlow(savedBilling)
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    /**
     * The billing form for the chosen country.
     *
     * The store's *billing* definitions, which are not the delivery ones: billing keeps
     * WooCommerce's own rules where a delivery address relaxes them, so a surname the store
     * requires is marked required here rather than optional. `email` is dropped because the sheet
     * asks for it in its own field above, next to the name — two boxes for one answer is the
     * mistake the company field made.
     */
    val fields: StateFlow<List<AddressField>> =
        combine(addressForms, _country) { forms, code ->
            forms.billingFieldsFor(code.ifBlank { forms.defaultCountry })
                .filterNot { it.name == EMAIL }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether the store requires a billing email for the chosen country.
     *
     * Read from the same billing definitions as everything else rather than assumed, and carried
     * across to the sheet's own email field: taking `email` out of the rendered form took its
     * required-ness with it, and a field the store refuses a save over has to say so before the
     * save, not after it. On this shop's countries it is required — as are the phone and the
     * surname, which the delivery form marks optional.
     */
    val emailRequired: StateFlow<Boolean> =
        combine(addressForms, _country) { forms, code ->
            forms.billingFieldsFor(code.ifBlank { forms.defaultCountry })
                .firstOrNull { it.name == EMAIL }
                ?.required ?: false
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _fieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val fieldErrors: StateFlow<Map<String, String>> = _fieldErrors.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _saved = MutableStateFlow(false)
    val saved: StateFlow<Boolean> = _saved.asStateFlow()

    init {
        // A company whose stored billing carries no country still has to render some form.
        if (_country.value.isBlank()) {
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

    fun setEmail(value: String) {
        _email.value = value
        _fieldErrors.value = _fieldErrors.value - "email"
    }

    fun setAllowCustomShipping(value: Boolean) { _allowCustomShipping.value = value }

    fun selectCountry(code: String) {
        _country.value = code
        val allowed = addressForms.value.billingFieldsFor(code).map { it.name }.toSet()
        _values.value = _values.value.filterKeys { it in allowed } + ("country" to code)
    }

    fun setField(name: String, value: String) {
        _values.value = _values.value + (name to value)
        _fieldErrors.value = _fieldErrors.value - name
    }

    fun save() {
        if (_saving.value) return
        val companyName = _name.value.trim()
        val mail = _email.value.trim()

        // Checked here and not left to the store, for the two the sheet asks for in fields of its
        // own: everything inside the address form carries its own required flag and the form marks
        // it, but these two sit outside it. A store refusal for a blank the screen never flagged is
        // a round trip the operator did not need to make.
        val problems = buildMap {
            if (companyName.isBlank()) put("name", "A company needs a name")
            if (emailRequired.value && mail.isBlank()) {
                put("email", "The store needs a billing email for this account")
            }
        }
        if (problems.isNotEmpty()) {
            _fieldErrors.value = _fieldErrors.value + problems
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
                // Everything the store already had, with what this form edits laid over the top,
                // rather than a payload rebuilt from the visible fields — that dropped every
                // billing key this country's form doesn't draw. Switching country still clears
                // what that country has no field for, because [selectCountry] prunes _values.
                //
                // Note the store has the last word regardless: the plugin intersects a submitted
                // address with the *billing* fields it defines for that country and blanks the
                // rest, so `company` — which it no longer collects anywhere, deriving it from the
                // account name on save — is cleared no matter what is sent from here.
                val billing = _values.value + mapOf(
                    "country" to _country.value.ifBlank { addressForms.value.defaultCountry },
                    EMAIL to mail,
                )
                val updated = WoapApi(settings).updateOrganization(
                    organizationId = organization.id,
                    name = companyName,
                    allowCustomShipping = _allowCustomShipping.value,
                    billing = billing,
                )
                // Applied locally from the row the store returned, so the account reads correctly
                // the moment the sheet closes rather than at the next snapshot.
                organizationRepository.saveOrganization(updated)
                _saved.value = true
            } catch (e: WooApiException) {
                _fieldErrors.value = e.params
                _error.value = if (e.params.isEmpty()) e.message else null
            } catch (e: Exception) {
                _error.value = e.message ?: "The store couldn't be reached"
            } finally {
                _saving.value = false
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
        /** The one billing field the sheet asks for outside the address form. */
        private const val EMAIL = "email"

        fun factory(
            organization: Organization,
            organizationRepository: OrganizationRepository,
            addressFormRepository: AddressFormRepository,
            settingsRepository: SettingsRepository,
        ) = viewModelFactory {
            initializer {
                OrganizationFormViewModel(
                    organization = organization,
                    organizationRepository = organizationRepository,
                    addressFormRepository = addressFormRepository,
                    settingsRepository = settingsRepository,
                )
            }
        }
    }
}
