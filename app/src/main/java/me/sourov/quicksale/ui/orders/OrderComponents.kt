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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.ui.CurrencyFormatter
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing
import java.math.BigDecimal

/** Pieces the cart and the checkout both use, so the two pages read as one flow. */

/** How wide the totals bar's action may grow before the total starts losing room. */
private val ACTION_MAX_WIDTH = 180.dp

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
                // Whatever the shell has not already accounted for — see `consumeWindowInsets` in
                // QuickSaleApp. On the till that is nothing but the keyboard's own extra height,
                // so the bar lands exactly on top of the keyboard rather than a bar's height
                // above it; on the checkout, which hides the shell's chrome, it is the system
                // navigation bar too.
                .navigationBarsPadding()
                .imePadding()
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
            // Total and action share a line rather than stacking. Stacked they cost ~38dp above a
            // full-width button, on the one screen — the till — that has the least to spare.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "$itemCount ${if (itemCount == 1) "item" else "items"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = if (showBreakdown) {
                            totals.total.display()
                        } else {
                            totals.subtotal.display()
                        },
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Spacer(Modifier.width(Spacing.md))
                Button(
                    onClick = onAction,
                    enabled = enabled && !busy,
                    // Capped rather than free: the label doubles as the refusal message
                    // ("Choose a customer first"), and an unbounded one would eat the total.
                    modifier = Modifier
                        .widthIn(max = ACTION_MAX_WIDTH)
                        .heightIn(min = Sizes.button),
                ) {
                    if (busy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        // The button says why it's unavailable rather than just being greyed out.
                        Text(actionLabel, maxLines = 2, textAlign = TextAlign.Center)
                    }
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

/**
 * WooCommerce's `date_created_gmt` (`2024-03-21T13:13:13`, no offset — it's already UTC) as a
 * short, locale-formatted date **in the device's own time zone**. Unparsable input is shown
 * verbatim rather than blanked, since a raw stamp is still more useful than nothing.
 *
 * The conversion is not cosmetic. This used to print the UTC stamp as if it were local, which put
 * an order stamped "3:43 AM" under a *Yesterday* heading — the heading and the time on the row
 * disagreeing about which day it was.
 */
fun String.toOrderDateLabel(): String =
    toOrderLocalDateTime()
        ?.format(java.time.format.DateTimeFormatter.ofPattern("MMM d, yyyy · h:mm a"))
        ?: this

/**
 * The same stamp as the calendar day it falls on *here*, or null when it can't be parsed.
 *
 * The store reports UTC; a fair runs on local time. Grouping an order taken at 9pm in Berlin under
 * the previous day — which comparing the raw UTC text would do — is the kind of small wrongness
 * that makes an operator stop trusting the screen.
 */
fun String.toOrderLocalDate(): java.time.LocalDate? = toOrderLocalDateTime()?.toLocalDate()

/** The UTC stamp moved into the device's zone, or null when it can't be parsed. */
private fun String.toOrderLocalDateTime(): java.time.LocalDateTime? =
    runCatching {
        java.time.LocalDateTime.parse(this)
            .atZone(java.time.ZoneOffset.UTC)
            .withZoneSameInstant(java.time.ZoneId.systemDefault())
            .toLocalDateTime()
    }.getOrNull()
