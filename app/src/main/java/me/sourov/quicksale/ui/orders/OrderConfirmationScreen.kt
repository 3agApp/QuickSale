package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.ui.products.asPrice
import java.math.BigDecimal

/**
 * Shown after an order is successfully created in WooCommerce. [orderId] is the store's order
 * number; the amount strings are the totals the store calculated (blank when unknown).
 */
@Composable
fun OrderConfirmationScreen(
    orderId: Long,
    total: String,
    totalTax: String,
    shippingTotal: String,
    discountTotal: String,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Filled.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(96.dp),
            tint = MaterialTheme.colorScheme.tertiary,
        )
        Text(
            text = "Order placed",
            style = MaterialTheme.typography.headlineSmall,
            modifier = Modifier.padding(top = 24.dp),
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
            modifier = Modifier.padding(top = 8.dp),
        )

        if (total.isNotBlank()) {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (shippingTotal.isPositiveAmount()) {
                        TotalsLine("Shipping", shippingTotal.asPrice())
                    }
                    if (discountTotal.isPositiveAmount()) {
                        TotalsLine("Discount", "−${discountTotal.asPrice()}")
                    }
                    if (totalTax.isPositiveAmount()) {
                        TotalsLine("Tax", totalTax.asPrice())
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                .padding(top = 32.dp)
                .fillMaxWidth()
                .height(52.dp),
        ) {
            Text("Done")
        }
    }
}

@Composable
private fun TotalsLine(label: String, value: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp),
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
