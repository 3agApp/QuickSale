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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PersonSearch
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.settings.PaymentGateway
import me.sourov.quicksale.data.settings.ShippingOption
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.products.ProductThumbnail
import me.sourov.quicksale.ui.theme.Sizes
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
    viewModel: SellViewModel,
    onBack: () -> Unit,
    onPlaced: (result: PlaceResult.Placed) -> Unit,
    modifier: Modifier = Modifier,
) {
    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val member by viewModel.member.collectAsStateWithLifecycle()
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
    var showingCustomerPicker by remember { mutableStateOf(false) }

    // Opening the checkout with no customer goes straight to the picker: it is the one thing that
    // must happen here, and making the operator find it first is the step this redesign removes.
    LaunchedEffect(Unit) {
        if (viewModel.customer.value == null) showingCustomerPicker = true
    }

    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(placed) {
        when (val result = placed) {
            is PlaceResult.Placed -> {
                viewModel.consumePlaced()
                onPlaced(result)
            }
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
                // No imePadding here, deliberately. The totals bar already consumes the keyboard
                // inset, and Scaffold measures the bar *including* it — so `padding` below carries
                // the keyboard's height once already. Adding imePadding on top double-counts it
                // and leaves a keyboard-sized hole under the last field.
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

            // First, because it is the one thing the cart deliberately doesn't know and the one
            // thing the store won't accept an order without. No "Customer" heading over it: the
            // card names a company and a person and carries a Change button, which says what it is
            // more plainly than a word above it did.
            Spacer(Modifier.height(Spacing.md))
            CustomerSection(
                organization = organization,
                member = member,
                onChoose = { showingCustomerPicker = true },
            )

            // The cart the operator just left, folded away. It is a recap, not a decision, and
            // expanded it pushed delivery and payment — the two things this page is for — a
            // screenful down. One tap brings it back when a line needs checking.
            Spacer(Modifier.height(Spacing.sectionSpacing))
            var showingLines by rememberSaveable { mutableStateOf(false) }
            SectionHeader(
                title = "Order",
                subtitle = "$itemCount ${if (itemCount == 1) "item" else "items"} · " +
                    totals.subtotal.display(),
                trailing = {
                    TextButton(onClick = { showingLines = !showingLines }) {
                        Text(if (showingLines) "Hide" else "Show")
                    }
                },
            )
            AnimatedVisibility(
                visible = showingLines,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                QuickSaleCard(modifier = Modifier.padding(top = Spacing.sectionGap)) {
                    Column(Modifier.padding(Spacing.md)) {
                        lines.forEach { line -> OrderLineSummary(line) }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(
                title = "Delivery",
                // Shortened, not dropped: this is the one place that says billing and delivery are
                // different things here. It used to open with the company's name, which pushed it
                // onto a second line for any account with a long one.
                subtitle = "The billing address is applied by the store",
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

    if (showingCustomerPicker) {
        CustomerPickerSheet(
            onDismiss = { showingCustomerPicker = false },
            onSelect = { chosen ->
                viewModel.selectCustomer(chosen)
                showingCustomerPicker = false
            },
        )
    }

    error?.let { OrderErrorDialog(error = it, onDismiss = viewModel::consumeError) }
}

/**
 * Who the order is for, or the invitation to say so.
 *
 * Before a customer is chosen this is the loudest thing on the page; afterwards it collapses to one
 * quiet line with a way back, because at that point it is settled and the delivery and payment
 * below it are what still need attention.
 */
@Composable
private fun CustomerSection(
    organization: Organization?,
    member: Member?,
    onChoose: () -> Unit,
) {
    if (organization == null) {
        QuickSaleCard(containerColor = MaterialTheme.colorScheme.secondaryContainer) {
            Column(Modifier.padding(Spacing.lg)) {
                Text(
                    text = "No customer yet",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    text = "The store needs an account before it will take this order.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(Spacing.md))
                Button(onClick = onChoose) {
                    Icon(
                        Icons.Filled.PersonSearch,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Choose or create")
                }
            }
        }
        return
    }

    // Once it is settled this is a one-line fact with a way back, so it gets a row's padding
    // rather than a card's — the delivery and payment below it are what still need attention.
    QuickSaleCard {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.md, vertical = Spacing.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(initials = organization.initials, size = Sizes.avatarSmall)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = organization.name,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = member?.name?.ifBlank { member.email } ?: "Loading…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onChoose) { Text("Change") }
        }
    }
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
