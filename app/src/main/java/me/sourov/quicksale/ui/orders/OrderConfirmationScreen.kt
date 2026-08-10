package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.products.asPrice
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing
import java.math.BigDecimal

/**
 * Shown after an order is successfully created.
 *
 * [organizationName] and [locationName] are the store's own stamps, recorded as they were at
 * order time — so they survive a later rename or deletion, and they are the honest record of what
 * happened rather than what the app believed.
 */
@Composable
fun OrderConfirmationScreen(
    orderId: Long,
    total: String,
    totalTax: String,
    shippingTotal: String,
    discountTotal: String,
    organizationName: String,
    locationName: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(88.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = "Order placed",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = Spacing.xl),
        )
        Text(
            text = if (orderId > 0) {
                "Order #$orderId was created in your store."
            } else {
                "Your order was created in your store."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = Spacing.sm),
        )

        if (organizationName.isNotBlank() || locationName.isNotBlank()) {
            Spacer(Modifier.height(Spacing.xl))
            QuickSaleCard {
                Column(Modifier.padding(Spacing.lg)) {
                    if (organizationName.isNotBlank()) {
                        StampRow(
                            icon = Icons.Outlined.Business,
                            label = "Account",
                            value = organizationName,
                        )
                    }
                    if (locationName.isNotBlank()) {
                        if (organizationName.isNotBlank()) Spacer(Modifier.height(Spacing.md))
                        StampRow(
                            icon = Icons.Outlined.Place,
                            label = "Delivering to",
                            value = locationName,
                        )
                    }
                }
            }
        }

        if (total.isNotBlank()) {
            Spacer(Modifier.height(Spacing.lg))
            QuickSaleCard {
                Column(modifier = Modifier.padding(Spacing.lg)) {
                    if (shippingTotal.isPositiveAmount()) {
                        TotalsLine("Shipping", shippingTotal.asPrice())
                    }
                    if (discountTotal.isPositiveAmount()) {
                        TotalsLine("Discount", "−${discountTotal.asPrice()}")
                    }
                    if (totalTax.isPositiveAmount()) {
                        TotalsLine("Tax", totalTax.asPrice())
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = Spacing.sm))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Total", style = MaterialTheme.typography.titleMedium)
                        Text(
                            total.asPrice(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }

        Button(
            onClick = onDone,
            modifier = Modifier
                .padding(top = Spacing.xxl)
                .fillMaxWidth()
                .height(Sizes.button),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun StampRow(icon: ImageVector, label: String, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(Spacing.md))
        Column {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

@Composable
private fun TotalsLine(label: String, value: String) {
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

private fun String.isPositiveAmount(): Boolean =
    (toBigDecimalOrNull() ?: BigDecimal.ZERO).signum() > 0
