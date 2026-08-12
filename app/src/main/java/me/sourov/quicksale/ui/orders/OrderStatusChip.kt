package me.sourov.quicksale.ui.orders

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.outlined.HourglassEmpty
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import me.sourov.quicksale.ui.components.StatusChip

private data class OrderStatusStyle(
    val label: String,
    val container: Color,
    val content: Color,
    val icon: ImageVector,
)

/**
 * A WooCommerce order status slug, shown wherever an order appears — the list row and the
 * detail header. Only `pending`/`processing` are ever editable here; every other status is shown
 * for information, not as something the counter is expected to act on.
 */
@Composable
fun OrderStatusChip(status: String, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val style = when (status) {
        "completed" -> OrderStatusStyle(
            "Completed", colors.tertiaryContainer, colors.onTertiaryContainer, Icons.Filled.CheckCircle,
        )
        "processing" -> OrderStatusStyle(
            "Processing", colors.primaryContainer, colors.onPrimaryContainer, Icons.Filled.Sync,
        )
        "pending" -> OrderStatusStyle(
            "Pending payment", colors.secondaryContainer, colors.onSecondaryContainer, Icons.Outlined.HourglassEmpty,
        )
        "on-hold" -> OrderStatusStyle(
            "On hold", colors.secondaryContainer, colors.onSecondaryContainer, Icons.Outlined.HourglassEmpty,
        )
        "cancelled", "failed", "refunded" -> OrderStatusStyle(
            status.replaceFirstChar { it.uppercase() }, colors.errorContainer, colors.onErrorContainer, Icons.Filled.Block,
        )
        else -> OrderStatusStyle(
            status.ifBlank { "Unknown" }.replaceFirstChar { it.uppercase() },
            colors.surfaceVariant,
            colors.onSurfaceVariant,
            Icons.AutoMirrored.Outlined.HelpOutline,
        )
    }
    StatusChip(
        label = style.label,
        containerColor = style.container,
        contentColor = style.content,
        icon = style.icon,
        modifier = modifier,
    )
}
