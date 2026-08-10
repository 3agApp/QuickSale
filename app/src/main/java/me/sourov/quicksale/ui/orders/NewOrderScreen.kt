package me.sourov.quicksale.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.settings.PaymentGateway
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.products.ProductThumbnail
import me.sourov.quicksale.ui.products.asPrice
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The order builder: cart, delivery and payment, with the running total always in view.
 *
 * The order belongs to a member of an organization, which is why both ids identify this screen —
 * the member's WordPress user id becomes the order's customer.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewOrderScreen(
    organizationId: Long,
    memberUserId: Long,
    onBack: () -> Unit,
    onPlaced: (result: PlaceResult.Placed) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val viewModel: NewOrderViewModel = viewModel(
        factory = NewOrderViewModel.factory(
            organizationId = organizationId,
            memberUserId = memberUserId,
            organizationRepository = container.organizations,
            productRepository = container.products,
            settingsRepository = container.settings,
            orderSettingsRepository = container.orderSettings,
            checkoutConfigRepository = container.checkoutConfig,
            addressFormRepository = container.addressForms,
        ),
    )

    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val member by viewModel.member.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val placing by viewModel.placing.collectAsStateWithLifecycle()
    val placed by viewModel.placed.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val blocker by viewModel.blocker.collectAsStateWithLifecycle()
    val checkout by viewModel.checkout.collectAsStateWithLifecycle()
    val selectedGateway by viewModel.selectedGateway.collectAsStateWithLifecycle()
    val selectedShipping by viewModel.selectedShipping.collectAsStateWithLifecycle()
    val shippingCost by viewModel.shippingCost.collectAsStateWithLifecycle()
    val couponCode by viewModel.couponCode.collectAsStateWithLifecycle()
    val delivery by viewModel.delivery.collectAsStateWithLifecycle()
    val addressForms by viewModel.addressForms.collectAsStateWithLifecycle()
    val oneOffCountry by viewModel.oneOffCountry.collectAsStateWithLifecycle()
    val oneOffFields by viewModel.oneOffFields.collectAsStateWithLifecycle()
    val oneOffValues by viewModel.oneOffValues.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(placed) {
        when (val result = placed) {
            is PlaceResult.Placed -> onPlaced(result)
            null -> Unit
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
            )
        },
        bottomBar = {
            OrderTotalsBar(
                totals = totals,
                itemCount = itemCount,
                placing = placing,
                blocker = blocker,
                onPlace = viewModel::placeOrder,
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
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
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
                            name = line.product.name,
                            priceEach = line.product.price,
                            quantity = line.quantity,
                            onIncrement = { viewModel.increment(line.product.id) },
                            onDecrement = { viewModel.decrement(line.product.id) },
                            onRemove = { viewModel.remove(line.product.id) },
                        )
                        HorizontalDivider()
                    }

                    item(key = "delivery") {
                        Spacer(Modifier.height(Spacing.xl))
                        SectionHeader(
                            title = "Delivery",
                            subtitle = "The store applies ${organization?.name.orEmpty()}'s billing address itself",
                        )
                        Spacer(Modifier.height(Spacing.sectionGap))
                        DeliverySection(
                            locations = locations,
                            delivery = delivery,
                            onSelectDelivery = viewModel::selectDelivery,
                            allowCustomShipping = organization?.allowCustomShipping == true,
                            addressForms = addressForms,
                            oneOffCountry = oneOffCountry,
                            oneOffFields = oneOffFields,
                            oneOffValues = oneOffValues,
                            onSelectCountry = viewModel::selectOneOffCountry,
                            onFieldChange = viewModel::setOneOffField,
                        )
                    }

                    item(key = "checkout_options") {
                        Spacer(Modifier.height(Spacing.lg))
                        SectionHeader(title = "Payment & charges")
                        Spacer(Modifier.height(Spacing.sectionGap))
                        CheckoutOptions(
                            gateways = checkout.gateways,
                            selectedGateway = selectedGateway,
                            onSelectGateway = viewModel::selectGateway,
                            shippingOptions = checkout.shippingOptions,
                            selectedShipping = selectedShipping,
                            onSelectShipping = viewModel::selectShipping,
                            shippingCost = shippingCost,
                            onShippingCostChange = viewModel::setShippingCost,
                            couponCode = couponCode,
                            onCouponChange = viewModel::setCouponCode,
                        )
                    }
                }
            }
        }
    }

    error?.let { OrderErrorDialog(error = it, onDismiss = viewModel::consumeError) }
}

/** The persistent totals + place button. Always visible, so the price is never a surprise. */
@Composable
private fun OrderTotalsBar(
    totals: TotalsPreview,
    itemCount: Int,
    placing: Boolean,
    blocker: PlaceBlocker?,
    onPlace: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(Spacing.screen),
        ) {
            if (totals.shipping != null || totals.tax != null) {
                SummaryRow("Subtotal", totals.subtotal.display())
                totals.shipping?.let { SummaryRow("Shipping", it.display()) }
                totals.tax?.let { tax ->
                    SummaryRow(
                        if (totals.taxIncluded) {
                            "Incl. ${totals.taxLabel} (est.)"
                        } else {
                            "${totals.taxLabel} (est.)"
                        },
                        tax.display(),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = Spacing.md),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    "Total ($itemCount ${if (itemCount == 1) "item" else "items"})",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    totals.total.display(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = onPlace,
                enabled = blocker == null && !placing,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.button),
            ) {
                if (placing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    // The button says why it's unavailable rather than just being greyed out.
                    Text(blocker?.reason ?: "Place order")
                }
            }
        }
    }
}

/** Shown when the account or member simply can't buy — the cart is a waste of time until fixed. */
@Composable
private fun RefusalBanner(text: String) {
    QuickSaleCard(
        modifier = Modifier.padding(top = Spacing.md),
        containerColor = MaterialTheme.colorScheme.errorContainer,
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = Icons.Outlined.ErrorOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.size(Sizes.iconLarge),
            )
            Spacer(Modifier.width(Spacing.md))
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
        }
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
                if (product.sku.isNotBlank()) {
                    Text(
                        "SKU ${product.sku}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
    )
}

@Composable
private fun CartLineRow(
    name: String,
    priceEach: String,
    quantity: Int,
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
                name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${priceEach.asPrice()} each",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        FilledTonalIconButton(onClick = onDecrement) {
            Icon(Icons.Filled.Remove, contentDescription = "Decrease")
        }
        Text(
            quantity.toString(),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = Spacing.md),
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

/**
 * Payment method, shipping and coupon inputs. Everything offered here comes from the connected
 * store's own configuration (synced with the catalog), so the section adapts to whichever
 * WooCommerce site the app points at.
 */
@Composable
private fun CheckoutOptions(
    gateways: List<PaymentGateway>,
    selectedGateway: PaymentGateway?,
    onSelectGateway: (PaymentGateway) -> Unit,
    shippingOptions: List<ShippingOption>,
    selectedShipping: ShippingOption?,
    onSelectShipping: (ShippingOption?) -> Unit,
    shippingCost: String,
    onShippingCostChange: (String) -> Unit,
    couponCode: String,
    onCouponChange: (String) -> Unit,
) {
    QuickSaleCard {
        Column(modifier = Modifier.padding(Spacing.md)) {
            if (gateways.isNotEmpty()) {
                SelectorRow(
                    label = "Payment method",
                    value = selectedGateway?.title ?: "Select…",
                    options = gateways.map { it.title },
                    onSelect = { index -> onSelectGateway(gateways[index]) },
                )
            }
            if (shippingOptions.isNotEmpty()) {
                SelectorRow(
                    label = "Shipping method",
                    value = selectedShipping?.label ?: "None (in person)",
                    options = listOf("None (in person)") + shippingOptions.map { it.label },
                    onSelect = { index ->
                        onSelectShipping(if (index == 0) null else shippingOptions[index - 1])
                    },
                )
                AnimatedVisibility(
                    visible = selectedShipping != null,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    OutlinedTextField(
                        value = shippingCost,
                        onValueChange = onShippingCostChange,
                        label = { Text("Shipping cost") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = Spacing.xs, bottom = Spacing.sm),
                    )
                }
            }
            OutlinedTextField(
                value = couponCode,
                onValueChange = onCouponChange,
                label = { Text("Coupon code (optional)") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.xs),
            )
        }
    }
}

/** A tappable label/value row that opens a dropdown of [options]. */
@Composable
private fun SelectorRow(
    label: String,
    value: String,
    options: List<String>,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = options.isNotEmpty()) { expanded = true }
                .padding(vertical = Spacing.md, horizontal = Spacing.xs),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(value, style = MaterialTheme.typography.bodyLarge)
            }
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEachIndexed { index, option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        expanded = false
                        onSelect(index)
                    },
                )
            }
        }
    }
}

/** One quiet line of the totals breakdown above the grand total. */
@Composable
private fun SummaryRow(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(value, style = MaterialTheme.typography.bodyMedium)
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

/** Formats a money amount with two decimals and the app's price style. */
private fun BigDecimal.display(): String =
    setScale(2, RoundingMode.HALF_UP).toPlainString().asPrice()
