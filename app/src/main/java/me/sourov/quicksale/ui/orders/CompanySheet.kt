package me.sourov.quicksale.ui.orders

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.organizations.LocationFormSheet
import me.sourov.quicksale.ui.organizations.LocationRow
import me.sourov.quicksale.ui.organizations.MemberRow
import me.sourov.quicksale.ui.organizations.OrganizationStatusChip
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The account behind the order in hand, in the order the counter asks about it: who is buying, who
 * it bills to, and where it can be delivered.
 *
 * Reachable from the cart and the checkout, because those questions come up mid-order and leaving
 * the order to answer them means losing the cart.
 *
 * The three sections are built from the same rows the Accounts screens use — [MemberRow] for the
 * person, [LocationRow] for each address — so an account reads the same here as it does there.
 *
 * Locations are editable from here and only from here. An edit made in the checkout's delivery form
 * belongs to that one order; an edit made here is the company's record changing.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompanySheet(
    viewModel: SellViewModel,
    onDismiss: () -> Unit,
) {
    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val member by viewModel.member.collectAsStateWithLifecycle()
    val locations by viewModel.allLocations.collectAsStateWithLifecycle()

    /** Null when no location form is open; holds "add" (null location) or the location being edited. */
    var editing by remember { mutableStateOf<LocationEdit?>(null) }

    val current = organization ?: return

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
            // The person first: on a till "whose order is this" is the question being checked, and
            // the company follows from them rather than the other way round.
            member?.let { who ->
                SectionHeader(title = "Customer", subtitle = "Who this order is for")
                Spacer(Modifier.height(Spacing.sectionGap))
                MemberRow(member = who, organizationCanTrade = current.orgStatus.canTrade)
                Spacer(Modifier.height(Spacing.sectionSpacing))
            }

            SectionHeader(title = "Company", subtitle = "What the store bills")
            Spacer(Modifier.height(Spacing.sectionGap))
            CompanyCard(current)

            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(
                title = if (locations.isEmpty()) "Locations" else "Locations ${locations.size}",
                subtitle = if (current.allowCustomShipping) {
                    "Saved on the account. An order may also go to a typed address."
                } else {
                    "Saved on the account. Orders must go to one of these."
                },
                trailing = {
                    TextButton(onClick = { editing = LocationEdit(null) }) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(Spacing.xs))
                        Text("Add")
                    }
                },
            )
            Spacer(Modifier.height(Spacing.sectionGap))

            if (locations.isEmpty()) {
                QuickSaleCard {
                    Text(
                        text = "No locations yet. Add one and every order can be sent there.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.md),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    // Every location the company owns, not only the ones this member may choose:
                    // this is a view of the account rather than of the order being built.
                    locations.forEach { location ->
                        LocationRow(
                            location = location,
                            onEdit = { editing = LocationEdit(location) },
                        )
                    }
                }
            }
        }
    }

    editing?.let { edit ->
        LocationFormSheet(
            organizationId = current.id,
            existing = edit.location,
            onDismiss = { editing = null },
            // The location form applies the saved row locally, which is what makes it appear in
            // this list — and in the delivery picker of the order being built behind it.
            onSaved = { editing = null },
        )
    }
}

/** Which location form is open: [location] is null when adding a new one. */
private data class LocationEdit(val location: OrgLocation?)

/**
 * The company as one card: who it is, whether it may trade, and the address the store puts on every
 * order. Split by dividers rather than into separate cards, because in a sheet the three parts are
 * one answer to one question.
 */
@Composable
private fun CompanyCard(organization: Organization) {
    QuickSaleCard {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(initials = organization.initials, size = Sizes.avatarSmall)
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = organization.name,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(Spacing.sm))
                OrganizationStatusChip(organization.orgStatus)
            }

            HorizontalDivider(Modifier.padding(vertical = Spacing.md))

            Text(
                // Shown exactly as WooCommerce prints it for the country — postcode before the city
                // in Germany, after it in the US — rather than assembled here.
                text = organization.billingFormatted.ifBlank { "No billing address on this account." },
                style = MaterialTheme.typography.bodyMedium,
                color = if (organization.billingFormatted.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            if (organization.email.isNotBlank()) {
                Spacer(Modifier.height(Spacing.md))
                ContactLine(Icons.Outlined.MailOutline, organization.email)
            }
            if (organization.phone.isNotBlank()) {
                Spacer(Modifier.height(Spacing.xs))
                ContactLine(Icons.Outlined.LocalPhone, organization.phone)
            }
        }
    }
}

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
