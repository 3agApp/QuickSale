package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.ui.components.AddressFormFields
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Adds or edits a saved branch, writing straight to the store.
 *
 * This is the address editor whose changes *stick*: the delivery form on the checkout only ever
 * describes the order in hand. The sheet says so out loud, because two address forms that look
 * alike and behave differently is exactly the sort of thing a counter learns the hard way.
 *
 * [onSaved] fires after the store has accepted the write, so the caller can resync and close.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BranchFormSheet(
    organizationId: Long,
    existing: OrgLocation?,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val viewModel: BranchFormViewModel = viewModel(
        // The key keeps "add" and "edit branch 3" from sharing one instance across openings.
        key = "branch-${organizationId}-${existing?.id ?: 0L}",
        factory = BranchFormViewModel.factory(
            organizationId = organizationId,
            existing = existing,
            organizationRepository = container.organizations,
            addressFormRepository = container.addressForms,
            settingsRepository = container.settings,
        ),
    )

    val name by viewModel.name.collectAsStateWithLifecycle()
    val isDefault by viewModel.isDefault.collectAsStateWithLifecycle()
    val addressForms by viewModel.addressForms.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val values by viewModel.values.collectAsStateWithLifecycle()
    val fieldErrors by viewModel.fieldErrors.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val saved by viewModel.saved.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(saved) { if (saved) onSaved() }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                // Save/Cancel are at the bottom of this scroll; the keyboard must push them, not
                // cover them.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
        ) {
            SectionHeader(
                title = if (viewModel.isEditing) "Edit branch" else "Add a branch",
                subtitle = "Saved on the account — every future order can be sent here",
            )

            Spacer(Modifier.height(Spacing.lg))
            OutlinedTextField(
                value = name,
                onValueChange = viewModel::setName,
                label = { Text("Branch name *") },
                supportingText = {
                    Text(fieldErrors["name"] ?: "What the counter picks it by — \"Warehouse North\"")
                },
                isError = fieldErrors["name"] != null,
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = "Default branch",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        // Setting it on one clears it on the others, server-side.
                        text = "New orders start here. Only one branch can be the default.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = isDefault, onCheckedChange = viewModel::setDefault)
            }

            Spacer(Modifier.height(Spacing.lg))
            AddressFormFields(
                addressForms = addressForms,
                country = country,
                fields = fields,
                values = values,
                onSelectCountry = viewModel::selectCountry,
                onFieldChange = viewModel::setField,
                errors = fieldErrors,
            )

            Spacer(Modifier.height(Spacing.sm))
            Text(
                // Both are true of the route and neither is obvious from a checkout's own rules.
                text = "A surname and a phone aren't required. Leave the company blank and the " +
                    "store fills in the account's own name.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                        Text(if (viewModel.isEditing) "Save branch" else "Add branch")
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
