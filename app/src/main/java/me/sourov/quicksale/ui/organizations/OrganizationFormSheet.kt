package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.ui.components.AddressFormFields
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Editing the company itself — its name, its billing address, and whether its orders may go
 * anywhere other than a saved location.
 *
 * The billing address is the one the *store* stamps on every order for this account; it is not a
 * delivery address and changing it never changes where anything is sent. That distinction is the
 * whole reason this sheet and the location editor look alike but are kept apart.
 *
 * [onSaved] fires once the store has accepted the write.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationFormSheet(
    organization: Organization,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val viewModel: OrganizationFormViewModel = viewModel(
        key = "organization-${organization.id}",
        factory = OrganizationFormViewModel.factory(
            organization = organization,
            organizationRepository = container.organizations,
            addressFormRepository = container.addressForms,
            settingsRepository = container.settings,
        ),
    )

    val name by viewModel.name.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val allowCustomShipping by viewModel.allowCustomShipping.collectAsStateWithLifecycle()
    val addressForms by viewModel.addressForms.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val emailRequired by viewModel.emailRequired.collectAsStateWithLifecycle()
    val values by viewModel.values.collectAsStateWithLifecycle()
    val fieldErrors by viewModel.fieldErrors.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(saved) {
        if (saved) {
            onSaved()
            viewModel.consumeSaved()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
        ) {
            SectionHeader(
                title = "Edit company",
                subtitle = "What the store bills, not where it delivers",
            )

            Spacer(Modifier.height(Spacing.lg))
            // "Account name" rather than "Company name", because the billing block below
            // used to carry a second field with that exact label and nothing on the screen
            // said which one reached an invoice. The store now derives the billing company
            // from this, so this is the only place the name is ever typed.
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::setName,
                label = { Text("Account name *") },
                supportingText = {
                    Text(fieldErrors["name"] ?: "Printed on this account's invoices and delivery labels")
                },
                isError = fieldErrors["name"] != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            // The asterisk follows the store's own billing definition rather than being written in:
            // this field sits outside the address form below, so nothing else would mark it, and a
            // save refused over a blank the screen never flagged is a wasted trip to the counter.
            OutlinedTextField(
                value = email,
                onValueChange = viewModel::setEmail,
                label = { Text(if (emailRequired) "Billing email *" else "Billing email") },
                supportingText = {
                    Text(fieldErrors["email"] ?: "Where the store sends this account's invoices")
                },
                isError = fieldErrors["email"] != null,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Allow typed delivery addresses",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = if (allowCustomShipping) {
                            "An order may go to an address typed at the counter."
                        } else {
                            "Every order must go to one of this account's saved locations."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = allowCustomShipping,
                    onCheckedChange = viewModel::setAllowCustomShipping,
                )
            }

            Spacer(Modifier.height(Spacing.lg))
            SectionHeader(
                title = "Billing address",
                subtitle = "Applied by the store to every order on this account",
            )
            Spacer(Modifier.height(Spacing.sectionGap))
            AddressFormFields(
                addressForms = addressForms,
                country = country,
                fields = fields,
                values = values,
                onSelectCountry = viewModel::selectCountry,
                onFieldChange = viewModel::setField,
                billing = true,
                errors = fieldErrors,
            )

            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !saving,
                    modifier = Modifier.height(Sizes.button),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !saving,
                    modifier = Modifier
                        .weight(1f)
                        .height(Sizes.button),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary,
                        )
                    } else {
                        Text("Save company")
                    }
                }
            }
        }
    }

    error?.let { text ->
        AlertDialog(
            onDismissRequest = viewModel::consumeError,
            title = { Text("The store refused") },
            text = { Text(text) },
            confirmButton = {
                TextButton(onClick = viewModel::consumeError) { Text("OK") }
            },
        )
    }
}
