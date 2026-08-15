package me.sourov.quicksale.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.sync.AutoSyncSettings
import me.sourov.quicksale.data.sync.SyncInterval
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.components.SpinningSyncIcon
import me.sourov.quicksale.ui.components.SyncRowDivider
import me.sourov.quicksale.ui.components.SyncTargetRow
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Sync, in Settings: run it now, and decide how often it should run itself.
 *
 * Strictly one hop — the website onto this device. Pulling the *website* from Kontor lives on its
 * own page ([KontorSettingsSection]) and is never folded into these buttons: they are different
 * jobs on different machines with different failure modes, and a single control that quietly did
 * both would leave the counter unable to say which half went wrong.
 *
 * Automatic sync is cheap by design — every page of the organization snapshot carries an ETag, so
 * a poll against an unchanged store transfers nothing and rewrites nothing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SyncSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val scope = rememberCoroutineScope()

    val autoSync by container.autoSync.settings
        .collectAsStateWithLifecycle(initialValue = AutoSyncSettings())
    val productCount by container.products.count().collectAsStateWithLifecycle(initialValue = 0)
    val organizationCount by container.organizations.count()
        .collectAsStateWithLifecycle(initialValue = 0)
    val productsSync by SyncManager.state(SyncTarget.Products).collectAsStateWithLifecycle()
    val organizationsSync by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()
    val anySyncing by SyncManager.anyRunning.collectAsStateWithLifecycle()
    val productsLastSync by container.syncMeta.lastSyncMillis(SyncTarget.Products)
        .collectAsStateWithLifecycle(initialValue = 0L)
    val organizationsLastSync by container.syncMeta.lastSyncMillis(SyncTarget.Organizations)
        .collectAsStateWithLifecycle(initialValue = 0L)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Sync",
            subtitle = "Keep this device's copy of the store current",
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

        Spacer(Modifier.height(Spacing.md))
        Button(
            onClick = { SyncManager.syncAll(context) },
            enabled = !anySyncing,
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
        ) {
            SpinningSyncIcon(
                syncing = anySyncing,
                tint = MaterialTheme.colorScheme.onPrimary,
                size = 18.dp,
            )
            Spacer(Modifier.size(Spacing.sm))
            Text(if (anySyncing) "Syncing…" else "Sync everything now")
        }

        Spacer(Modifier.height(Spacing.xl))
        SettingsSwitchRow(
            title = "Sync automatically",
            subtitle = "While QuickSale is open",
            checked = autoSync.enabled,
            onCheckedChange = { enabled ->
                scope.launch { container.autoSync.setEnabled(enabled) }
            },
        )

        AnimatedVisibility(
            visible = autoSync.enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.md))
                var expanded by remember { mutableStateOf(false) }
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = it },
                ) {
                    OutlinedTextField(
                        value = autoSync.interval.label,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("How often") },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                        },
                        modifier = Modifier
                            .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                            .fillMaxWidth(),
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false },
                    ) {
                        SyncInterval.entries.forEach { interval ->
                            DropdownMenuItem(
                                text = { Text(interval.label) },
                                onClick = {
                                    expanded = false
                                    scope.launch { container.autoSync.setInterval(interval) }
                                },
                            )
                        }
                    }
                }

                Spacer(Modifier.height(Spacing.md))
                SettingsSwitchRow(
                    title = "Catch up on launch",
                    subtitle = "Sync at start-up when the local copy is already older than that",
                    checked = autoSync.syncOnLaunch,
                    onCheckedChange = { enabled ->
                        scope.launch { container.autoSync.setSyncOnLaunch(enabled) }
                    },
                )
            }
        }
    }
}

/** A labelled switch row, the shape every on/off setting in the app uses. */
@Composable
fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.size(Spacing.md))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
