package me.sourov.quicksale.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.ui.products.ProductThumbnail
import me.sourov.quicksale.ui.products.asPrice
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The till. A standing cart with the scanner armed, and the app's front door on a selling device.
 *
 * Nothing about money, delivery, payment — or *who is buying* — appears here. At a fair the visitor
 * is already standing at the product when they decide they want it, so the first scan has to be the
 * first thing that happens; the account is the checkout's business. Scanning the same item again
 * adds another of the store's pack size.
 *
 * The cart survives leaving the tab: the view model is scoped to the Sell back-stack entry and
 * shared with the checkout, so nothing is retyped and nothing is rung up twice.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SellScreen(
    viewModel: SellViewModel,
    onCheckout: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val blocker by viewModel.blocker.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    // Scans are collected here, not in the view model, because the view model outlives this tab —
    // subscribing there would ring products into the cart while the operator was printing labels.
    LaunchedEffect(Unit) {
        ScannerHub.scans.collect(viewModel::onScan)
    }

    // No top bar of its own: who the order is for, the company sheet and the clear button all live
    // in the shell's bar now. Two strips to say one thing cost 48dp of a 775dp screen, and this is
    // the screen that can least afford it.
    Scaffold(
        modifier = modifier,
        bottomBar = {
            OrderTotalsBar(
                totals = totals,
                itemCount = itemCount,
                actionLabel = if (lines.isEmpty()) "Scan a product to start" else "Checkout",
                onAction = onCheckout,
                // Only the reasons that make the whole order impossible stop you reaching the
                // checkout; everything else — including not having picked a customer yet — is the
                // checkout's own to explain, on the page that can actually fix it.
                enabled = lines.isNotEmpty() && blocker?.fatal != true,
                showBreakdown = false,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = Spacing.screen),
        ) {
            // A fatal refusal is worth showing before anything is rung up, not after.
            AnimatedVisibility(
                visible = blocker?.fatal == true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                RefusalBanner(text = blocker?.reason.orEmpty())
            }

            OutlinedTextField(
                value = query,
                onValueChange = viewModel::onQueryChange,
                placeholder = {
                    Text(
                        "Scan or search products",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                leadingIcon = {
                    Icon(Icons.Outlined.QrCodeScanner, contentDescription = null)
                },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.submitTyped() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.sm, bottom = Spacing.sm),
            )

            when {
                query.isNotBlank() -> {
                    // Manual add: show matches to tap.
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(searchResults, key = { it.id }) { product ->
                            ProductResultRow(
                                product = product,
                                modifier = Modifier.clickable { viewModel.addFromSearch(product) },
                            )
                            HorizontalDivider()
                        }
                    }
                }

                lines.isEmpty() -> ScanEmptyState()

                else -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                ) {
                    items(lines, key = { it.product.id }) { line ->
                        CartLineRow(
                            line = line,
                            onIncrement = { viewModel.increment(line.product.id) },
                            onDecrement = { viewModel.decrement(line.product.id) },
                            onRemove = { viewModel.remove(line.product.id) },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }
}

/** A search match to tap to add. Shared with the order-editing screen's add-product search. */
@Composable
internal fun ProductResultRow(product: Product, modifier: Modifier = Modifier) {
    ListItem(
        modifier = modifier.fillMaxWidth(),
        colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surface),
        leadingContent = { ProductThumbnail(product.imageUrl, size = 52.dp) },
        headlineContent = {
            Text(
                text = product.name.ifBlank { "(no name)" },
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(product.price.asPrice(), style = MaterialTheme.typography.titleSmall)
                // The EAN wins the space when there is one: it's what was scanned.
                val code = product.ean.takeIf { it.isNotBlank() }?.let { "EAN $it" }
                    ?: product.sku.takeIf { it.isNotBlank() }?.let { "SKU $it" }
                if (code != null) {
                    Text(
                        code,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        },
    )
}

/**
 * One line of the cart, with the product's picture.
 *
 * The picture is not decoration: a counter running six near-identical SKUs reads a photograph far
 * faster than it reads "Bio-Vollmilch 3,8% 1L Mehrweg", and a wrongly scanned item is caught here
 * or not at all.
 *
 * There is no delete button. There used to be, and it made four touch targets on a 411dp row for
 * three things you can want — and the third was already reachable, since decrementing the last one
 * removes the line. Long-press does it outright for a line rung up ten deep.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CartLineRow(
    line: CartLine,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onRemove: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = {}, onLongClick = onRemove)
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(line.product.imageUrl, size = Sizes.thumbnail)
        Spacer(Modifier.width(Spacing.sm))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                line.product.name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${line.product.price.asPrice()} each · ${line.lineTotal.display()}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (line.beyondStock > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Outlined.Inventory2,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.error,
                    )
                    Spacer(Modifier.width(Spacing.xs))
                    Text(
                        text = "${line.beyondStock} more than in stock",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        FilledTonalIconButton(onClick = onDecrement) {
            Icon(
                imageVector = if (line.quantity > 1) Icons.Filled.Remove else Icons.Outlined.Delete,
                contentDescription = if (line.quantity > 1) "Decrease" else "Remove",
            )
        }
        Text(
            line.quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.sm),
        )
        FilledTonalIconButton(onClick = onIncrement) {
            Icon(Icons.Filled.Add, contentDescription = "Increase")
        }
    }
}

@Composable
private fun ScanEmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.QrCodeScanner,
            contentDescription = null,
            modifier = Modifier.size(72.dp),
            tint = MaterialTheme.colorScheme.outline,
        )
        Text(
            text = "Scan to add products",
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(top = Spacing.lg),
        )
        Text(
            text = "Point the scanner at a barcode to add it, or search above. Scanning the same item again increases its quantity.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = Spacing.sm),
        )
    }
}
