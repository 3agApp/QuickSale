package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.products.asPrice
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing
import java.math.BigDecimal

/**
 * One order: what's on it, and — while it's still [WooCommerceApi.OrderDetail.isEditable] —
 * a way to add, remove or re-quantity what's on it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderDetailScreen(
    viewModel: OrderDetailViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val order by viewModel.order.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val workingLines by viewModel.workingLines.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = order?.let { "Order #${it.number.ifBlank { it.id.toString() }}" }
                                ?: "Order",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        order?.organizationName?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { if (editing) viewModel.cancelEditing() else onBack() }) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (editing) "Cancel editing" else "Back",
                        )
                    }
                },
                actions = {
                    if (order?.isEditable == true && !editing) {
                        IconButton(onClick = viewModel::startEditing) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit items")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (editing) {
                EditOrderBar(
                    itemCount = workingLines.sumOf { it.quantity },
                    total = workingLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.lineTotal },
                    saving = saving,
                    enabled = workingLines.isNotEmpty(),
                    onCancel = viewModel::cancelEditing,
                    onSave = viewModel::saveChanges,
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        val current = order
        when {
            current == null && loading -> LoadingState(modifier = Modifier.padding(padding).fillMaxSize())

            current == null -> EmptyState(
                modifier = Modifier.padding(padding).fillMaxSize(),
                icon = Icons.AutoMirrored.Outlined.ReceiptLong,
                title = "Order not found",
                message = "It may have been removed from the store.",
            )

            editing -> EditableOrderContent(
                modifier = Modifier.padding(padding),
                query = query,
                onQueryChange = viewModel::onQueryChange,
                onSubmitTyped = viewModel::submitTyped,
                searchResults = searchResults,
                onAddFromSearch = viewModel::addFromSearch,
                lines = workingLines,
                onIncrement = viewModel::increment,
                onDecrement = viewModel::decrement,
                onRemove = viewModel::remove,
            )

            else -> ReadOnlyOrderContent(order = current, modifier = Modifier.padding(padding))
        }
    }

    error?.let { OrderErrorDialog(error = it, onDismiss = viewModel::consumeError) }
}

/** What the order has on it right now, with no way to change it. */
@Composable
private fun ReadOnlyOrderContent(order: WooCommerceApi.OrderDetail, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen),
    ) {
        Spacer(Modifier.height(Spacing.md))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OrderStatusChip(order.status)
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = order.dateCreatedGmt.toOrderDateLabel(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!order.isEditable) {
            Spacer(Modifier.height(Spacing.md))
            Text(
                text = "This order can no longer be edited from here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(
            title = "Items",
            subtitle = "${order.lineItems.sumOf { it.quantity }} " +
                if (order.lineItems.sumOf { it.quantity } == 1) "item" else "items",
        )
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(Spacing.md)) {
                order.lineItems.forEach { item -> ReadOnlyLineRow(item) }
            }
        }

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(title = "Totals")
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(Spacing.md)) {
                // Line items' own totals, not a derived subtotal: WooCommerce already nets any
                // per-item discount into each line's `total`, so summing them is the honest figure.
                val itemsSubtotal = order.lineItems.fold(BigDecimal.ZERO) { acc, item ->
                    acc + (item.total.toBigDecimalOrNull() ?: BigDecimal.ZERO)
                }
                SummaryRow("Items", itemsSubtotal.display())
                if (order.shippingTotal.toBigDecimalOrNull()?.let { it != BigDecimal.ZERO } == true) {
                    SummaryRow("Shipping", order.shippingTotal.asPrice())
                }
                if (order.totalTax.toBigDecimalOrNull()?.let { it != BigDecimal.ZERO } == true) {
                    SummaryRow("Tax", order.totalTax.asPrice())
                }
                if (order.discountTotal.toBigDecimalOrNull()?.let { it != BigDecimal.ZERO } == true) {
                    SummaryRow("Discount", "−${order.discountTotal.asPrice()}")
                }
                HorizontalDivider(Modifier.padding(vertical = Spacing.xs))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Total", style = MaterialTheme.typography.titleSmall)
                    Text(order.total.asPrice(), style = MaterialTheme.typography.titleSmall)
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun ReadOnlyLineRow(item: WooCommerceApi.OrderLineItem) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${item.quantity} ×",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = item.name.ifBlank { "(no name)" },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(item.total.asPrice(), style = MaterialTheme.typography.titleSmall)
    }
}

/** Add-product search plus the interactive line list, the same shape as the cart's own editor. */
@Composable
private fun EditableOrderContent(
    query: String,
    onQueryChange: (String) -> Unit,
    onSubmitTyped: () -> Unit,
    searchResults: List<Product>,
    onAddFromSearch: (Product) -> Unit,
    lines: List<EditableLine>,
    onIncrement: (Long) -> Unit,
    onDecrement: (Long) -> Unit,
    onRemove: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Spacing.screen),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            placeholder = {
                Text("Scan or search products", maxLines = 1, overflow = TextOverflow.Ellipsis)
            },
            leadingIcon = { Icon(Icons.Outlined.QrCodeScanner, contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }) {
                        Icon(Icons.Filled.Close, contentDescription = "Clear")
                    }
                }
            },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmitTyped() }),
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = Spacing.md),
        )

        when {
            query.isNotBlank() -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(searchResults, key = { it.id }) { product ->
                    ProductResultRow(
                        product = product,
                        modifier = Modifier.clickable { onAddFromSearch(product) },
                    )
                    HorizontalDivider()
                }
            }

            lines.isEmpty() -> EmptyState(
                icon = Icons.Outlined.QrCodeScanner,
                title = "No items left",
                message = "Scan or search to add a product before saving.",
            )

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(lines, key = { it.localKey }) { line ->
                    EditableLineRow(
                        line = line,
                        onIncrement = { onIncrement(line.localKey) },
                        onDecrement = { onDecrement(line.localKey) },
                        onRemove = { onRemove(line.localKey) },
                    )
                    HorizontalDivider()
                }
            }
        }
    }
}

@Composable
private fun EditableLineRow(
    line: EditableLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = line.name.ifBlank { "(no name)" },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "${line.unitPrice.asPrice()} each · ${line.lineTotal.display()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onDecrement) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text(
            line.quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        FilledTonalIconButton(onClick = onIncrement) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
        IconButton(onClick = onRemove) {
            Icon(
                Icons.Outlined.Delete,
                contentDescription = "Remove",
                tint = MaterialTheme.colorScheme.error,
            )
        }
    }
}

/** Cancel / Save bar for edit mode — the cart's totals bar has a single action, this needs two. */
@Composable
private fun EditOrderBar(
    itemCount: Int,
    total: BigDecimal,
    saving: Boolean,
    enabled: Boolean,
    onCancel: () -> Unit,
    onSave: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                // Only what the shell has not already taken. This screen keeps the shell's bottom
                // navigation below it, so in practice that is the keyboard alone — which covers
                // that navigation bar, and would take Save down with it.
                .navigationBarsPadding()
                .imePadding()
                .padding(Spacing.screen),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = total.display(),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                OutlinedButton(
                    onClick = onCancel,
                    enabled = !saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizes.button),
                ) { Text("Cancel") }
                Button(
                    onClick = onSave,
                    enabled = enabled && !saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizes.button),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save changes")
                    }
                }
            }
        }
    }
}
