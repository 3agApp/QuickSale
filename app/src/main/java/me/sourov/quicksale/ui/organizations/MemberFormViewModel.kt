package me.sourov.quicksale.ui.organizations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.remote.WoapApi
import me.sourov.quicksale.data.remote.WooApiException
import me.sourov.quicksale.data.settings.SettingsRepository

/**
 * Adding somebody to a company, or changing what an existing person may do.
 *
 * The two halves are deliberately different shapes. **Adding** needs a name and an email, because
 * the store creates a real login from them. **Editing** never touches either: a person's name and
 * email belong to their WordPress account, not to this membership, and the plugin's member route
 * would not accept them anyway. What editing changes is the membership — their role, whether they
 * are switched on, and which locations they may send an order to.
 */
class MemberFormViewModel(
    private val organizationId: Long,
    /** Null when adding; the membership being edited otherwise. */
    private val existing: Member?,
    private val organizationRepository: OrganizationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val isEditing: Boolean get() = existing != null

    /** The company's locations, which are what [locationAccess] can be narrowed to. */
    val locations: StateFlow<List<OrgLocation>> =
        organizationRepository.locations(organizationId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _firstName = MutableStateFlow("")
    val firstName: StateFlow<String> = _firstName.asStateFlow()

    private val _lastName = MutableStateFlow("")
    val lastName: StateFlow<String> = _lastName.asStateFlow()

    private val _email = MutableStateFlow("")
    val email: StateFlow<String> = _email.asStateFlow()

    private val _isAdmin = MutableStateFlow(existing?.isAdmin ?: false)
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    private val _isActive = MutableStateFlow(existing?.isActive ?: true)
    val isActive: StateFlow<Boolean> = _isActive.asStateFlow()

    /** Null means unrestricted — every location the company has, including ones added later. */
    private val _allowedLocations = MutableStateFlow(existing?.allowedLocationIds)
    val allowedLocations: StateFlow<Set<Long>?> = _allowedLocations.asStateFlow()

    private val _saving = MutableStateFlow(false)
    val saving: StateFlow<Boolean> = _saving.asStateFlow()

    private val _removing = MutableStateFlow(false)
    val removing: StateFlow<Boolean> = _removing.asStateFlow()

    private val _fieldErrors = MutableStateFlow<Map<String, String>>(emptyMap())
    val fieldErrors: StateFlow<Map<String, String>> = _fieldErrors.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _done = MutableStateFlow(false)
    val done: StateFlow<Boolean> = _done.asStateFlow()

    fun setFirstName(value: String) {
        _firstName.value = value
        _fieldErrors.value = _fieldErrors.value - "first_name"
    }

    fun setLastName(value: String) { _lastName.value = value }

    fun setEmail(value: String) {
        _email.value = value
        _fieldErrors.value = _fieldErrors.value - "email"
    }

    fun setAdmin(value: Boolean) { _isAdmin.value = value }

    fun setActive(value: Boolean) { _isActive.value = value }

    /** Switches between "every location" and a chosen few. */
    fun setUnrestricted(unrestricted: Boolean) {
        _allowedLocations.value = if (unrestricted) null else emptySet()
    }

    fun toggleLocation(locationId: Long) {
        val current = _allowedLocations.value ?: return
        _allowedLocations.value = if (locationId in current) {
            current - locationId
        } else {
            current + locationId
        }
    }

    fun save() {
        if (_saving.value) return
        if (existing == null) add() else update(existing)
    }

    private fun add() {
        val first = _firstName.value.trim()
        val last = _lastName.value.trim()
        val mail = _email.value.trim()
        val problems = buildMap {
            if (first.isBlank()) put("first_name", "A first name is required")
            if (mail.isBlank()) {
                put("email", "An email is required — it becomes their login")
            } else if (!mail.contains('@')) {
                put("email", "That doesn't look like an email address")
            }
        }
        if (problems.isNotEmpty()) {
            _fieldErrors.value = _fieldErrors.value + problems
            return
        }

        _saving.value = true
        viewModelScope.launch {
            runWrite {
                val api = WoapApi(it)
                val member = api.createMember(
                    organizationId = organizationId,
                    email = mail,
                    firstName = first,
                    lastName = last,
                    role = if (_isAdmin.value) ROLE_ADMIN else ROLE_MEMBER,
                )
                // A location restriction can only be set once the membership exists, so it is a
                // second call rather than part of the first.
                val finished = _allowedLocations.value
                    ?.takeIf { ids -> ids.isNotEmpty() }
                    ?.let { ids ->
                        api.updateMember(
                            organizationId = organizationId,
                            memberId = member.memberId,
                            locationAccess = ids,
                        )
                    }
                    ?: member
                organizationRepository.saveMember(finished)
            }
            _saving.value = false
        }
    }

    private fun update(member: Member) {
        _saving.value = true
        viewModelScope.launch {
            runWrite {
                val restricted = _allowedLocations.value
                val updated = WoapApi(it).updateMember(
                    organizationId = organizationId,
                    memberId = member.memberId,
                    role = if (_isAdmin.value) ROLE_ADMIN else ROLE_MEMBER,
                    status = if (_isActive.value) Member.STATUS_ACTIVE else STATUS_INACTIVE,
                    // An empty chosen set is not a restriction the store can store — in its
                    // storage an empty access list *means* unrestricted — so it is sent as "all"
                    // rather than as a list nobody could deliver to.
                    locationAccess = restricted?.takeIf { ids -> ids.isNotEmpty() },
                    unrestrictedLocations = restricted == null || restricted.isEmpty(),
                )
                organizationRepository.saveMember(updated)
            }
            _saving.value = false
        }
    }

    /**
     * Takes this person off the account.
     *
     * The store keeps their login and demotes them to an ordinary customer; it refuses outright
     * when they are the last active admin, and that refusal is shown as it comes rather than
     * pre-empted here — the app's copy of who is an admin can be a sync out of date.
     */
    fun remove() {
        val member = existing ?: return
        if (_removing.value) return
        _removing.value = true
        viewModelScope.launch {
            runWrite {
                WoapApi(it).deleteMember(organizationId, member.memberId)
                organizationRepository.deleteMember(member.memberId)
            }
            _removing.value = false
        }
    }

    /** The shared shape of every write here: check the connection, run it, translate a refusal. */
    private suspend fun runWrite(block: suspend (me.sourov.quicksale.data.settings.StoreSettings) -> Unit) {
        try {
            val settings = settingsRepository.settings.first()
            if (!settings.isConfigured) {
                _error.value = "Connect your store in Settings first"
                return
            }
            block(settings)
            _done.value = true
        } catch (e: WooApiException) {
            _fieldErrors.value = _fieldErrors.value + e.params
            _error.value = if (e.params.isEmpty()) e.message else null
        } catch (e: Exception) {
            _error.value = e.message ?: "The store couldn't be reached"
        }
    }


    /**
     * Clears the finished flag once the caller has acted on it.
     *
     * This view model is keyed and so outlives the sheet that shows it. Left set, the flag fired
     * again the instant the sheet was reopened and closed it before it had drawn — the row's edit
     * button simply stopped working after the first save.
     */
    fun consumeDone() { _done.value = false }

    fun consumeError() { _error.value = null }

    companion object {
        const val ROLE_ADMIN = "admin"
        const val ROLE_MEMBER = "member"
        const val STATUS_INACTIVE = "inactive"

        fun factory(
            organizationId: Long,
            existing: Member?,
            organizationRepository: OrganizationRepository,
            settingsRepository: SettingsRepository,
        ) = viewModelFactory {
            initializer {
                MemberFormViewModel(
                    organizationId = organizationId,
                    existing = existing,
                    organizationRepository = organizationRepository,
                    settingsRepository = settingsRepository,
                )
            }
        }
    }
}
