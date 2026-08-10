package me.sourov.quicksale.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.settings.StoreSettings
import me.sourov.quicksale.data.sync.AutoSyncSettings
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.navigation.TopLevelDestination
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.components.SpinningSyncIcon
import me.sourov.quicksale.ui.components.SyncRowDivider
import me.sourov.quicksale.ui.components.SyncTargetRow
import me.sourov.quicksale.ui.components.lastSyncLabel
import me.sourov.quicksale.ui.theme.BrandGradient
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The till's home screen: is the store connected, is the local copy fresh, how much of it is
 * there, and where do you go next.
 */
@Composable
fun HomeScreen(
    onNavigate: (TopLevelDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }

    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = StoreSettings())
    val productCount by container.products.count().collectAsStateWithLifecycle(initialValue = 0)
    val organizationCount by container.organizations.count().collectAsStateWithLifecycle(initialValue = 0)
    val memberCount by container.organizations.memberCount().collectAsStateWithLifecycle(initialValue = 0)
    val autoSync by container.autoSync.settings
        .collectAsStateWithLifecycle(initialValue = AutoSyncSettings())

    val productsSync by SyncManager.state(SyncTarget.Products).collectAsStateWithLifecycle()
    val organizationsSync by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()
    val anySyncing by SyncManager.anyRunning.collectAsStateWithLifecycle()

    val productsLastSync by container.syncMeta.lastSyncMillis(SyncTarget.Products)
        .collectAsStateWithLifecycle(initialValue = 0L)
    val organizationsLastSync by container.syncMeta.lastSyncMillis(SyncTarget.Organizations)
        .collectAsStateWithLifecycle(initialValue = 0L)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
    ) {
        ConnectionHero(
            settings = settings,
            onClick = { onNavigate(TopLevelDestination.SETTINGS) },
        )

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(
            title = "Store data",
            subtitle = autoSyncSummary(autoSync),
            trailing = {
                Button(onClick = { SyncManager.syncAll(context) }, enabled = !anySyncing) {
                    SpinningSyncIcon(
                        syncing = anySyncing,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        size = 18.dp,
                    )
                    Spacer(Modifier.size(Spacing.sm))
                    Text(if (anySyncing) "Syncing" else "Sync all")
                }
            },
        )
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(vertical = Spacing.xs)) {
                SyncTargetRow(
                    target = SyncTarget.Products,
                    count = productCount,
                    state = productsSync,
                    lastSyncMillis = productsLastSync,
                    onSync = { SyncManager.syncProducts(context) },
                )
                SyncRowDivider()
                SyncTargetRow(
                    target = SyncTarget.Organizations,
                    count = organizationCount,
                    state = organizationsSync,
                    lastSyncMillis = organizationsLastSync,
                    onSync = { SyncManager.syncOrganizations(context) },
                )
            }
        }

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(title = "On this device")
        Spacer(Modifier.height(Spacing.sectionGap))
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
            StatTile(
                value = productCount,
                label = "Products",
                icon = Icons.Filled.Inventory2,
                onClick = { onNavigate(TopLevelDestination.PRODUCTS) },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = organizationCount,
                label = "Accounts",
                icon = Icons.Filled.Business,
                onClick = { onNavigate(TopLevelDestination.ORGANIZATIONS) },
                modifier = Modifier.weight(1f),
            )
            StatTile(
                value = memberCount,
                label = "Members",
                icon = Icons.Filled.Groups,
                onClick = { onNavigate(TopLevelDestination.ORGANIZATIONS) },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(title = "Start an order")
        Spacer(Modifier.height(Spacing.sectionGap))
        StartOrderCard(
            organizationCount = organizationCount,
            lastSyncMillis = organizationsLastSync,
            onBrowse = { onNavigate(TopLevelDestination.ORGANIZATIONS) },
            onSync = { SyncManager.syncOrganizations(context) },
            syncing = organizationsSync.isRunning,
        )

        Spacer(Modifier.height(Spacing.lg))
    }
}

private fun autoSyncSummary(settings: AutoSyncSettings): String =
    if (settings.enabled) {
        "Syncing automatically ${settings.interval.label.replaceFirstChar { it.lowercase() }}"
    } else {
        "Automatic sync is off"
    }

/** The connection banner. Doubles as the way into Settings when nothing is set up yet. */
@Composable
private fun ConnectionHero(
    settings: StoreSettings,
    onClick: () -> Unit,
) {
    val configured = settings.isConfigured
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(BrandGradient)
            .clickable(onClick = onClick)
            .padding(Spacing.xl),
    ) {
        Column {
            Text(
                text = "B2B point of sale",
                style = MaterialTheme.typography.labelLarge,
                color = Color.White.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = if (configured) "You're all set" else "Let's get set up",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
            )
            Spacer(Modifier.height(Spacing.md))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = if (configured) Icons.Filled.CheckCircle else Icons.Outlined.Info,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(Sizes.icon),
                )
                Spacer(Modifier.size(Spacing.sm))
                Text(
                    text = if (configured) {
                        "Connected · ${settings.siteUrl.toDisplayHost()}"
                    } else {
                        "Tap to connect your store"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.92f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                Spacer(Modifier.size(Spacing.sm))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = Color.White.copy(alpha = 0.92f),
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

private fun String.toDisplayHost(): String =
    trim()
        .removePrefix("https://")
        .removePrefix("http://")
        .trimEnd('/')

/** A count with a label — the fastest read of what the device is holding. */
@Composable
private fun StatTile(
    value: Int,
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    QuickSaleCard(modifier = modifier.clickable(onClick = onClick)) {
        Column(
            modifier = Modifier.padding(Spacing.md),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(Sizes.icon),
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * The counter's actual starting point. When no organizations have been synced there is nothing to
 * browse, so the card offers the sync instead of sending the operator to an empty list.
 */
@Composable
private fun StartOrderCard(
    organizationCount: Int,
    lastSyncMillis: Long,
    onBrowse: () -> Unit,
    onSync: () -> Unit,
    syncing: Boolean,
) {
    val hasAccounts = organizationCount > 0
    QuickSaleCard(
        containerColor = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Column(Modifier.padding(Spacing.lg)) {
            Text(
                text = if (hasAccounts) "Find the account" else "No accounts yet",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
            Spacer(Modifier.height(Spacing.xs))
            Text(
                text = if (hasAccounts) {
                    "Pick an organization, then the member you're serving. Accounts ${lastSyncLabel(lastSyncMillis)}."
                } else {
                    "Sync your store's organizations to start taking orders at the counter."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
            )
            Spacer(Modifier.height(Spacing.lg))
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (hasAccounts) {
                    Button(onClick = onBrowse) {
                        Icon(Icons.Filled.Business, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(Spacing.sm))
                        Text("Browse accounts")
                    }
                } else {
                    Button(onClick = onSync, enabled = !syncing) {
                        SpinningSyncIcon(
                            syncing = syncing,
                            tint = MaterialTheme.colorScheme.onPrimary,
                            size = 18.dp,
                        )
                        Spacer(Modifier.size(Spacing.sm))
                        Text(if (syncing) "Syncing…" else "Sync accounts")
                    }
                }
            }
        }
    }
}
