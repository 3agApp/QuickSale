package me.sourov.quicksale.ui.organizations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.remote.WoapApi
import me.sourov.quicksale.data.remote.WooApiException
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.OrganizationStatus
import me.sourov.quicksale.data.local.MemberWithOrganization
import me.sourov.quicksale.data.local.OrganizationTally

/**
 * Which way round the Accounts tab is reading the same data.
 *
 * Both views answer "who can we sell to", from the two directions the counter arrives at it: the
 * company, which is what an order actually belongs to and what gets approved or suspended, and the
 * person, which is who is standing at the stand. Neither is a subset of the other, so the tab
 * carries both rather than picking a side.
 */
enum class AccountsView(val label: String) {
    COMPANIES("Companies"),
    PEOPLE("People"),
}

class OrganizationsViewModel(private val repository: OrganizationRepository) : ViewModel() {

    private val _query = MutableStateFlow("")

    private val _statusFilter = MutableStateFlow<OrganizationStatus?>(null)

    private val _view = MutableStateFlow(AccountsView.COMPANIES)

    /** Whether the tab is listing companies or the people inside them. */
    val view: StateFlow<AccountsView> = _view.asStateFlow()

    /** The status the list is narrowed to, or null for every status. */
    val statusFilter: StateFlow<OrganizationStatus?> = _statusFilter.asStateFlow()

    /** Search text and status filter travel together — either one changing re-runs the query. */
    private val criteria = combine(_query, _statusFilter) { query, status ->
        query to status?.slug.orEmpty()
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    val organizations: Flow<PagingData<Organization>> = criteria
        .flatMapLatest { (query, status) ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                repository.pagingSource(query, status)
            }.flow
        }
        .cachedIn(viewModelScope)

    /**
     * The people view's rows, already ordered so equal companies sit together.
     *
     * A separate pager rather than a mapping of [organizations], because the two lists page over
     * different row counts — one company with nine members is one row here and nine rows there.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val people: Flow<PagingData<MemberWithOrganization>> = criteria
        .flatMapLatest { (query, status) ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                repository.memberPagingSource(query, status)
            }.flow
        }
        .cachedIn(viewModelScope)

    // No match count: the list dropped the row that showed it. The number that matters on
    // this screen — how many accounts are waiting for review — is [pendingCount], on its chip.

    /**
     * How many accounts are waiting to be reviewed. Shown on the Pending chip so the queue is
     * visible without switching to it — a registration nobody notices is one nobody approves.
     */
    val pendingCount: StateFlow<Int> = repository.countByStatus(OrganizationStatus.PENDING)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Member/location counts for every organization, so rows don't each run their own query. */
    val tallies: StateFlow<Map<Long, OrganizationTally>> = repository.tallies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setQuery(value: String) { _query.value = value }

    fun setStatusFilter(status: OrganizationStatus?) { _statusFilter.value = status }

    fun setView(value: AccountsView) { _view.value = value }

    companion object {
        fun factory(repository: OrganizationRepository) = viewModelFactory {
            initializer { OrganizationsViewModel(repository) }
        }
    }
}

class OrganizationDetailViewModel(
    private val organizationId: Long,
    private val repository: OrganizationRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    val organization = repository.organization(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members = repository.members(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val locations = repository.locations(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _working = MutableStateFlow(false)
    val working: StateFlow<Boolean> = _working.asStateFlow()

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Moves the account between pending, active, suspended and rejected.
     *
     * Its own route, and its own confirmation in the UI, because this is what sends the shop's
     * approval and rejection mail — a status is not a field you nudge while editing an address.
     * The store answering "already that status" is reported as success, not as an error: two
     * people working the same account must not produce two emails.
     */
    fun setStatus(status: OrganizationStatus) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                if (!settings.isConfigured) {
                    _error.value = "Connect your store in Settings first"
                    return@launch
                }
                val change = WoapApi(settings).setOrganizationStatus(organizationId, status.slug)
                change.organization?.let { repository.saveOrganization(it) }
                _message.value = if (change.changed) {
                    "Account is now ${status.label.lowercase()}"
                } else {
                    "Account was already ${status.label.lowercase()}"
                }
            } catch (e: WooApiException) {
                _error.value = e.message
            } catch (e: Exception) {
                _error.value = e.message ?: "The store couldn't be reached"
            } finally {
                _working.value = false
            }
        }
    }

    fun consumeMessage() { _message.value = null }

    fun consumeError() { _error.value = null }

    companion object {
        fun factory(
            organizationId: Long,
            repository: OrganizationRepository,
            settingsRepository: SettingsRepository,
        ) = viewModelFactory {
            initializer {
                OrganizationDetailViewModel(organizationId, repository, settingsRepository)
            }
        }
    }
}
