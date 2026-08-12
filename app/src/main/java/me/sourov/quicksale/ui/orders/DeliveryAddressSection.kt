package me.sourov.quicksale.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.settings.AddressField
import me.sourov.quicksale.data.settings.AddressForms
import me.sourov.quicksale.ui.components.AddressChoiceField
import me.sourov.quicksale.ui.components.AddressFormFields
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Where this order is going.
 *
 * One address, not a choice between kinds of address. Picking a branch fills the form — the
 * member's default branch is already filled in when the screen opens — and every field stays
 * editable, because the counter regularly needs "the usual place, but the loading bay round the
 * back" and that is a fact about *this order*, not a correction to the company's records. Edits
 * here are never written back to the branch; the company sheet is where branches are changed.
 *
 * The switch is the walk-out sale: no destination at all, which is what WooCommerce wants when an
 * order carries no shipping lines.
 */
@Composable
fun DeliveryAddressSection(
    branches: List<OrgLocation>,
    delivery: DeliveryState,
    onDeliveryEnabledChange: (Boolean) -> Unit,
    onSelectBranch: (Long) -> Unit,
    onResetToBranch: () -> Unit,
    addressForms: AddressForms,
    country: String,
    fields: List<AddressField>,
    values: Map<String, String>,
    onSelectCountry: (String) -> Unit,
    onFieldChange: (String, String) -> Unit,
    allowCustomShipping: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        QuickSaleCard(
            containerColor = if (delivery.enabled) {
                MaterialTheme.colorScheme.surfaceContainer
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            },
        ) {
            Row(
                modifier = Modifier.padding(
                    start = Spacing.md,
                    end = Spacing.md,
                    top = Spacing.sm,
                    bottom = Spacing.sm,
                ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Outlined.Storefront,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Deliver this order",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (delivery.enabled) {
                            "Off for a counter sale they take away"
                        } else {
                            "Counter sale — nothing is being shipped"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = delivery.enabled, onCheckedChange = onDeliveryEnabledChange)
            }
        }

        AnimatedVisibility(
            visible = delivery.enabled,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = Spacing.md),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                if (branches.isNotEmpty()) {
                    AddressChoiceField(
                        label = "Branch",
                        value = branches.firstOrNull { it.id == delivery.branchId }
                            ?.let { branchLabel(it) }
                            ?: "Pick a branch to fill this in",
                        choices = branches.map { it.id.toString() to branchLabel(it) },
                        onSelect = { id -> id.toLongOrNull()?.let(onSelectBranch) },
                    )
                }

                AddressFormFields(
                    addressForms = addressForms,
                    country = country,
                    fields = fields,
                    values = values,
                    onSelectCountry = onSelectCountry,
                    onFieldChange = onFieldChange,
                )

                AnimatedVisibility(
                    visible = delivery.edited,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically(),
                ) {
                    EditedNotice(
                        allowCustomShipping = allowCustomShipping,
                        onReset = onResetToBranch,
                    )
                }
            }
        }
    }
}

/**
 * Shown once the address no longer matches its branch, so it is never a surprise that the parcel
 * is going somewhere the company's records don't mention.
 *
 * On an account that forbids custom shipping the store will refuse this address. The app says so
 * rather than blocking, because the store is the authority on the rule and its refusal names the
 * reason — but the reset is right here, one tap away.
 */
@Composable
private fun EditedNotice(allowCustomShipping: Boolean, onReset: () -> Unit) {
    QuickSaleCard(
        containerColor = if (allowCustomShipping) {
            MaterialTheme.colorScheme.surfaceContainerHigh
        } else {
            MaterialTheme.colorScheme.errorContainer
        },
    ) {
        Column(modifier = Modifier.padding(start = Spacing.md, end = Spacing.md, top = Spacing.sm)) {
            Text(
                text = if (allowCustomShipping) {
                    "Delivering somewhere else this once. The saved branch is unchanged."
                } else {
                    "This account only ships to its saved branches, so the store will refuse a " +
                        "changed address. Reset it, or edit the branch from the company sheet."
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (allowCustomShipping) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onErrorContainer
                },
            )
            Spacer(Modifier.height(Spacing.xs))
            TextButton(onClick = onReset) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.Undo,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(Spacing.sm))
                Text("Reset to the branch")
            }
        }
    }
}

/** A branch reads as its name, with the default marked so the preselected one explains itself. */
private fun branchLabel(branch: OrgLocation): String {
    val name = branch.name.ifBlank { branch.singleLine.ifBlank { "Saved branch" } }
    return if (branch.isDefault) "$name · Default" else name
}
