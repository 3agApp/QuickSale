package me.sourov.quicksale.ui.organizations

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import me.sourov.quicksale.data.local.OrganizationStatus
import me.sourov.quicksale.ui.components.StatusChip

/**
 * The organization's trading state, shown wherever an organization appears.
 *
 * Only `active` is a green light. Everything else is a refusal the counter should see *before*
 * ringing anything up — the store enforces it at order creation regardless, and finding out then
 * means a cart's worth of wasted work.
 */
@Composable
fun OrganizationStatusChip(
    status: OrganizationStatus,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val (container, content, icon) = when (status) {
        OrganizationStatus.ACTIVE -> Triple(
            colors.tertiaryContainer,
            colors.onTertiaryContainer,
            Icons.Filled.CheckCircle,
        )

        OrganizationStatus.PENDING -> Triple(
            colors.secondaryContainer,
            colors.onSecondaryContainer,
            Icons.Outlined.HourglassEmpty,
        )

        OrganizationStatus.SUSPENDED, OrganizationStatus.REJECTED -> Triple(
            colors.errorContainer,
            colors.onErrorContainer,
            Icons.Filled.Block,
        )

        OrganizationStatus.UNKNOWN -> Triple(
            colors.surfaceVariant,
            colors.onSurfaceVariant,
            Icons.AutoMirrored.Outlined.HelpOutline,
        )
    }

    StatusChip(
        label = status.label,
        containerColor = container,
        contentColor = content,
        icon = icon,
        modifier = modifier,
    )
}
