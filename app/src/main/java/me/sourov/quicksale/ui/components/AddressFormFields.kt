package me.sourov.quicksale.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.data.settings.AddressField
import me.sourov.quicksale.data.settings.AddressForms
import me.sourov.quicksale.ui.theme.Spacing

/**
 * An address form rendered from the shop's own per-country field definitions.
 *
 * Nothing here is hand-written: the fields, their order, their labels and whether they're required
 * all come from WooCommerce, because a hand-written address form is wrong in a different way in
 * every country. Client-side validation stops at marking required fields — the store applies the
 * real rules (postcode format, states from the country's list) and its answers are authoritative.
 *
 * Both places the app composes an address use this: the delivery address on checkout, and the
 * branch editor. They are the same field definitions, so they must look and behave the same.
 *
 * [errors] maps a field name to the store's own reason for refusing it, taken from `data.params`.
 */
@Composable
fun AddressFormFields(
    addressForms: AddressForms,
    country: String,
    fields: List<AddressField>,
    values: Map<String, String>,
    onSelectCountry: (String) -> Unit,
    onFieldChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    errors: Map<String, String> = emptyMap(),
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        if (addressForms.isEmpty) {
            Text(
                text = "Address forms haven't synced yet — sync accounts to fill one in.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            return@Column
        }

        fields.forEach { field ->
            val error = errors[field.name]
            when {
                // The country picker drives which form is rendered, so it gets its own control.
                field.type == "country" -> AddressChoiceField(
                    label = field.label.ifBlank { "Country" },
                    value = addressForms.countryName(country),
                    choices = addressForms.countryChoices,
                    onSelect = onSelectCountry,
                    required = field.required,
                    enabled = enabled,
                    error = error,
                )

                // A state list exists only where the country has one; elsewhere free text is what
                // the shop's own checkout renders too.
                field.hasOptions -> AddressChoiceField(
                    label = field.label,
                    value = field.options[values[field.name]].orEmpty(),
                    choices = field.options.entries.map { it.key to it.value },
                    onSelect = { code -> onFieldChange(field.name, code) },
                    required = field.required,
                    enabled = enabled,
                    error = error,
                )

                else -> OutlinedTextField(
                    value = values[field.name].orEmpty(),
                    onValueChange = { onFieldChange(field.name, it) },
                    label = { Text(field.label + if (field.required) " *" else "") },
                    singleLine = true,
                    enabled = enabled,
                    isError = error != null,
                    supportingText = error?.let { { Text(it) } },
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
fun AddressChoiceField(
    label: String,
    value: String,
    choices: List<Pair<String, String>>,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    required: Boolean = false,
    enabled: Boolean = true,
    error: String? = null,
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { if (enabled) expanded = it },
        modifier = modifier.fillMaxWidth(),
    ) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            isError = error != null,
            supportingText = error?.let { { Text(it) } },
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
