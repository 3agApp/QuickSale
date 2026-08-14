package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * One person on an account.
 *
 * The page exists because the person is what an order is stamped with: the till needs a `customer_id`
 * and that is theirs, not the company's. So the useful thing to do from here is start an order for
 * them, and it sits in the bottom bar where the primary action lives everywhere else in the app.
 *
 * The company is a card rather than a heading — it's the answer to a different question ("who are
 * they buying for, and can that account trade?"), and it stays one tap from being asked properly.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MemberDetailScreen(
    viewModel: MemberDetailViewModel,
    onBack: () -> Unit,
    onOpenCompany: (organizationId: Long) -> Unit,
    onPlaceOrder: (Member) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val blocker by viewModel.blocker.collectAsStateWithLifecycle()

    var editing by rememberSaveable { mutableStateOf(false) }

    /**
     * Leave when the person we were showing is taken off the account.
     *
     * Only once they have actually been seen: an id that never matched is a genuine "not found"
     * worth saying, while somebody who was here a moment ago has just been removed — usually by the
     * sheet on this very screen — and the honest next screen is the list they came from.
     */
    var everFound by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state) {
        when (state) {
            is MemberState.Found -> everFound = true
            MemberState.Missing -> if (everFound) onBack()
            MemberState.Loading -> Unit
        }
    }

    val current = (state as? MemberState.Found)?.member

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = current?.name?.ifBlank { current.email } ?: "Person",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    // Editing is offered for anyone the page can show, including someone the store
                    // won't let order — changing exactly that is one of the things the sheet is for.
                    if (current != null) {
                        IconButton(onClick = { editing = true }) {
                            Icon(Icons.Outlined.Edit, contentDescription = "Edit person")
                        }
                    }
                },
            )
        },
        bottomBar = {
            if (current != null) {
                PlaceOrderBar(
                    member = current,
                    blocker = blocker,
                    onPlaceOrder = { onPlaceOrder(current) },
                )
            }
        },
    ) { padding ->
        if (current == null) {
            // Loading draws nothing rather than a "not found" it is about to contradict.
            if (state is MemberState.Missing) {
                EmptyState(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    icon = Icons.Outlined.Person,
                    title = "Person not found",
                    message = "They may have been removed from the account.",
                )
            }
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Spacing.screen),
        ) {
            Spacer(Modifier.height(Spacing.md))
            PersonHeader(current)

            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(title = "Access")
            Spacer(Modifier.height(Spacing.sectionGap))
            QuickSaleCard {
                Column(Modifier.padding(Spacing.md)) {
                    FactRow("Role", current.roleLabel)
                    FactRow("Membership", current.statusLabel)
                    FactRow(
                        label = "Ordering",
                        value = if (current.canPlaceOrders) "Allowed" else "Not allowed",
                        emphasis = !current.canPlaceOrders,
                    )
                }
            }

            organization?.let { org ->
                Spacer(Modifier.height(Spacing.sectionSpacing))
                SectionHeader(title = "Company")
                Spacer(Modifier.height(Spacing.sectionGap))
                CompanyCard(organization = org, onClick = { onOpenCompany(org.id) })
            }

            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(
                title = "Delivers to",
                // Said plainly, because "all locations" and "these three" are the whole difference
                // between a person who can send stock anywhere and one who cannot.
                subtitle = if (current.allowedLocationIds == null) {
                    "Every location this company has"
                } else {
                    "Only the locations listed here"
                },
            )
            Spacer(Modifier.height(Spacing.sectionGap))
            QuickSaleCard {
                Column(Modifier.padding(Spacing.md)) {
                    if (locations.isEmpty()) {
                        Text(
                            text = "No locations on this account yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        locations.forEach { LocationRow(it) }
                    }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }

    // The same sheet the company's People tab opens, so there is one idea of what editing a person
    // means. It writes through to the local copy itself, which is what this page reads — so a saved
    // change is already on screen behind the sheet as it closes, and a removal takes the page with it.
    if (editing && current != null) {
        MemberFormSheet(
            organizationId = current.organizationId,
            existing = current,
            onDismiss = { editing = false },
            onDone = { editing = false },
        )
    }
}

@Composable
private fun PersonHeader(member: Member) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Monogram(initials = member.initials, size = Sizes.avatarLarge)
        Spacer(Modifier.width(Spacing.md))
        Column(Modifier.weight(1f)) {
            Text(
                text = member.name.ifBlank { "(no name)" },
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (member.email.isNotBlank()) {
                Text(
                    text = member.email,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/** The company this person buys for. Tappable, because the account is a page of its own. */
@Composable
private fun CompanyCard(organization: Organization, onClick: () -> Unit) {
    QuickSaleCard(modifier = Modifier.clickable(onClick = onClick)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(initials = organization.initials, size = Sizes.avatar)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = organization.name.ifBlank { "(no name)" },
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                val place = listOf(organization.city, organization.country)
                    .filter { it.isNotBlank() }
                    .joinToString(", ")
                if (place.isNotBlank()) {
                    Text(
                        text = place,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(Spacing.sm))
            OrganizationStatusChip(organization.orgStatus)
            Spacer(Modifier.width(Spacing.sm))
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun LocationRow(location: OrgLocation) {
    Column(Modifier.padding(vertical = Spacing.xs)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = location.name.ifBlank { "(unnamed)" },
                style = MaterialTheme.typography.titleSmall,
            )
            if (location.isDefault) {
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "Default",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
        Text(
            text = location.singleLine,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * The one thing this page is for.
 *
 * A refusal replaces the button rather than sitting beside a disabled one, because the reason is
 * the useful half — "suspended" tells the operator what to do next, a greyed-out button doesn't.
 */
@Composable
private fun PlaceOrderBar(
    member: Member,
    blocker: OrderBlocker?,
    onPlaceOrder: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(Spacing.screen),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            blocker?.let {
                Text(
                    text = it.reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            Button(
                onClick = onPlaceOrder,
                enabled = blocker == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(Sizes.button),
            ) {
                Icon(Icons.Filled.PointOfSale, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.sm))
                Text(
                    text = "Start an order for ${member.firstName.ifBlank { "this customer" }}",
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun FactRow(label: String, value: String, emphasis: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = Spacing.xs),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(Spacing.md))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (emphasis) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            textAlign = TextAlign.End,
        )
    }
}
