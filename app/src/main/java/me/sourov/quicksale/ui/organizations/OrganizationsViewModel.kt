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
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.OrganizationTally

class OrganizationsViewModel(private val repository: OrganizationRepository) : ViewModel() {

    private val _query = MutableStateFlow("")

    @OptIn(ExperimentalCoroutinesApi::class)
    val organizations: Flow<PagingData<Organization>> = _query
        .flatMapLatest { q ->
            Pager(PagingConfig(pageSize = 30, enablePlaceholders = false)) {
                repository.pagingSource(q)
            }.flow
        }
        .cachedIn(viewModelScope)

    @OptIn(ExperimentalCoroutinesApi::class)
    val matchingCount: StateFlow<Int> = _query
        .flatMapLatest { q -> repository.countMatching(q) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    /** Member/location counts for every organization, so rows don't each run their own query. */
    val tallies: StateFlow<Map<Long, OrganizationTally>> = repository.tallies()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun setQuery(value: String) { _query.value = value }

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
