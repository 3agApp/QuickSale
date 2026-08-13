package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.ui.components.AddressFormFields
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Adding a trade customer at the stand.
 *
 * Two questions, in the order the conversation actually happens: who are you, and who do you buy
 * for. The second usually resolves to a company already in the list, which makes the whole thing
 * three fields and a tap; when it doesn't, the company is created alongside them.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewCustomerSheet(
    onDismiss: () -> Unit,
    onCreated: (CreatedCustomer) -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val viewModel: NewCustomerViewModel = viewModel(
        factory = NewCustomerViewModel.factory(
            organizationRepository = container.organizations,
            addressFormRepository = container.addressForms,
            settingsRepository = container.settings,
        ),
    )

    val firstName by viewModel.firstName.collectAsStateWithLifecycle()
    val lastName by viewModel.lastName.collectAsStateWithLifecycle()
    val email by viewModel.email.collectAsStateWithLifecycle()
    val choice by viewModel.choice.collectAsStateWithLifecycle()
    val companyQuery by viewModel.companyQuery.collectAsStateWithLifecycle()
    val companyMatches by viewModel.companyMatches.collectAsStateWithLifecycle()
    val companyName by viewModel.companyName.collectAsStateWithLifecycle()
    val addressForms by viewModel.addressForms.collectAsStateWithLifecycle()
    val country by viewModel.country.collectAsStateWithLifecycle()
    val fields by viewModel.fields.collectAsStateWithLifecycle()
    val values by viewModel.values.collectAsStateWithLifecycle()
    val fieldErrors by viewModel.fieldErrors.collectAsStateWithLifecycle()
    val personComplete by viewModel.personComplete.collectAsStateWithLifecycle()
    val saving by viewModel.saving.collectAsStateWithLifecycle()
    val created by viewModel.created.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(created) { created?.let(onCreated) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                // The form's own Save/Cancel sit at the bottom of this scroll, and without this
                // the keyboard that fills the fields also hides the button that commits them.
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
        ) {
            SectionHeader(
                title = "New customer",
                subtitle = "They can order the moment this is saved",
            )

            Spacer(Modifier.height(Spacing.lg))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(
                    value = firstName,
                    onValueChange = viewModel::setFirstName,
                    label = { Text("First name *") },
                    isError = fieldErrors["first_name"] != null,
                    supportingText = fieldErrors["first_name"]?.let { { Text(it) } },
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
                isError = fieldErrors["email"] != null,
                supportingText = {
                    Text(
                        fieldErrors["email"]
                            ?: "Becomes their login. The shop mails them a password to set.",
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(
                title = "Company",
                subtitle = "Who they buy for. Every order belongs to one.",
            )
            Spacer(Modifier.height(Spacing.sectionGap))

            when (val current = choice) {
                null -> CompanyChooser(
                    query = companyQuery,
                    matches = companyMatches,
                    enabled = personComplete,
                    onQueryChange = viewModel::setCompanyQuery,
                    onChoose = viewModel::chooseExisting,
                    onCreateNew = viewModel::chooseNewCompany,
                )

                is CompanyChoice.Existing -> ChosenCompany(
                    name = current.organization.name,
                    detail = current.organization.city,
                    onChange = viewModel::clearChoice,
                )

                CompanyChoice.New -> {
                    ChosenCompany(
                        name = "A new company",
                        detail = "Created with this customer",
                        onChange = viewModel::clearChoice,
                    )
                    Spacer(Modifier.height(Spacing.md))
                    OutlinedTextField(
                        value = companyName,
                        onValueChange = viewModel::setCompanyName,
                        label = { Text("Company name *") },
                        isError = fieldErrors["name"] != null,
                        supportingText = fieldErrors["name"]?.let { { Text(it) } },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(Spacing.md))
                    Text(
                        text = "Billing address",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Spacer(Modifier.height(Spacing.sm))
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
                }
            }

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
                    enabled = !saving && choice != null && personComplete,
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
                        Text("Add customer")
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

/**
 * Search the companies already known, or start a new one.
 *
 * Disabled until the person is filled in, because choosing a company is the second half of a
 * sentence — offering it first invites the operator to answer the questions out of order and then
 * discover the name field was the required one.
 */
@Composable
private fun CompanyChooser(
    query: String,
    matches: List<me.sourov.quicksale.data.local.Organization>,
    enabled: Boolean,
    onQueryChange: (String) -> Unit,
    onChoose: (me.sourov.quicksale.data.local.Organization) -> Unit,
    onCreateNew: () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            enabled = enabled,
            placeholder = { Text("Search companies") },
            leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
            supportingText = if (enabled) null else {
                { Text("Fill in the name and email first") }
            },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(Modifier.height(Spacing.sm))
        TextButton(onClick = onCreateNew, enabled = enabled) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text("Their company isn't listed — create it")
        }

        if (enabled && matches.isNotEmpty()) {
            Spacer(Modifier.height(Spacing.sm))
            // Bounded rather than scrollable: this sits inside the sheet's own scroll, and a
            // nested scrolling list here is a scroll trap.
            Column(Modifier.heightIn(max = 280.dp)) {
                matches.take(MAX_VISIBLE_COMPANIES).forEach { organization ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onChoose(organization) }
                            .padding(vertical = Spacing.md),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Monogram(initials = organization.initials, size = Sizes.avatarSmall)
                        Spacer(Modifier.width(Spacing.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = organization.name,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (organization.city.isNotBlank()) {
                                Text(
                                    text = organization.city,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                }
            }
        }
    }
}

/** The settled answer, with the way to change it. */
@Composable
private fun ChosenCompany(
    name: String,
    detail: String,
    onChange: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Filled.Business,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(Sizes.icon),
        )
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onChange) { Text("Change") }
    }
}

/** More than this and the sheet stops being a quick answer; refine the search instead. */
private const val MAX_VISIBLE_COMPANIES = 8
