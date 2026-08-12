package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.theme.Spacing
import java.time.LocalDate

/**
 * Every order the store has taken, newest first and grouped by the day it was taken.
 *
 * The day headings are the point of the screen: at a fair the only question anyone asks of a list
 * of orders is "is this one from today", and a column of identical timestamps answers it slowly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    onOrderClick: (orderId: Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val viewModel: OrdersViewModel = viewModel(
        factory = OrdersViewModel.factory(container.settings),
    )

    val orders by viewModel.orders.collectAsStateWithLifecycle()
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    // Refetch when the network comes back. The first load of a list opened offline returns
    // nothing, and without this the screen kept saying "No orders yet" long after it was wrong.
    val online by container.connectivity.online.collectAsStateWithLifecycle(initialValue = true)
    LaunchedEffect(online) { if (online) viewModel.refresh() }

    // Recomputed per composition rather than remembered: a fair runs past midnight, and a cached
    // "today" would quietly start meaning yesterday.
    val today = LocalDate.now()
    val visible = orders.filter { filter.matches(it, today) }
    val groups = remember(visible) { visible.groupByDay() }

    Column(modifier = modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            OrderFilter.entries.forEach { option ->
                FilterChip(
                    selected = option == filter,
                    onClick = { viewModel.setFilter(option) },
                    label = { Text(option.label) },
                )
            }
        }

        PullToRefreshBox(
            isRefreshing = loading,
            onRefresh = viewModel::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            when {
                loading && orders.isEmpty() -> LoadingState()

                visible.isEmpty() -> Box(Modifier.fillMaxSize()) {
                    EmptyState(
                        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                        title = if (orders.isEmpty()) "No orders yet" else "Nothing matches",
                        message = if (orders.isEmpty()) {
                            "Orders placed from any device show up here."
                        } else {
                            "No orders under \"${filter.label}\". Try another filter."
                        },
                    )
                }

                else -> LazyColumn(
                    contentPadding = PaddingValues(
                        start = Spacing.screen,
                        end = Spacing.screen,
                        bottom = Spacing.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                ) {
                    groups.forEach { group ->
                        item(key = "header-${group.heading}") {
                            Text(
                                text = group.heading,
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(
                                    top = Spacing.sm,
                                    bottom = 2.dp,
                                ),
                            )
                        }
                        items(group.orders, key = { it.id }) { order ->
                            OrderRow(
                                order = order,
                                onClick = { onOrderClick(order.id) },
                                showAccount = true,
                            )
                        }
                    }
                }
            }
        }
    }

    error?.let { OrderErrorDialog(error = it, onDismiss = viewModel::consumeError) }
}

/** Orders sharing one calendar day, under the heading that day gets. */
private class DayGroup(val heading: String, val orders: List<WooCommerceApi.OrderSummary>)

/**
 * Splits the feed into day groups, preserving the newest-first order the store returned.
 *
 * Orders whose stamp won't parse are grouped under "Earlier" rather than dropped — an order the
 * app can't date is still an order that was taken.
 */
private fun List<WooCommerceApi.OrderSummary>.groupByDay(): List<DayGroup> {
    val today = LocalDate.now()
    return groupBy { it.dateCreatedGmt.toOrderLocalDate() }
        .map { (day, orders) ->
            val heading = when (day) {
                null -> "Earlier"
                today -> "Today"
                today.minusDays(1) -> "Yesterday"
                else -> day.format(java.time.format.DateTimeFormatter.ofPattern("EEEE, MMM d"))
            }
            DayGroup(heading, orders)
        }
}
