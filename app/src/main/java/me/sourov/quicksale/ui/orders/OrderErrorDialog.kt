package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Surfaces a refusal from the store.
 *
 * Every rule the plugin applies is enforced server-side on every request, so a stale snapshot
 * degrades to a clean refusal rather than a wrong order — which means the useful next step is
 * usually a re-sync, offered here when the error suggests one.
 */
@Composable
fun OrderErrorDialog(
    error: OrderError,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = { Text(error.headline) },
        text = {
            Column {
                if (error.detail.isNotBlank()) {
                    Text(
                        text = error.detail,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                if (error.suggestsSync) {
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = "Nothing was charged and no order was created. Your copy of this " +
                            "account may be out of date.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("OK") }
        },
        dismissButton = if (error.suggestsSync) {
            {
                TextButton(
                    onClick = {
                        SyncManager.syncOrganizations(context)
                        onDismiss()
                    },
                ) { Text("Sync accounts") }
            }
        } else {
            null
        },
    )
}
