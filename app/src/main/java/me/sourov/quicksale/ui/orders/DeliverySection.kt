package me.sourov.quicksale.ui.orders

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditLocationAlt
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.settings.AddressField
import me.sourov.quicksale.data.settings.AddressForms
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Where the order is going.
 *
 * Three shapes, matching what the store accepts: one of the organization's saved locations
 * (resolved by ID, scoped to this member's access list), a typed one-off address (only when the
 * organization allows it), or no delivery at all for a walk-out sale.
 */
@Composable
fun DeliverySection(
    locations: List<OrgLocation>,
    delivery: DeliveryChoice,
    onSelectDelivery: (DeliveryChoice) -> Unit,
    allowCustomShipping: Boolean,
    addressForms: AddressForms,
    oneOffCountry: String,
    oneOffFields: List<AddressField>,
    oneOffValues: Map<String, String>,
    onSelectCountry: (String) -> Unit,
    onFieldChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        locations.forEach { location ->
            DeliveryOption(
                selected = (delivery as? DeliveryChoice.AtLocation)?.locationId == location.id,
                onSelect = { onSelectDelivery(DeliveryChoice.AtLocation(location.id)) },
                icon = Icons.Outlined.Place,
                title = location.name.ifBlank { "Saved location" },
                subtitle = location.singleLine,
                badge = if (location.isDefault) "Default" else null,
            )
        }

        if (allowCustomShipping) {
            DeliveryOption(
                selected = delivery is DeliveryChoice.OneOffAddress,
                onSelect = { onSelectDelivery(DeliveryChoice.OneOffAddress) },
                icon = Icons.Outlined.EditLocationAlt,
                title = "One-off address",
                subtitle = "Deliver somewhere else, just this once",
            )
        }

        DeliveryOption(
            selected = delivery is DeliveryChoice.None,
            onSelect = { onSelectDelivery(DeliveryChoice.None) },
            icon = Icons.Outlined.Storefront,
            title = "No delivery",
            subtitle = "Taking it away from the counter",
        )

        AnimatedVisibility(
            visible = delivery is DeliveryChoice.OneOffAddress && allowCustomShipping,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            OneOffAddressForm(
                addressForms = addressForms,
                country = oneOffCountry,
                fields = oneOffFields,
                values = oneOffValues,
                onSelectCountry = onSelectCountry,
                onFieldChange = onFieldChange,
            )
        }
    }
}

@Composable
private fun DeliveryOption(
    selected: Boolean,
    onSelect: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    badge: String? = null,
) {
    QuickSaleCard(
        modifier = Modifier
            .padding(bottom = Spacing.sm)
            .clickable(onClick = onSelect),
        containerColor = if (selected) {
            MaterialTheme.colorScheme.secondaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainer
        },
    ) {
        Row(
            modifier = Modifier.padding(end = Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            RadioButton(selected = selected, onClick = onSelect)
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f).padding(vertical = Spacing.md)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    badge?.let {
                        Spacer(Modifier.width(Spacing.sm))
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
                if (subtitle.isNotBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

/**
 * The one-off address form, rendered from the shop's own per-country field definitions.
 *
 * Nothing here is hand-written: the fields, their order, their labels and whether they're required
 * all come from WooCommerce, because a hand-written address form is wrong in a different way in
 * every country. Validation stops at marking required fields — the store applies its real rules
 * (postcode format, states from the country's list) and its answers are the authoritative ones.
 */
@Composable
private fun OneOffAddressForm(
    addressForms: AddressForms,
    country: String,
    fields: List<AddressField>,
    values: Map<String, String>,
    onSelectCountry: (String) -> Unit,
    onFieldChange: (String, String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Spacing.xs, bottom = Spacing.md),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (addressForms.isEmpty) {
            Text(
                text = "Address forms haven't synced yet — sync accounts, or pick a saved location.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        fields.forEach { field ->
            when {
                // The country picker drives which form is rendered, so it gets its own control.
                field.type == "country" -> ChoiceField(
                    label = field.label.ifBlank { "Country" },
                    value = addressForms.countryName(country),
                    choices = addressForms.countryChoices,
                    onSelect = onSelectCountry,
                    required = field.required,
                )

                // A state list exists only where the country has one; elsewhere free text is what
                // the shop's own checkout renders too.
                field.hasOptions -> ChoiceField(
                    label = field.label,
                    value = field.options[values[field.name]].orEmpty(),
                    choices = field.options.entries.map { it.key to it.value },
                    onSelect = { code -> onFieldChange(field.name, code) },
                    required = field.required,
                )

                else -> OutlinedTextField(
                    value = values[field.name].orEmpty(),
                    onValueChange = { onFieldChange(field.name, it) },
                    label = { Text(field.label + if (field.required) " *" else "") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (field.type) {
                            "tel" -> KeyboardType.Phone
                            "email" -> KeyboardType.Email
                            else -> KeyboardType.Text
                        },
                        imeAction = ImeAction.Next,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/** A labelled dropdown over a code→name list, submitting the code rather than the display name. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    required: Boolean,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = Modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            label = { Text(label + if (required) " *" else "") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.heightIn(max = 360.dp),
        ) {
            choices.forEach { (code, name) ->
                DropdownMenuItem(
                    text = { Text(name) },
                    onClick = {
                        expanded = false
                        onSelect(code)
                    },
                )
            }
        }
    }
}
