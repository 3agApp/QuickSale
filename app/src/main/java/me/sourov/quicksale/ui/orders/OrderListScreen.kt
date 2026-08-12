package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.products.asPrice
import me.sourov.quicksale.ui.theme.Spacing

/**
 * An organization's orders, newest first. Read-only here — tap an order to see and, when it's
 * still open, edit what's on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderListScreen(
    viewModel: OrderListViewModel,
    onBack: () -> Unit,
    onOrderClick: (orderId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Orders") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            when {
                loading && orders.isEmpty() -> LoadingState()

                orders.isEmpty() -> EmptyState(
                    icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                    title = "No orders yet",
                    message = "Orders placed for this account will show up here.",
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = Spacing.screen, vertical = Spacing.md),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    items(orders, key = { it.id }) { order ->
                        OrderRow(order = order, onClick = { onOrderClick(order.id) })
                    }
                }
            }
        }
    }

    error?.let { OrderErrorDialog(error = it, onDismiss = viewModel::consumeError) }
}

/**
 * One order in a list. [showAccount] adds the organization's name, which the account's own history
 * doesn't need — every row there belongs to the same company — and the all-orders tab does.
 */
@Composable
internal fun OrderRow(
    order: WooCommerceApi.OrderSummary,
    onClick: () -> Unit,
    showAccount: Boolean = false,
) {
    QuickSaleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (showAccount && order.organizationName.isNotBlank()) {
                        order.organizationName
                    } else {
                        "Order #${order.number.ifBlank { order.id.toString() }}"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = buildString {
                        if (showAccount && order.organizationName.isNotBlank()) {
                            append("#${order.number.ifBlank { order.id.toString() }} · ")
                        }
                        append(order.dateCreatedGmt.toOrderDateLabel())
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(Spacing.sm))
            Column(horizontalAlignment = Alignment.End) {
                OrderStatusChip(order.status)
                Spacer(Modifier.height(Spacing.xs))
                Text(order.total.asPrice(), style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}
