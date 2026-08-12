package me.sourov.quicksale.ui.organizations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
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

/** A newly created person, and the company they were attached to. */
data class CreatedCustomer(
    val organizationId: Long,
    val memberUserId: Long,
    val name: String,
    val organizationName: String,
)

/** Which company the new person belongs to. */
sealed interface CompanyChoice {
    /** One that already exists — the common case for a second buyer at a known customer. */
    data class Existing(val organization: Organization) : CompanyChoice

    /** A company being signed up in the same breath as its first buyer. */
    data object New : CompanyChoice
}

/**
 * Signing up a trade customer at the stand.
 *
 * The person comes first because that is who is standing there: a name and an email is all it takes
 * to identify them, and only then does the question of which company they buy for arise. Most of
 * the time that company already exists and the whole thing is three fields and a tap; when it
 * doesn't, the company is created with them in one go.
 *
 * A new company is created **active**, not the route's default of pending — the order is being
 * placed in the next few seconds and a pending organization cannot trade.
 */
class NewCustomerViewModel(
    private val organizationRepository: OrganizationRepository,
    addressFormRepository: AddressFormRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val addressForms: StateFlow<AddressForms> = addressFormRepository.forms
        .stateIn(viewModelScope, SharingStarted.Eagerly, AddressForms())

    private val _firstName = MutableStateFlow("")
    val firstName: StateFlow<String> = _firstName.asStateFlow()

    private val _lastName = MutableStateFlow("")
    val lastName: StateFlow<String> = _lastName.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _choice = MutableStateFlow<CompanyChoice?>(null)
    val choice: StateFlow<CompanyChoice?> = _choice.asStateFlow()

    private val _companyQuery = MutableStateFlow("")
    val companyQuery: StateFlow<String> = _companyQuery.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val companyMatches: StateFlow<List<Organization>> = _companyQuery
        .flatMapLatest { organizationRepository.searchSellableOrganizations(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // --- Only used when [choice] is [CompanyChoice.New] ---

    private val _companyName = MutableStateFlow("")
    val companyName: StateFlow<String> = _companyName.asStateFlow()

    private val _country = MutableStateFlow("")
    val country: StateFlow<String> = _country.asStateFlow()

    private val _values = MutableStateFlow<Map<String, String>>(emptyMap())
    val values: StateFlow<Map<String, String>> = _values.asStateFlow()

    /**
     * The billing form for the chosen country.
     *
     * The shop's *shipping* field definitions are reused for billing deliberately: they are the
     * same per-country address shape and the plugin serves only one form.
     */
    val fields: StateFlow<List<AddressField>> =
        combine(addressForms, _country) { forms, code ->
            forms.fieldsFor(code.ifBlank { forms.defaultCountry })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _fieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val fieldErrors: StateFlow<Map<String, String>> = _fieldErrors.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _created = MutableStateFlow<CreatedCustomer?>(null)
    val created: StateFlow<CreatedCustomer?> = _created.asStateFlow()

    init {
        viewModelScope.launch {
            val forms = addressForms.first { !it.isEmpty }
            if (_country.value.isBlank()) selectCountry(forms.defaultCountry)
        }
    }

    fun setFirstName(value: String) {
        _firstName.value = value
        _fieldErrors.value = _fieldErrors.value - "first_name"
    }

    fun setLastName(value: String) { _lastName.value = value }

    fun setEmail(value: String) {
        _email.value = value
        _fieldErrors.value = _fieldErrors.value - "email"
    }

    fun setCompanyQuery(value: String) { _companyQuery.value = value }

    fun chooseExisting(organization: Organization) {
        _choice.value = CompanyChoice.Existing(organization)
    }

    fun chooseNewCompany() { _choice.value = CompanyChoice.New }

    /** Back to "which company", without losing the person already typed in. */
    fun clearChoice() { _choice.value = null }

    fun setCompanyName(value: String) {
        _companyName.value = value
        _fieldErrors.value = _fieldErrors.value - "name"
    }

    fun selectCountry(code: String) {
        _country.value = code
        val allowed = addressForms.value.fieldsFor(code).map { it.name }.toSet()
        _values.value = _values.value.filterKeys { it in allowed } + ("country" to code)
    }

    fun setField(name: String, value: String) {
        _values.value = _values.value + (name to value)
        _fieldErrors.value = _fieldErrors.value - name
    }

    /** True once there is enough to create the person, ignoring the company question. */
    val personComplete: StateFlow<Boolean> =
        combine(_firstName, _email) { first, mail ->
            first.isNotBlank() && mail.contains('@')
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    fun save() {
        if (_saving.value) return
        val first = _firstName.value.trim()
        val last = _lastName.value.trim()
        val mail = _email.value.trim()
        val target = _choice.value ?: return

        val problems = buildMap {
            if (first.isBlank()) put("first_name", "A first name is required")
            if (mail.isBlank()) {
                put("email", "An email is required — it becomes their login")
            } else if (!mail.contains('@')) {
                put("email", "That doesn't look like an email address")
            }
            if (target is CompanyChoice.New && _companyName.value.isBlank()) {
                put("name", "A company name is required")
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
                val api = WoapApi(settings)

                val organization = when (target) {
                    is CompanyChoice.Existing -> target.organization
                    CompanyChoice.New -> {
                        val billing = fields.value.associate { field ->
                            field.name to _values.value[field.name].orEmpty()
                        } + mapOf(
                            "country" to _country.value.ifBlank { addressForms.value.defaultCountry },
                            "email" to mail,
                            // The person signing up is the company's billing contact too, unless
                            // the operator typed someone else into the address form.
                            "first_name" to _values.value["first_name"].orEmpty().ifBlank { first },
                            "last_name" to _values.value["last_name"].orEmpty().ifBlank { last },
                        )
                        api.createOrganization(
                            name = _companyName.value.trim(),
                            billing = billing,
                        )
                    }
                }

                val member = api.createMember(
                    organizationId = organization.id,
                    email = mail,
                    firstName = first,
                    lastName = last,
                )
                _created.value = CreatedCustomer(
                    organizationId = organization.id,
                    memberUserId = member.userId,
                    name = member.name.ifBlank { "$first $last".trim() },
                    organizationName = organization.name,
                )
            } catch (e: WooApiException) {
                _fieldErrors.value = _fieldErrors.value + e.params
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
            organizationRepository: OrganizationRepository,
            addressFormRepository: AddressFormRepository,
            settingsRepository: SettingsRepository,
        ) = viewModelFactory {
            initializer {
                NewCustomerViewModel(
                    organizationRepository,
                    addressFormRepository,
                    settingsRepository,
                )
            }
        }
    }
}
