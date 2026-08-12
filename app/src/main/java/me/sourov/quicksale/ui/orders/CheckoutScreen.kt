package me.sourov.quicksale.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.settings.PaymentGateway
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.products.ProductThumbnail
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Page two of an order: where it goes, how it's paid for, and what it costs.
 *
 * The cart is behind you by the time you get here, summarised rather than editable — a checkout
 * that also lets you re-scan is the one-page screen this replaced.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CheckoutScreen(
    viewModel: NewOrderViewModel,
    onBack: () -> Unit,
    onPlaced: (result: PlaceResult.Placed) -> Unit,
    modifier: Modifier = Modifier,
) {
    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val lines by viewModel.lines.collectAsStateWithLifecycle()
    val totals by viewModel.totals.collectAsStateWithLifecycle()
    val itemCount by viewModel.itemCount.collectAsStateWithLifecycle()
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
    val branches by viewModel.locations.collectAsStateWithLifecycle()
    val delivery by viewModel.delivery.collectAsStateWithLifecycle()
    val addressForms by viewModel.addressForms.collectAsStateWithLifecycle()
    val addressCountry by viewModel.addressCountry.collectAsStateWithLifecycle()
    val addressFields by viewModel.addressFields.collectAsStateWithLifecycle()
    val addressValues by viewModel.addressValues.collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    var showingCompany by remember { mutableStateOf(false) }

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
                        Text("Checkout", style = MaterialTheme.typography.titleMedium)
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
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to the cart",
                        )
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
                actionLabel = blocker?.reason ?: "Place order",
                onAction = viewModel::placeOrder,
                enabled = blocker == null,
                busy = placing,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            AnimatedVisibility(
                visible = blocker?.fatal == true,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                RefusalBanner(text = blocker?.reason.orEmpty())
            }

            Spacer(Modifier.height(Spacing.md))
            SectionHeader(
                title = "Order",
                subtitle = "$itemCount ${if (itemCount == 1) "item" else "items"}",
            )
            Spacer(Modifier.height(Spacing.sectionGap))
            QuickSaleCard {
                Column(Modifier.padding(Spacing.md)) {
                    lines.forEach { line -> OrderLineSummary(line) }
                }
            }

            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(
                title = "Delivery",
                subtitle = "${organization?.name.orEmpty()}'s billing address is applied by the store",
            )
            Spacer(Modifier.height(Spacing.sectionGap))
            DeliveryAddressSection(
                branches = branches,
                delivery = delivery,
                onDeliveryEnabledChange = viewModel::setDeliveryEnabled,
                onSelectBranch = viewModel::selectBranch,
                onResetToBranch = viewModel::resetAddressToBranch,
                addressForms = addressForms,
                country = addressCountry,
                fields = addressFields,
                values = addressValues,
                onSelectCountry = viewModel::selectAddressCountry,
                onFieldChange = viewModel::setAddressField,
                allowCustomShipping = organization?.allowCustomShipping == true,
            )

            Spacer(Modifier.height(Spacing.sectionSpacing))
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

            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (showingCompany) {
        CompanySheet(viewModel = viewModel, onDismiss = { showingCompany = false })
    }

    error?.let { OrderErrorDialog(error = it, onDismiss = viewModel::consumeError) }
}

/** One cart line, read-only: quantity, picture and what it comes to. */
@Composable
private fun OrderLineSummary(line: CartLine) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ProductThumbnail(line.product.imageUrl, size = 40.dp)
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = "${line.quantity} ×",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = line.product.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = line.lineTotal.display(),
            style = MaterialTheme.typography.titleSmall,
        )
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
