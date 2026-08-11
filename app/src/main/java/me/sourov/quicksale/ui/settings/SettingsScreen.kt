package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.ui.components.IconBadge
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Settings as a directory rather than one long page: each row opens the area it names.
 *
 * See [SettingsSection] for the rows and why they're split this way.
 */
@Composable
fun SettingsScreen(
    onSectionClick: (SettingsSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(Spacing.screen),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(SettingsSection.entries.size) { index ->
            val section = SettingsSection.entries[index]
            SettingsRow(section = section, onClick = { onSectionClick(section) })
        }
    }
}

@Composable
private fun SettingsRow(section: SettingsSection, onClick: () -> Unit) {
    QuickSaleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconBadge(
                icon = section.icon,
                size = Sizes.avatarSmall,
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = section.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(Sizes.iconLarge),
            )
        }
    }
}
