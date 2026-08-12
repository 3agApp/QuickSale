package me.sourov.quicksale.ui.onboarding

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
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.data.settings.DeviceMode
import me.sourov.quicksale.ui.components.IconBadge
import me.sourov.quicksale.ui.components.QuickSaleBrandLockup
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The first thing a fresh install asks: what is this device for.
 *
 * Two handhelds work the same fair doing opposite jobs, and answering this once is what lets each
 * of them open straight onto its own job instead of onto a menu. Nothing here is permanent — the
 * same choice sits at the top of Settings.
 */
@Composable
fun DeviceModeScreen(
    onSelect: (DeviceMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
    ) {
        Spacer(Modifier.height(Spacing.xxl))
        QuickSaleBrandLockup()

        Spacer(Modifier.height(Spacing.xl))
        Text(
            text = "What is this device for?",
            style = MaterialTheme.typography.headlineMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Spacer(Modifier.height(Spacing.sm))
        Text(
            text = "It decides where the app opens and which tabs you get. You can change it " +
                "any time in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(Spacing.xl))
        Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
            ModeCard(
                mode = DeviceMode.SELL,
                icon = Icons.Filled.PointOfSale,
                onSelect = onSelect,
            )
            ModeCard(
                mode = DeviceMode.LABEL,
                icon = Icons.Filled.Print,
                onSelect = onSelect,
            )
            ModeCard(
                mode = DeviceMode.BOTH,
                icon = Icons.Filled.Devices,
                onSelect = onSelect,
            )
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ModeCard(
    mode: DeviceMode,
    icon: ImageVector,
    onSelect: (DeviceMode) -> Unit,
) {
    QuickSaleCard(modifier = Modifier.clickable { onSelect(mode) }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(icon = icon, size = Sizes.avatar)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(mode.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = mode.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}
