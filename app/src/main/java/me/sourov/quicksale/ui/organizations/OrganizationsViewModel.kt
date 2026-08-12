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
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.OrganizationStatus
import me.sourov.quicksale.data.local.OrganizationTally

class OrganizationsViewModel(private val repository: OrganizationRepository) : ViewModel() {

    private val _query = MutableStateFlow("")

    private val _statusFilter = MutableStateFlow<OrganizationStatus?>(null)

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

    companion object {
        fun factory(repository: OrganizationRepository) = viewModelFactory {
            initializer { OrganizationsViewModel(repository) }
        }
    }
}

class OrganizationDetailViewModel(
    organizationId: Long,
    repository: OrganizationRepository,
) : ViewModel() {

    val organization = repository.organization(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val members = repository.members(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val locations = repository.locations(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    companion object {
        fun factory(organizationId: Long, repository: OrganizationRepository) = viewModelFactory {
            initializer { OrganizationDetailViewModel(organizationId, repository) }
        }
    }
}
