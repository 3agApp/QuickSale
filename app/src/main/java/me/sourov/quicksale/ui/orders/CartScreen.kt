package me.sourov.quicksale.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Storefront
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import me.sourov.quicksale.ui.products.ProductThumbnail
import me.sourov.quicksale.ui.products.asPrice
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Page one of an order: what's being bought.
 *
 * Nothing about money, delivery or payment appears here — those are the checkout's business. The
 * whole screen is the scanner and the list it fills, which is what the counter is actually doing
 * while the customer is standing there.
 *
 * The company button in the top bar opens the account behind this order: its billing address and
 * its branches, editable, without leaving the cart.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: NewOrderViewModel,
    onBack: () -> Unit,
    onReviewOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val member by viewModel.member.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val blocker by viewModel.blocker.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var showingCompany by remember { mutableStateOf(false) }

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
                            text = member?.name?.ifBlank { member?.email.orEmpty() } ?: "New order",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        organization?.let {
                            Text(
                                text = it.name,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { showingCompany = true }) {
                        Icon(
                            imageVector = Icons.Outlined.Storefront,
                            contentDescription = "Company details and branches",
                        )
                    }
                },
            )
        },
        bottomBar = {
            OrderTotalsBar(
                totals = totals,
                itemCount = itemCount,
                actionLabel = if (lines.isEmpty()) "Add a product to continue" else "Review order",
                onAction = onReviewOrder,
                // Only the reasons that make the whole order impossible stop you reaching the
                // checkout; everything else is the checkout's own to explain, on the page that
                // can actually fix it.
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
                    .padding(vertical = Spacing.md),
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

    if (showingCompany) {
        CompanySheet(
            viewModel = viewModel,
            onDismiss = { showingCompany = false },
        )
    }
}

@Composable
private fun ProductResultRow(product: Product, modifier: Modifier = Modifier) {
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
 */
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
            .padding(vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(line.product.imageUrl, size = 48.dp)
        Spacer(Modifier.width(Spacing.md))
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
