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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.RepeatingStepperButton
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
    val placedBy by viewModel.placedBy.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val editing by viewModel.editing.collectAsStateWithLifecycle()
    val workingLines by viewModel.workingLines.collectAsStateWithLifecycle()
    val workingShipping by viewModel.workingShipping.collectAsStateWithLifecycle()
    val changes by viewModel.changes.collectAsStateWithLifecycle()
    val reviewing by viewModel.reviewing.collectAsStateWithLifecycle()
    val checkout by viewModel.checkout.collectAsStateWithLifecycle()
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
                    // Shipping counts: it is editable on this screen now, and a running total that
                    // sat still while the delivery charge doubled would be worse than no total.
                    total = workingLines.fold(BigDecimal.ZERO) { acc, line -> acc + line.lineTotal } +
                        (workingShipping?.cost?.toBigDecimalOrNull() ?: BigDecimal.ZERO),
                    saving = saving,
                    // Live only when there is something to send. An order still needs a product on
                    // it, so an edit that removed the last line stays unsaveable.
                    enabled = changes.isNotEmpty() && workingLines.isNotEmpty(),
                    onCancel = viewModel::cancelEditing,
                    onSave = viewModel::requestSave,
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
                canStepDown = viewModel::canStepDown,
                shipping = workingShipping,
                shippingOptions = checkout.shippingOptions,
                onSelectShipping = viewModel::selectShipping,
                onShippingCostChange = viewModel::setShippingCost,
                onRemoveShipping = viewModel::removeShipping,
            )

            else -> ReadOnlyOrderContent(
                order = current,
                placedBy = placedBy,
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (reviewing) {
        ConfirmChangesDialog(
            changes = changes,
            saving = saving,
            onConfirm = viewModel::confirmSave,
            onDismiss = viewModel::dismissReview,
        )
    }

    error?.let { OrderErrorDialog(error = it, onDismiss = viewModel::consumeError) }
}

/**
 * What saving is about to do, before it does it.
 *
 * The one screen in the app that writes to an order someone has already been given a price for, so
 * it asks. The list is the request itself put into words — see [OrderDetailViewModel.pendingSave] —
 * rather than a second description of it that could drift.
 */
@Composable
private fun ConfirmChangesDialog(
    changes: List<OrderChange>,
    saving: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = { if (!saving) onDismiss() },
        title = { Text("Save these changes?") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                changes.forEach { change -> ChangeRow(change) }
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = !saving) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Text("Save")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !saving) { Text("Keep editing") }
        },
    )
}

/** One line of the confirmation: what happens, to what, and by how much. */
@Composable
private fun ChangeRow(change: OrderChange) {
    val (icon, tint) = when (change.kind) {
        OrderChange.Kind.ADDED -> Icons.Filled.Add to MaterialTheme.colorScheme.primary
        OrderChange.Kind.REMOVED -> Icons.Outlined.Delete to MaterialTheme.colorScheme.error
        OrderChange.Kind.CHANGED ->
            Icons.AutoMirrored.Filled.ArrowForward to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(16.dp),
            tint = tint,
        )
        Spacer(Modifier.width(Spacing.sm))
        Column(Modifier.weight(1f)) {
            Text(
                text = change.label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (change.detail.isNotBlank()) {
                Text(
                    text = change.detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** What the order has on it right now, with no way to change it. */
@Composable
private fun ReadOnlyOrderContent(
    order: WooCommerceApi.OrderDetail,
    placedBy: Member?,
    modifier: Modifier = Modifier,
) {
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
        SectionHeader(title = "Account")
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(Spacing.md)) {
                if (order.organizationName.isNotBlank()) {
                    DetailRow("Company", order.organizationName)
                }
                if (order.locationName.isNotBlank()) {
                    DetailRow("Location", order.locationName)
                }
                // The buyer, named from this device's copy of the account. Falling back to the
                // billing contact is not a guess — on an order with no member left to find, that
                // name is the only person the store still associates with it.
                val buyerName = placedBy?.name?.takeIf { it.isNotBlank() }
                    ?: order.billing.name.takeIf { it.isNotBlank() }
                DetailRow(
                    label = "Placed by",
                    value = buyerName ?: "Customer #${order.customerId}",
                )
                placedBy?.email?.takeIf { it.isNotBlank() }?.let { DetailRow("Email", it) }
                if (order.paymentMethodTitle.isNotBlank()) {
                    DetailRow("Payment", order.paymentMethodTitle)
                }
            }
        }

        if (order.customerNote.isNotBlank()) {
            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(title = "Customer note")
            Spacer(Modifier.height(Spacing.sectionGap))
            QuickSaleCard {
                Text(
                    text = order.customerNote,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(Spacing.md),
                )
            }
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

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(title = "Delivery")
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(Spacing.md)) {
                if (order.shipping.isEmpty) {
                    // No delivery address is a fact about the order, not missing data: it is what a
                    // sale the customer carried off the stand looks like.
                    Text(
                        text = "Nothing to deliver — taken from the counter.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AddressBlock(order.shipping)
                }
            }
        }

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(title = "Billing")
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(Spacing.md)) {
                if (order.billing.isEmpty) {
                    Text(
                        text = "The store holds no billing address for this order.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    AddressBlock(order.billing)
                }
            }
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

/**
 * One address, laid out the way it would be written on an envelope.
 *
 * Contact details sit under the address rather than beside it, because they are what the counter
 * actually reaches for — a delivery to chase, an invoice query to answer.
 */
@Composable
private fun AddressBlock(address: WooCommerceApi.OrderAddress) {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
        address.name.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.titleSmall)
        }
        address.company.takeIf { it.isNotBlank() }?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        address.streetLines.forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (address.email.isNotBlank() || address.phone.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xs))
            address.email.takeIf { it.isNotBlank() }?.let { DetailRow("Email", it) }
            address.phone.takeIf { it.isNotBlank() }?.let { DetailRow("Phone", it) }
        }
    }
}

/** A labelled fact, for the rows that are read rather than scanned. */
@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.End,
        )
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
    canStepDown: (Long) -> Boolean,
    shipping: EditableShipping?,
    shippingOptions: List<ShippingOption>,
    onSelectShipping: (ShippingOption) -> Unit,
    onShippingCostChange: (String) -> Unit,
    onRemoveShipping: () -> Unit,
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
                        canStepDown = { canStepDown(line.localKey) },
                    )
                    HorizontalDivider()
                }
                // Below the products, because it is the thing you correct after them: the delivery
                // was quoted off a cart that has just changed under it.
                item(key = "shipping") {
                    EditableShippingCard(
                        shipping = shipping,
                        options = shippingOptions,
                        onSelect = onSelectShipping,
                        onCostChange = onShippingCostChange,
                        onRemove = onRemoveShipping,
                    )
                }
            }
        }
    }
}

/**
 * The order's delivery charge, while it is being edited.
 *
 * The cost is entered the way it is at the till — what the customer pays, tax included where the
 * store prices that way — and converted on the way back to WooCommerce, which stores it net.
 *
 * A store with no synced shipping methods still gets the cost field for whatever is already on the
 * order: not being able to name a *different* method is no reason to be unable to correct the
 * charge on this one.
 */
@Composable
private fun EditableShippingCard(
    shipping: EditableShipping?,
    options: List<ShippingOption>,
    onSelect: (ShippingOption) -> Unit,
    onCostChange: (String) -> Unit,
    onRemove: () -> Unit,
) {
    QuickSaleCard(modifier = Modifier.padding(vertical = Spacing.md)) {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Shipping",
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.weight(1f),
                )
                if (shipping != null) {
                    TextButton(onClick = onRemove) { Text("Remove") }
                }
            }

            if (options.isNotEmpty()) {
                SelectorRow(
                    label = "Method",
                    value = shipping?.methodTitle ?: "None — nothing is being shipped",
                    options = options.map { it.label },
                    onSelect = { index -> onSelect(options[index]) },
                )
            } else if (shipping == null) {
                Text(
                    text = "Sync the store to choose a shipping method.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = shipping.methodTitle,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            if (shipping != null) {
                OutlinedTextField(
                    value = shipping.cost,
                    onValueChange = onCostChange,
                    label = { Text("Shipping cost") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = Spacing.xs),
                )
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
    /** Whether a held − may take another step, or has reached the last one before removal. */
    canStepDown: () -> Boolean,
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
            // Stated, not enforced — the same note the cart puts under a line, for the same reason.
            line.packSizeNote?.let { note ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Info,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = note,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        RepeatingStepperButton(
            onStep = onDecrement,
            contentDescription = "Decrease",
            repeatWhileHeld = canStepDown,
        ) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text(
            line.quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        RepeatingStepperButton(onStep = onIncrement, contentDescription = "Increase") {
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
