package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.paging.LoadState
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationTally
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The organizations the till can sell to. This is the B2B replacement for the old customer list:
 * an order belongs to an organization first and a person second.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationsScreen(
    query: String,
    onOrganizationClick: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer.organizations }
    val viewModel: OrganizationsViewModel =
        viewModel(factory = OrganizationsViewModel.factory(repository))

    LaunchedEffect(query) { viewModel.setQuery(query) }

    val organizations = viewModel.organizations.collectAsLazyPagingItems()
    val count by viewModel.matchingCount.collectAsStateWithLifecycle()
    val tallies by viewModel.tallies.collectAsStateWithLifecycle()
    val syncState by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()

    // Pull to refresh: the gesture the list already invites, wired to the same sync as everywhere.
    PullToRefreshBox(
        isRefreshing = syncState.isRunning,
        onRefresh = { SyncManager.syncOrganizations(context) },
        modifier = modifier.fillMaxSize(),
    ) {
        Column(Modifier.fillMaxSize()) {
            Text(
                text = "$count ${if (count == 1) "organization" else "organizations"}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    start = Spacing.screen,
                    top = Spacing.md,
                    bottom = Spacing.xs,
                ),
            )

            val refreshing = organizations.loadState.refresh is LoadState.Loading
            when {
                refreshing && organizations.itemCount == 0 -> LoadingState()

                organizations.itemCount == 0 && query.isNotBlank() -> EmptyState(
                    icon = Icons.Filled.Business,
                    title = "No matches",
                    message = "No organization matches \"$query\".",
                )

                organizations.itemCount == 0 -> EmptyState(
                    icon = Icons.Filled.Business,
                    title = "No accounts yet",
                    message = "Sync your store's organizations to start taking orders.",
                    actionLabel = if (syncState.isRunning) null else "Sync now",
                    onAction = { SyncManager.syncOrganizations(context) },
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        bottom = Spacing.screen,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(
                        count = organizations.itemCount,
                        key = organizations.itemKey { it.id },
                    ) { index ->
                        organizations[index]?.let { organization ->
                            OrganizationRow(
                                organization = organization,
                                tally = tallies[organization.id],
                                onClick = { onOrganizationClick(organization.id) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OrganizationRow(
    organization: Organization,
    tally: OrganizationTally?,
    onClick: () -> Unit,
) {
    QuickSaleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(initials = organization.initials)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = organization.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                ) {
                    Tally(
                        icon = Icons.Outlined.Groups,
                        value = tally?.memberCount ?: 0,
                        singular = "member",
                    )
                    Tally(
                        icon = Icons.Outlined.Place,
                        value = tally?.locationCount ?: 0,
                        singular = "location",
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                OrganizationStatusChip(organization.orgStatus)
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconLarge),
            )
        }
    }
}

@Composable
private fun Tally(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    value: Int,
    singular: String,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(14.dp),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = "$value ${if (value == 1) singular else "${singular}s"}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
