package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonRemove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Adding a person to a company, or changing what an existing one may do.
 *
 * Adding asks for a name and an email because the store makes a real login out of them — the
 * person can sign in to the website afterwards. Editing shows neither: those belong to their
 * WordPress account rather than to this membership, and offering them here would imply the app
 * could change a login it cannot.
 *
 * [onDone] fires once the store has accepted a save or a removal.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberFormSheet(
    organizationId: Long,
    existing: Member?,
    onDismiss: () -> Unit,
    onDone: () -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val viewModel: MemberFormViewModel = viewModel(
        key = "member-$organizationId-${existing?.memberId ?: 0L}",
        factory = MemberFormViewModel.factory(
            organizationId = organizationId,
            existing = existing,
            organizationRepository = container.organizations,
            settingsRepository = container.settings,
        ),
    )

    val firstName by viewModel.firstName.collectAsStateWithLifecycle()
    val lastName by viewModel.lastName.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val isActive by viewModel.isActive.collectAsStateWithLifecycle()
    val allowedLocations by viewModel.allowedLocations.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val fieldErrors by viewModel.fieldErrors.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val removing by viewModel.removing.collectAsStateWithLifecycle()
    val done by viewModel.done.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    var confirmingRemoval by remember { mutableStateOf(false) }
    val busy = saving || removing

    LaunchedEffect(done) {
        if (done) {
            onDone()
            viewModel.consumeDone()
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
                title = if (viewModel.isEditing) "Edit person" else "Add a person",
                subtitle = existing?.name?.takeIf { it.isNotBlank() }
                    ?: existing?.email
                    ?: "They get a login and can order for this company",
            )

            if (!viewModel.isEditing) {
                Spacer(Modifier.height(Spacing.lg))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    OutlinedTextField(
                        value = firstName,
                        onValueChange = viewModel::setFirstName,
                        label = { Text("First name *") },
                        supportingText = fieldErrors["first_name"]?.let { { Text(it) } },
                        isError = fieldErrors["first_name"] != null,
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        value = lastName,
                        onValueChange = viewModel::setLastName,
                        label = { Text("Last name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                }

                Spacer(Modifier.height(Spacing.sm))
                OutlinedTextField(
                    value = email,
                    onValueChange = viewModel::setEmail,
                    label = { Text("Email *") },
                    supportingText = {
                        Text(fieldErrors["email"] ?: "Becomes their login on the website")
                    },
                    isError = fieldErrors["email"] != null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(Spacing.md))
            ToggleRow(
                title = "Company administrator",
                detail = if (isAdmin) {
                    "Can manage this company, its people and its locations."
                } else {
                    "Can place orders, but not change the account."
                },
                checked = isAdmin,
                onCheckedChange = viewModel::setAdmin,
            )

            if (viewModel.isEditing) {
                Spacer(Modifier.height(Spacing.sm))
                ToggleRow(
                    title = "Active",
                    detail = if (isActive) {
                        "May place orders for this company."
                    } else {
                        "Switched off — the store will refuse their orders."
                    },
                    checked = isActive,
                    onCheckedChange = viewModel::setActive,
                )
            }

            if (locations.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.lg))
                SectionHeader(
                    title = "Where they may send orders",
                    subtitle = "Deliveries only — a counter sale needs no location",
                )
                Spacer(Modifier.height(Spacing.sectionGap))
                ToggleRow(
                    title = "Every location",
                    detail = "Including any added to this company later.",
                    checked = allowedLocations == null,
                    onCheckedChange = viewModel::setUnrestricted,
                )
                if (allowedLocations != null) {
                    Spacer(Modifier.height(Spacing.xs))
                    locations.forEach { location ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.toggleLocation(location.id) }
                                .padding(vertical = Spacing.xs),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = location.id in allowedLocations.orEmpty(),
                                onCheckedChange = { viewModel.toggleLocation(location.id) },
                            )
                            Spacer(Modifier.width(Spacing.xs))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    text = location.name,
                                    style = MaterialTheme.typography.bodyLarge,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = location.singleLine,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (allowedLocations.orEmpty().isEmpty()) {
                        Text(
                            // Saying so beats saving a restriction that means the opposite: the
                            // store reads an empty access list as no restriction at all.
                            text = "Nothing ticked means every location, which is what the store " +
                                "stores an empty list as.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.md)) {
                TextButton(
                    onClick = onDismiss,
                    enabled = !busy,
                    modifier = Modifier.height(Sizes.button),
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = viewModel::save,
                    enabled = !busy,
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
                        Text(if (viewModel.isEditing) "Save person" else "Add person")
                    }
                }
            }

            if (viewModel.isEditing) {
                Spacer(Modifier.height(Spacing.sm))
                TextButton(
                    onClick = { confirmingRemoval = true },
                    enabled = !busy,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.Outlined.PersonRemove,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text(
                        text = "Remove from this company",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (confirmingRemoval && existing != null) {
        AlertDialog(
            onDismissRequest = { confirmingRemoval = false },
            title = { Text("Remove ${existing.name.ifBlank { existing.email }}?") },
            text = {
                Text(
                    "They keep their login but can no longer order for this company. The store " +
                        "refuses this if they are the last administrator.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingRemoval = false
                        viewModel.remove()
                    },
                ) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRemoval = false }) { Text("Keep them") }
            },
        )
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

/** A titled switch with a line underneath saying what the current position means. */
@Composable
private fun ToggleRow(
    title: String,
    detail: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
