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
import me.sourov.quicksale.data.settings.NewOrderStatus
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Which WooCommerce status an order gets when this till places it.
 *
 * Two choices, because the question the shop is actually answering is one question: does an order
 * rung up here count as work already, or does someone check it first. Each row says what follows
 * from it downstream — the counter choosing this is rarely the person who will notice an order
 * sitting in the wrong queue a day later.
 */
@Composable
fun OrderStatusSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember(context) { context.appContainer.newOrderStatus }
    val scope = rememberCoroutineScope()
    val current by repository.status
        .collectAsStateWithLifecycle(initialValue = NewOrderStatus.DEFAULT)

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Orders",
            subtitle = "The status an order gets when it is placed from this device.",
        )
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(vertical = Spacing.sm)) {
                NewOrderStatus.entries.forEach { status ->
                    StatusRow(
                        status = status,
                        selected = status == current,
                        onSelect = { scope.launch { repository.setStatus(status) } },
                    )
                }
            }
        }
        Spacer(Modifier.height(Spacing.md))
        // Said once, under both options, because it is the part neither of them changes — and the
        // part someone reading a status called "Processing" is most likely to assume differently.
        Text(
            text = "Neither marks the order paid. QuickSale records the payment method and leaves " +
                "the money to the shop.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun StatusRow(
    status: NewOrderStatus,
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
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(status.title, style = MaterialTheme.typography.titleSmall)
                if (status == NewOrderStatus.DEFAULT) {
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "Default",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            Text(
                text = status.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
