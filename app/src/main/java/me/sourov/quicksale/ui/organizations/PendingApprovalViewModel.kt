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
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.OrganizationStatus
import me.sourov.quicksale.data.remote.WoapApi
import me.sourov.quicksale.data.remote.WooApiException
import me.sourov.quicksale.data.settings.SettingsRepository

/** What a decision did, once the store has answered. */
sealed interface ReviewOutcome {
    /** The store applied the change. [alsoActivated] counts the memberships switched on with it. */
    data class Applied(val status: OrganizationStatus, val alsoActivated: Int) : ReviewOutcome

    /** The account already held that status — somebody else worked the queue first. */
    data object AlreadyDecided : ReviewOutcome

    data class Failed(val message: String) : ReviewOutcome
}

/**
 * Reviewing one account that's waiting for approval.
 *
 * The screen is read from the local snapshot and written through the store: nothing is edited in
 * Room directly. A decision is followed by a resync, because the snapshot is the only truthful
 * account of what the store holds — merging a local guess into it is exactly what the API's
 * "replace, don't merge" rule exists to prevent.
 */
class PendingApprovalViewModel(
    private val organizationId: Long,
    repository: OrganizationRepository,
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

    private val _outcome = MutableStateFlow<ReviewOutcome?>(null)
    val outcome: StateFlow<ReviewOutcome?> = _outcome.asStateFlow()

    /**
     * Approves the account and everybody on it.
     *
     * Two writes, in this order: the organization's status — which is what sends the shop's
     * approval mail — and then any membership that isn't active yet, so the person who registered
     * can actually sign in and buy rather than landing on an approved account they're locked out of.
     */
    fun approve() = decide(OrganizationStatus.ACTIVE, activateMembers = true)

    /** Rejects the account. Reversible: the store can move it back to pending or active later. */
    fun reject() = decide(OrganizationStatus.REJECTED, activateMembers = false)

    private fun decide(status: OrganizationStatus, activateMembers: Boolean) {
        if (_working.value) return
        _working.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                if (!settings.isConfigured) {
                    _outcome.value = ReviewOutcome.Failed("Connect your store in Settings first")
                    return@launch
                }
                val api = WoapApi(settings)
                val change = api.setOrganizationStatus(organizationId, status.slug)
                if (!change.changed) {
                    // Asking for the status it already holds is a success and sends nothing, so two
                    // people working the same queue never produce two approval emails.
                    _outcome.value = ReviewOutcome.AlreadyDecided
                    return@launch
                }
                val activated = if (activateMembers) activateInactiveMembers(api) else 0
                _outcome.value = ReviewOutcome.Applied(status, activated)
            } catch (e: WooApiException) {
                _outcome.value = ReviewOutcome.Failed(e.message)
            } catch (e: Exception) {
                _outcome.value = ReviewOutcome.Failed(e.message ?: "The store couldn't be reached")
            } finally {
                _working.value = false
            }
        }
    }

    /**
     * Switches on every membership that isn't already active, and reports how many moved.
     *
     * Only `status` is sent. Permissions are stored as a diff against the role, so echoing back a
     * capability map would pin the member to permissions their role may have moved away from.
     */
    private suspend fun activateInactiveMembers(api: WoapApi): Int {
        val inactive = members.value.filterNot { it.isActive }
        var activated = 0
        inactive.forEach { member ->
            // One member the store refuses must not undo an approval that already succeeded, so
            // each is attempted on its own and the count reports what actually landed.
            runCatching { api.setMemberStatus(organizationId, member.memberId, Member.STATUS_ACTIVE) }
                .onSuccess { activated++ }
        }
        return activated
    }

    fun consumeOutcome() { _outcome.value = null }

    companion object {
        fun factory(
            organizationId: Long,
            repository: OrganizationRepository,
            settingsRepository: SettingsRepository,
        ) = viewModelFactory {
            initializer {
                PendingApprovalViewModel(organizationId, repository, settingsRepository)
            }
        }
    }
}
