package me.sourov.quicksale.ui.organizations

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationRepository

/**
 * Why an order can't be started for this person, or null when it can.
 *
 * The same two refusals the checkout enforces, asked here instead — at the point where the operator
 * is deciding whether to walk someone to the till, rather than after a basket has been built.
 */
data class OrderBlocker(val reason: String)

/**
 * Whether this page has a person to show, in the three states it actually has.
 *
 * "Not read yet" and "no longer there" are different answers, and only one of them is worth putting
 * on screen — the other is a page that briefly accuses itself of not finding somebody it is about
 * to display. The distinction also carries the removal: a person who *was* here and now isn't has
 * just been taken off the account, and the page should get out of the way rather than sit there
 * describing a hole.
 */
sealed interface MemberState {
    data object Loading : MemberState

    /** Read, and there is no such membership — removed, or an id that never matched. */
    data object Missing : MemberState

    data class Found(val member: Member) : MemberState
}

/**
 * One person on an account: who they are, what the store lets them do, and the company they buy for.
 *
 * A person is the thing an order is actually stamped with, so they had earned a page of their own —
 * tapping someone in the people list used to land on their company, which answered a question
 * nobody had asked. The company is still one tap away, as a card rather than a destination.
 */
class MemberDetailViewModel(
    private val organizationId: Long,
    private val userId: Long,
    repository: OrganizationRepository,
) : ViewModel() {

    val state: StateFlow<MemberState> = repository.member(organizationId, userId)
        .map { found -> found?.let(MemberState::Found) ?: MemberState.Missing }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MemberState.Loading)

    val member: StateFlow<Member?> = state
        .map { (it as? MemberState.Found)?.member }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val organization: StateFlow<Organization?> = repository.organization(organizationId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Only the locations this person may actually deliver to — not every one the company has. */
    @OptIn(ExperimentalCoroutinesApi::class)
    val locations: StateFlow<List<OrgLocation>> = member
        .flatMapLatest { who -> if (who == null) flowOf(emptyList()) else repository.locationsFor(who) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Whether an order may be started for this person.
     *
     * Checked against the store's own resolved answers rather than re-derived from role or status:
     * the server re-checks at order creation anyway, and disagreeing with it here would mean
     * offering a button that fails at the end of a basket.
     */
    val blocker: StateFlow<OrderBlocker?> = combine(member, organization) { who, org ->
        when {
            // Still loading is not a refusal; the button waits rather than accusing anyone.
            who == null || org == null -> null
            !org.orgStatus.canTrade ->
                OrderBlocker("${org.name} is ${org.orgStatus.label.lowercase()} and can't order")
            !who.canPlaceOrders ->
                OrderBlocker("${who.name} isn't allowed to place orders")
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    companion object {
        fun factory(
            organizationId: Long,
            userId: Long,
            repository: OrganizationRepository,
        ) = viewModelFactory {
            initializer { MemberDetailViewModel(organizationId, userId, repository) }
        }
    }
}
