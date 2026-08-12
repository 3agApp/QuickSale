package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.LocalPhone
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.organizations.BranchFormSheet
import me.sourov.quicksale.ui.organizations.LocationRow
import me.sourov.quicksale.ui.organizations.OrganizationStatusChip
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The account behind the order in hand: who it bills to, and where it can be delivered.
 *
 * Reachable from the cart and the checkout, because the question it answers — "is this the right
 * company, and do they have the branch I'm looking for?" — comes up mid-order, and leaving the
 * order to find out means losing the cart.
 *
 * Branches are editable from here and only from here. An edit made in the checkout's delivery form
 * belongs to that one order; an edit made here is the company's record changing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySheet(
    viewModel: SellViewModel,
    onDismiss: () -> Unit,
) {
    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val branches by viewModel.allLocations.collectAsStateWithLifecycle()

    /** Null when no branch form is open; holds "add" (null branch) or the branch being edited. */
    var editing by remember { mutableStateOf<BranchEdit?>(null) }

    val current = organization ?: return

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    ) {
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen)
                .padding(bottom = Spacing.xxl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(initials = current.initials, size = 48.dp)
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = current.name,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    OrganizationStatusChip(current.orgStatus)
                }
            }

            if (current.billingFormatted.isNotBlank()) {
                Spacer(Modifier.height(Spacing.sectionSpacing))
                SectionHeader(
                    title = "Billing address",
                    subtitle = "The store applies this to every order itself",
                )
                Spacer(Modifier.height(Spacing.sectionGap))
                QuickSaleCard {
                    Text(
                        // Shown as WooCommerce prints it for its country, not assembled here.
                        text = current.billingFormatted,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            }

            if (current.email.isNotBlank() || current.phone.isNotBlank()) {
                Spacer(Modifier.height(Spacing.md))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (current.email.isNotBlank()) {
                        ContactLine(Icons.Outlined.MailOutline, current.email)
                    }
                    if (current.phone.isNotBlank()) {
                        ContactLine(Icons.Outlined.LocalPhone, current.phone)
                    }
                }
            }

            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(
                title = "Branches",
                subtitle = if (current.allowCustomShipping) {
                    "Saved on the account. An order may also go to a typed address."
                } else {
                    "Saved on the account. Orders must go to one of these."
                },
            )
            Spacer(Modifier.height(Spacing.sectionGap))

            if (branches.isEmpty()) {
                QuickSaleCard {
                    Text(
                        text = "No branches yet. Add one and every order can be sent there.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    branches.forEach { branch ->
                        LocationRow(
                            location = branch,
                            onEdit = { editing = BranchEdit(branch) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(Spacing.md))
            OutlinedButton(
                onClick = { editing = BranchEdit(null) },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text("Add a branch")
            }
        }
    }

    editing?.let { edit ->
        BranchFormSheet(
            organizationId = current.id,
            existing = edit.branch,
            onDismiss = { editing = null },
            // The branch form applies the saved row locally, which is what makes it appear in
            // this list — and in the delivery picker of the order being built behind it.
            onSaved = { editing = null },
        )
    }
}

/** Which branch form is open: [branch] is null when adding a new one. */
private data class BranchEdit(val branch: OrgLocation?)

@Composable
private fun ContactLine(icon: ImageVector, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.width(Spacing.sm))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
