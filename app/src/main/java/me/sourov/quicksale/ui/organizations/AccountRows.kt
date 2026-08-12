package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.ui.components.IconBadge
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.StatusChip
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * The rows that describe an account, shared by every screen that shows one: the detail screen, the
 * review queue and the company sheet on the cart.
 */

/**
 * One person who may buy on the account's behalf.
 *
 * `can_place_orders` is the store's own resolved answer and is shown as given rather than re-derived
 * from role and status. The organization's status gates it too: an active member of a suspended
 * account still can't buy.
 */
@Composable
fun MemberRow(
    member: Member,
    organizationCanTrade: Boolean,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val canOrder = member.canPlaceOrders && organizationCanTrade
    QuickSaleCard(
        modifier = modifier.let {
            if (onClick != null) it.clickable(enabled = canOrder, onClick = onClick) else it
        },
    ) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(
                initials = member.initials,
                containerColor = if (canOrder) {
                    MaterialTheme.colorScheme.primaryContainer
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                },
                contentColor = if (canOrder) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = member.name.ifBlank { member.email },
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (member.isAdmin) {
                        Spacer(Modifier.width(Spacing.sm))
                        StatusChip(
                            label = member.roleLabel,
                            containerColor = MaterialTheme.colorScheme.secondaryContainer,
                            contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                        )
                    }
                }
                if (member.email.isNotBlank() && member.name.isNotBlank()) {
                    Text(
                        text = member.email,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (!canOrder) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = if (!organizationCanTrade) {
                            "Blocked by the account's status"
                        } else {
                            "Not allowed to place orders"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                } else {
                    member.allowedLocationIds?.let { allowed ->
                        Spacer(Modifier.height(Spacing.xs))
                        Text(
                            text = "Can deliver to ${allowed.size} of the saved branches",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            if (canOrder && onClick != null) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = "Start an order for ${member.name}",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(Sizes.iconLarge),
                )
            }
        }
    }
}

/**
 * One saved branch. The address is shown exactly as WooCommerce formats it for its country —
 * postcode before the city in Germany, after it in the US — rather than assembled here.
 *
 * [onEdit] is supplied only where branches are actually editable (the company sheet); elsewhere the
 * row is a read-only record of where deliveries go.
 */
@Composable
fun LocationRow(
    location: OrgLocation,
    modifier: Modifier = Modifier,
    onEdit: (() -> Unit)? = null,
) {
    QuickSaleCard(modifier = modifier) {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.Top,
        ) {
            IconBadge(
                icon = Icons.Outlined.Place,
                size = Sizes.avatarSmall,
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = location.name,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    if (location.isDefault) {
                        Spacer(Modifier.width(Spacing.sm))
                        StatusChip(
                            label = "Default",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer,
                            icon = Icons.Outlined.Star,
                        )
                    }
                }
                if (location.formatted.isNotBlank()) {
                    Spacer(Modifier.height(Spacing.xs))
                    Text(
                        text = location.formatted,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            onEdit?.let {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = "Edit ${location.name}",
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        }
    }
}
