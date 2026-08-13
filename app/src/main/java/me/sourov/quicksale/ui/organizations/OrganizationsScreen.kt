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
import androidx.compose.material.icons.outlined.RateReview
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.sourov.quicksale.appContainer
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
 * [onOrganizationClick] receives the whole organization — the caller routes on its status.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationsScreen(
    query: String,
    onOrganizationClick: (organization: Organization) -> Unit,
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
            StatusFilterRow(
                selected = statusFilter,
                pendingCount = pendingCount,
                onSelect = viewModel::setStatusFilter,
            )

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
                    title = "Nothing ${statusFilter?.label?.lowercase()}",
                    message = if (statusFilter == OrganizationStatus.PENDING) {
                        "No accounts are waiting for approval."
                    } else {
                        "No account is ${statusFilter?.label?.lowercase()} right now."
                    },
                )

                organizations.itemCount == 0 -> EmptyState(
                    icon = Icons.Filled.Business,
                    title = "No accounts yet",
                    message = "Sync your store's organizations to start taking orders.",
                    actionLabel = if (syncState.isRunning) null else "Sync now",
                    onAction = { SyncManager.syncOrganizations(context) },
                )

                // Dividers rather than cards with gaps, matching the catalog.
                else -> LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.screen),
                ) {
                    items(
                        count = organizations.itemCount,
                        key = organizations.itemKey { it.id },
                    ) { index ->
                        organizations[index]?.let { organization ->
                            OrganizationRow(
                                organization = organization,
                                tally = tallies[organization.id],
                                onClick = { onOrganizationClick(organization) },
                            )
                            HorizontalDivider()
                        }
                    }
                }
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
