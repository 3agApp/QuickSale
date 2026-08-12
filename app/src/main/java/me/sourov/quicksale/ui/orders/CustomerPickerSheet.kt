package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.SellableCustomer
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.organizations.NewCustomerSheet
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Who this order is for — a person, picked in one tap.
 *
 * The customer is the individual, not the company: their WordPress user id is what the order is
 * stamped with, and a person belongs to exactly one organization, so their company follows from
 * them rather than the other way round. Each row carries the company underneath, which is what
 * tells two people with the same first name apart.
 *
 * Searches the device's own copy, so it answers instantly and works on fair wi-fi. Matching runs
 * over the person's name, their email *and* their company name — the operator is as likely to be
 * told "we're Böxli" as a person's name.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomerPickerSheet(
    onDismiss: () -> Unit,
    onSelect: (Customer) -> Unit,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }

    var query by remember { mutableStateOf("") }
    var creating by remember { mutableStateOf(false) }

    val results by remember(query) { container.organizations.searchSellableCustomers(query) }
        .collectAsStateWithLifecycle(initialValue = emptyList())

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.lg),
        ) {
            Text(
                text = "Who is this order for?",
                style = MaterialTheme.typography.titleLarge,
            )

            Spacer(Modifier.height(Spacing.md))
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text("Search name, email or company") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(Modifier.height(Spacing.sm))
            FilledTonalButton(
                onClick = { creating = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.button),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("New customer")
            }

            Spacer(Modifier.height(Spacing.sm))
            if (results.isEmpty()) {
                Box(Modifier.heightIn(min = 200.dp)) {
                    EmptyState(
                        icon = Icons.Filled.Groups,
                        title = if (query.isBlank()) "No customers yet" else "Nobody matches",
                        message = if (query.isBlank()) {
                            "Sync accounts, or add the customer standing in front of you."
                        } else {
                            "Nothing active matches \"$query\". Add them instead."
                        },
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(bottom = Spacing.xl),
                    modifier = Modifier.heightIn(max = 460.dp),
                ) {
                    items(results, key = { it.member.memberId }) { customer ->
                        CustomerRow(
                            customer = customer,
                            onClick = {
                                onSelect(
                                    Customer(
                                        organizationId = customer.member.organizationId,
                                        memberUserId = customer.member.userId,
                                    ),
                                )
                            },
                        )
                        HorizontalDivider()
                    }
                }
            }
        }
    }

    if (creating) {
        NewCustomerSheet(
            onDismiss = { creating = false },
            onCreated = { created ->
                creating = false
                onSelect(Customer(created.organizationId, created.memberUserId))
            },
        )
    }
}

/** One person, with the company they buy for. Tapping it is the whole choice. */
@Composable
private fun CustomerRow(
    customer: SellableCustomer,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = Spacing.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(initials = customer.member.initials, size = Sizes.avatarSmall)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = customer.member.name.ifBlank { customer.member.email },
                style = MaterialTheme.typography.titleSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = listOf(customer.organizationName, customer.organizationCity)
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Only when it adds something the two lines above don't already say.
            if (customer.member.name.isNotBlank() && customer.member.email.isNotBlank()) {
                Text(
                    text = customer.member.email,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
