package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.IconBadge
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.components.SyncIconButton
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * One organization: whether it can trade, who may buy for it, and where its deliveries go.
 *
 * Choosing a member is what starts an order — the member's WordPress user id becomes the order's
 * customer, which is what puts the order in their *My orders* and their organization's history.
 */
@Composable
fun OrganizationDetailScreen(
    organizationId: Long,
    onStartOrder: (memberUserId: Long) -> Unit,
    onViewOrders: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer.organizations }
    val viewModel: OrganizationDetailViewModel = viewModel(
        factory = OrganizationDetailViewModel.factory(organizationId, repository),
    )

    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val syncState by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()

    val current = organization
    if (current == null) {
        // Either still loading, or the last snapshot dropped it — a snapshot answers deletions by
        // omission, so an organization that vanished really is gone from the store.
        if (syncState.isRunning) LoadingState(modifier) else {
            EmptyState(
                modifier = modifier,
                icon = Icons.Filled.Business,
                title = "Account not found",
                message = "It may have been removed from your store. Sync to check.",
                actionLabel = "Sync accounts",
                onAction = { SyncManager.syncOrganizations(context) },
            )
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
    ) {
        OrganizationHeader(
            organization = current,
            syncing = syncState.isRunning,
            onSync = { SyncManager.syncOrganizations(context) },
        )

        Spacer(Modifier.height(Spacing.xl))
        OrdersRow(onClick = onViewOrders)

        if (current.billingFormatted.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xl))
            SectionHeader(
                title = "Billing address",
                subtitle = "Applied by the store to every order",
            )
            Spacer(Modifier.height(Spacing.sectionGap))
            QuickSaleCard {
                Text(
                    // Shown exactly as WooCommerce formats it for the country — postcode before
                    // the city in Germany, after it in the US, and so on for every variant.
                    text = current.billingFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        }

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(
            title = "Members",
            subtitle = if (members.isEmpty()) null else "Tap whoever you're serving",
        )
        Spacer(Modifier.height(Spacing.sectionGap))
        if (members.isEmpty()) {
            QuickSaleCard {
                Text(
                    text = "No members on this account.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                members.forEach { member ->
                    MemberRow(
                        member = member,
                        organizationCanTrade = current.orgStatus.canTrade,
                        onClick = { onStartOrder(member.userId) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(
            title = "Branches",
            subtitle = if (current.allowCustomShipping) {
                "An order may also be sent to a typed address"
            } else {
                "Orders must ship to one of these"
            },
        )
        Spacer(Modifier.height(Spacing.sectionGap))
        if (locations.isEmpty()) {
            QuickSaleCard {
                Text(
                    text = "No saved branches.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                locations.forEach { LocationRow(location = it) }
            }
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun OrganizationHeader(
    organization: Organization,
    syncing: Boolean,
    onSync: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Monogram(initials = organization.initials, size = 48.dp)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = organization.name,
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.height(Spacing.sm))
            OrganizationStatusChip(organization.orgStatus)
        }
        SyncIconButton(
            syncing = syncing,
            onClick = onSync,
            contentDescription = "Sync accounts",
        )
    }

    if (!organization.orgStatus.canTrade) {
        Spacer(Modifier.height(Spacing.lg))
        QuickSaleCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Block,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.size(Sizes.iconLarge),
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = "This account is ${organization.orgStatus.label.lowercase()}, so the " +
                        "store will refuse its orders.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
            }
        }
    }

    if (organization.email.isNotBlank()) {
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Outlined.MailOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = organization.email,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Entry point into this organization's order history — every member's orders, newest first. */
@Composable
private fun OrdersRow(onClick: () -> Unit) {
    QuickSaleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = Icons.AutoMirrored.Outlined.ReceiptLong, size = Sizes.avatarSmall)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text("Orders", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "View and edit this account's orders",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

