package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.settings.DeviceMode
import me.sourov.quicksale.navigation.TopLevelDestination
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Spacing

/**
 * What this handheld is for, and therefore where the app opens and which tabs it has.
 *
 * Shows the resulting tab row under each choice rather than describing it: the consequence of the
 * setting is entirely visual, so saying it in words would be the longer way round.
 */
@Composable
fun DeviceModeSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer.deviceMode }
    val scope = rememberCoroutineScope()
    val current by repository.mode.collectAsStateWithLifecycle(initialValue = null)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "This device",
            subtitle = "Two handhelds, two jobs — this one picks which it opens on.",
        )
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(vertical = Spacing.sm)) {
                DeviceMode.entries.forEach { mode ->
                    ModeRow(
                        mode = mode,
                        selected = mode == current,
                        onSelect = { scope.launch { repository.update(mode) } },
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeRow(
    mode: DeviceMode,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .padding(horizontal = Spacing.lg, vertical = Spacing.md),
        verticalAlignment = Alignment.Top,
    ) {
        RadioButton(selected = selected, onClick = null)
        Spacer(Modifier.width(Spacing.md))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            Text(mode.title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = mode.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = TopLevelDestination.forMode(mode).joinToString(" · ") { it.label },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
