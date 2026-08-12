package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.ui.CurrencyFormatter
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing
import java.math.BigDecimal

/** Pieces the cart and the checkout both use, so the two pages read as one flow. */

/**
 * The persistent totals bar. Always visible, so the price is never a surprise.
 *
 * The cart hides the breakdown: shipping and tax aren't decided until the checkout, and showing an
 * "estimate" of numbers nobody has chosen yet is noise.
 */
@Composable
fun OrderTotalsBar(
    totals: TotalsPreview,
    itemCount: Int,
    actionLabel: String,
    onAction: () -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    busy: Boolean = false,
    showBreakdown: Boolean = true,
) {
    Surface(modifier = modifier, shadowElevation = 8.dp) {
        Column(
            modifier = Modifier
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars))
                .padding(Spacing.screen),
        ) {
            if (showBreakdown && (totals.shipping != null || totals.tax != null)) {
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
                    text = if (showBreakdown) {
                        "Total ($itemCount ${if (itemCount == 1) "item" else "items"})"
                    } else {
                        "$itemCount ${if (itemCount == 1) "item" else "items"}"
                    },
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = if (showBreakdown) totals.total.display() else totals.subtotal.display(),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Button(
                onClick = onAction,
                enabled = enabled && !busy,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.button),
            ) {
                if (busy) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    // The button says why it's unavailable rather than just being greyed out.
                    Text(actionLabel)
                }
            }
        }
    }
}

/** Shown when the account or member simply can't buy — the cart is a waste of time until fixed. */
@Composable
fun RefusalBanner(text: String, modifier: Modifier = Modifier) {
    QuickSaleCard(
        modifier = modifier.padding(top = Spacing.md),
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

/** One quiet line of the totals breakdown above the grand total. */
@Composable
fun SummaryRow(label: String, value: String) {
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

/** A tappable label/value row that opens a dropdown of [options]. */
@Composable
fun SelectorRow(
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

/** Formats a running total the way the store's own website writes a price. */
fun BigDecimal.display(): String = CurrencyFormatter.format(this)
