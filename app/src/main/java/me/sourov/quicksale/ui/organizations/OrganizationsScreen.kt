package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.MemberWithOrganization
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationStatus
import me.sourov.quicksale.data.local.OrganizationTally
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The organizations the till can sell to. This is the B2B replacement for the old customer list:
 * an order belongs to an organization first and a person second.
 *
 * The status filter doubles as the review queue: **Pending** is the list of accounts waiting for
 * somebody to approve them, which is the one thing on this screen that isn't about selling.
 *
 * The count row that used to sit under the filters is gone: it spent 56dp on a number and a button,
 * and the button — *New customer* — is a bar action now. The number it showed disagreed with the
 * list whenever a status filter was on anyway, and the count that matters here, how many accounts
 * are waiting for review, is on the Pending chip.
 *
 * The tab reads the same accounts two ways. **Companies** is the list an order actually belongs to,
 * and the one that gets approved or suspended. **People** turns it inside out and lists everyone
 * under the company they buy for — because at the stand you are as likely to be given a person's
 * name as a shop's, and one search box answers both.
 *
 * [onAccountOpen] receives the account's id and status — the caller routes on the status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationsScreen(
    query: String,
    /**
     * Opens an account. Taken as id and status rather than the whole row because both views lead
     * here — a person's row knows which company they belong to, but never holds one.
     */
    onAccountOpen: (organizationId: Long, status: OrganizationStatus) -> Unit,
    /** Opens one person's page — the People view's rows lead here rather than to their company. */
    onPersonOpen: (organizationId: Long, userId: Long) -> Unit,
    creating: Boolean,
    onCreatingChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer.organizations }
    val viewModel: OrganizationsViewModel =
        viewModel(factory = OrganizationsViewModel.factory(repository))

    LaunchedEffect(query) { viewModel.setQuery(query) }

    val organizations = viewModel.organizations.collectAsLazyPagingItems()
    val people = viewModel.people.collectAsLazyPagingItems()
    val view by viewModel.view.collectAsStateWithLifecycle()
    val tallies by viewModel.tallies.collectAsStateWithLifecycle()
    val statusFilter by viewModel.statusFilter.collectAsStateWithLifecycle()
    val pendingCount by viewModel.pendingCount.collectAsStateWithLifecycle()
    val syncState by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()

    // Pull to refresh: the gesture the list already invites, wired to the same sync as everywhere.
    PullToRefreshBox(
        isRefreshing = syncState.isRunning,
        onRefresh = { SyncManager.syncOrganizations(context) },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            AccountsViewToggle(selected = view, onSelect = viewModel::setView)

            StatusFilterRow(
                selected = statusFilter,
                pendingCount = pendingCount,
                onSelect = viewModel::setStatusFilter,
            )

            when (view) {
                AccountsView.PEOPLE -> PeopleList(
                    people = people,
                    query = query,
                    statusFilter = statusFilter,
                    onAccountOpen = onAccountOpen,
                    onPersonOpen = onPersonOpen,
                )

                AccountsView.COMPANIES -> CompanyList(
                    organizations = organizations,
                    tallies = tallies,
                    query = query,
                    statusFilter = statusFilter,
                    syncing = syncState.isRunning,
                    onSync = { SyncManager.syncOrganizations(context) },
                    onAccountOpen = onAccountOpen,
                )
            }
        }
    }

    if (creating) {
        NewCustomerSheet(
            onDismiss = { onCreatingChange(false) },
            // The sheet writes the new company and person into the local copy itself, so they
            // are in this list before the sheet has finished closing.
            onCreated = { onCreatingChange(false) },
        )
    }
}

/** The accounts themselves — what an order belongs to, and what gets approved or suspended. */
@Composable
private fun CompanyList(
    organizations: LazyPagingItems<Organization>,
    tallies: Map<Long, OrganizationTally>,
    query: String,
    statusFilter: OrganizationStatus?,
    syncing: Boolean,
    onSync: () -> Unit,
    onAccountOpen: (organizationId: Long, status: OrganizationStatus) -> Unit,
) {
    val refreshing = organizations.loadState.refresh is LoadState.Loading
    when {
        refreshing && organizations.itemCount == 0 -> LoadingState()

        organizations.itemCount == 0 && query.isNotBlank() -> EmptyState(
            icon = Icons.Filled.Business,
            title = "No matches",
            message = "No organization matches \"$query\".",
        )

        organizations.itemCount == 0 && statusFilter != null -> EmptyState(
            icon = Icons.Filled.Business,
            title = "Nothing ${statusFilter.label.lowercase()}",
            message = if (statusFilter == OrganizationStatus.PENDING) {
                "No accounts are waiting for approval."
            } else {
                "No account is ${statusFilter.label.lowercase()} right now."
            },
        )

        organizations.itemCount == 0 -> EmptyState(
            icon = Icons.Filled.Business,
            title = "No accounts yet",
            message = "Sync your store's organizations to start taking orders.",
            actionLabel = if (syncing) null else "Sync now",
            onAction = onSync,
        )

        // Dividers rather than cards with gaps, matching the catalog.
        else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.screen)) {
            items(
                count = organizations.itemCount,
                key = organizations.itemKey { it.id },
            ) { index ->
                organizations[index]?.let { organization ->
                    OrganizationRow(
                        organization = organization,
                        tally = tallies[organization.id],
                        onClick = { onAccountOpen(organization.id, organization.orgStatus) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

/** Which way round the tab is reading: the companies, or the people inside them. */
@Composable
private fun AccountsViewToggle(
    selected: AccountsView,
    onSelect: (AccountsView) -> Unit,
) {
    SingleChoiceSegmentedButtonRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
    ) {
        AccountsView.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index, AccountsView.entries.size),
            ) {
                Text(option.label, maxLines = 1)
            }
        }
    }
}

/**
 * Every person, under the company they buy for.
 *
 * The company header repeats down the list rather than being drawn once at the top of a section,
 * because paging hands rows over in pages and a header that lived outside the row would be lost
 * the moment its page scrolled away. Comparing against the row before is enough: the query already
 * orders by company, so equal names are always adjacent.
 */
@Composable
private fun PeopleList(
    people: LazyPagingItems<MemberWithOrganization>,
    query: String,
    statusFilter: OrganizationStatus?,
    onAccountOpen: (organizationId: Long, status: OrganizationStatus) -> Unit,
    onPersonOpen: (organizationId: Long, userId: Long) -> Unit,
) {
    val refreshing = people.loadState.refresh is LoadState.Loading
    when {
        refreshing && people.itemCount == 0 -> LoadingState()

        people.itemCount == 0 && query.isNotBlank() -> EmptyState(
            icon = Icons.Outlined.Person,
            title = "No matches",
            message = "Nobody matches \"$query\" — by their own name, or their company's.",
        )

        people.itemCount == 0 && statusFilter != null -> EmptyState(
            icon = Icons.Outlined.Person,
            title = "Nobody here",
            message = "No account is ${statusFilter.label.lowercase()} right now.",
        )

        people.itemCount == 0 -> EmptyState(
            icon = Icons.Outlined.Person,
            title = "No people yet",
            message = "Sync your store's organizations to see who can order.",
        )

        else -> LazyColumn(contentPadding = PaddingValues(bottom = Spacing.screen)) {
            items(
                count = people.itemCount,
                key = people.itemKey { it.member.memberId },
            ) { index ->
                val row = people[index] ?: return@items
                // peek rather than get: asking for the previous row must not drag its page back in.
                val previous = if (index > 0) people.peek(index - 1) else null
                if (row.organizationName != previous?.organizationName) {
                    CompanyHeader(
                        name = row.organizationName,
                        city = row.organizationCity,
                        status = OrganizationStatus.fromSlug(row.organizationStatus),
                        onClick = {
                            onAccountOpen(
                                row.member.organizationId,
                                OrganizationStatus.fromSlug(row.organizationStatus),
                            )
                        },
                    )
                }
                PersonRow(
                    member = row.member,
                    onClick = { onPersonOpen(row.member.organizationId, row.member.userId) },
                )
                HorizontalDivider()
            }
        }
    }
}

/** The company a run of people belong to. Tappable, because the account itself is often the answer. */
@Composable
private fun CompanyHeader(
    name: String,
    city: String,
    status: OrganizationStatus,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(
                start = Spacing.screen,
                end = Spacing.screen,
                top = Spacing.md,
                bottom = Spacing.xs,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = name.ifBlank { "(no name)" },
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (city.isNotBlank()) {
                Text(
                    text = city,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        // Only when it isn't the ordinary case: an active account needs no badge to say so.
        if (status != OrganizationStatus.ACTIVE) {
            Spacer(Modifier.width(Spacing.sm))
            OrganizationStatusChip(status)
        }
    }
}

/** One person on an account. */
@Composable
private fun PersonRow(member: Member, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(initials = member.initials, size = Sizes.avatar)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = member.name.ifBlank { member.email.ifBlank { "(no name)" } },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (member.email.isNotBlank()) {
                Text(
                    text = member.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.width(Spacing.sm))
        // Two different facts, and both matter at the counter: an admin can act for the whole
        // company, and someone the store won't let order is a dead end worth seeing before you
        // walk them through a basket.
        Column(horizontalAlignment = Alignment.End) {
            if (member.isAdmin) {
                Text(
                    text = member.roleLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (!member.canPlaceOrders) {
                Text(
                    text = "Can't order",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

/**
 * Narrows the list to one lifecycle state. **Pending** carries its count because it is a queue:
 * an account waiting for approval is work, and work nobody can see is work nobody does.
 */
@Composable
private fun StatusFilterRow(
    selected: OrganizationStatus?,
    pendingCount: Int,
    onSelect: (OrganizationStatus?) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        FilterChip(
            selected = selected == null,
            onClick = { onSelect(null) },
            label = { Text("All") },
        )
        FILTERABLE_STATUSES.forEach { status ->
            val isPending = status == OrganizationStatus.PENDING
            FilterChip(
                selected = selected == status,
                onClick = { onSelect(status) },
                label = {
                    Text(
                        if (isPending && pendingCount > 0) {
                            "${status.label} ($pendingCount)"
                        } else {
                            status.label
                        },
                    )
                },
            )
        }
    }
}

/** [OrganizationStatus.UNKNOWN] is not a status the store can hold, so it isn't offered. */
private val FILTERABLE_STATUSES = listOf(
    OrganizationStatus.PENDING,
    OrganizationStatus.ACTIVE,
    OrganizationStatus.SUSPENDED,
    OrganizationStatus.REJECTED,
)

/**
 * One account, in two lines.
 *
 * The tallies used to have a line of their own and the status chip another below it, which made a
 * ~110dp row out of four short facts. They share the second line now: the tallies read as text and
 * the chip sits at the end of it, where the chevron's column used to be.
 */
@Composable
private fun OrganizationRow(
    organization: Organization,
    tally: OrganizationTally?,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(initials = organization.initials)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = organization.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                // A pending account opens its review rather than its detail page, so it keeps a
                // glyph saying so. Everything else doesn't: the whole row is tappable, and a
                // chevron on each of nine rows was a column of space saying nothing.
                if (organization.orgStatus == OrganizationStatus.PENDING) {
                    Spacer(Modifier.width(Spacing.sm))
                    Icon(
                        imageVector = Icons.Outlined.RateReview,
                        contentDescription = "Review ${organization.name}",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(Sizes.icon),
                    )
                }
            }
            Spacer(Modifier.height(Spacing.xs))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = tallySummary(tally),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                OrganizationStatusChip(organization.orgStatus)
            }
        }
    }
}

/** "3 members · 2 locations", the two facts that used to be a row of iconed tallies. */
private fun tallySummary(tally: OrganizationTally?): String {
    fun count(value: Int, singular: String, plural: String) =
        "$value ${if (value == 1) singular else plural}"
    return listOf(
        count(tally?.memberCount ?: 0, "member", "members"),
        count(tally?.locationCount ?: 0, "location", "locations"),
    ).joinToString(" · ")
}
