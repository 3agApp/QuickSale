package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.LocalPhone
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationStatus
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * One account, in the three parts it actually has.
 *
 * These used to run together down a single scroll — the company's own details, the people who buy
 * for it, and the addresses it takes delivery at — with nothing saying where one ended and the next
 * began. They are tabs now because they are three different subjects: the company is a billing
 * record, a person is a login, a location is somewhere a van goes. Each tab carries its own add and
 * edit, so managing one never means scrolling past the other two.
 *
 * Tapping a person opens that person, rather than starting their order. Ordering lives on their own
 * page now, alongside what the store will actually let them do — a company's list of people is a
 * place you arrive to *look* something up, and one tap that quietly re-pointed the till was too
 * heavy an outcome for a row you might only have meant to read. Editing stays a separate tap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrganizationDetailScreen(
    organizationId: Long,
    onOpenMember: (memberUserId: Long) -> Unit,
    onViewOrders: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val viewModel: OrganizationDetailViewModel = viewModel(
        factory = OrganizationDetailViewModel.factory(
            organizationId = organizationId,
            repository = container.organizations,
            settingsRepository = container.settings,
        ),
    )

    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val message by viewModel.message.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val syncState by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()

    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(message) {
        message?.let {
            snackbar.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    var tab by rememberSaveable { mutableStateOf(AccountTab.COMPANY) }
    var editingCompany by remember { mutableStateOf(false) }
    var editingStatus by remember { mutableStateOf(false) }
    /** Null when no person form is open; holds "add" (null member) or the one being edited. */
    var editingMember by remember { mutableStateOf<MemberEdit?>(null) }
    /** The same again for locations. */
    var editingLocation by remember { mutableStateOf<LocationEdit?>(null) }

    val current = organization
    if (current == null) {
        // Either still loading, or the last snapshot dropped it — a snapshot answers deletions by
        // omission, so an organization that vanished really is gone from the store.
        if (syncState.isRunning) {
            LoadingState(modifier)
        } else {
            EmptyState(
                modifier = modifier,
                icon = Icons.Filled.Business,
                title = "Account not found",
                message = "It may have been removed from your store. Sync to check.",
                actionLabel = "Sync accounts",
                onAction = { SyncManager.syncOrganizations(context) },
            )
        }
        return
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = current.name,
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
                    IconButton(onClick = onViewOrders) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ReceiptLong,
                            contentDescription = "This account's orders",
                        )
                    }
                    AccountMenu(
                        onEditCompany = { editingCompany = true },
                        onChangeStatus = { editingStatus = true },
                    )
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AccountIdentity(organization = current)

            TabRow(selectedTabIndex = tab.ordinal) {
                AccountTab.entries.forEach { entry ->
                    Tab(
                        selected = entry == tab,
                        onClick = { tab = entry },
                        text = {
                            Text(
                                text = entry.labelWith(
                                    when (entry) {
                                        AccountTab.COMPANY -> null
                                        AccountTab.PEOPLE -> members.size
                                        AccountTab.LOCATIONS -> locations.size
                                    },
                                ),
                                maxLines = 1,
                            )
                        },
                    )
                }
            }

            when (tab) {
                AccountTab.COMPANY -> CompanyTab(
                    organization = current,
                    onEdit = { editingCompany = true },
                )

                AccountTab.PEOPLE -> PeopleTab(
                    members = members,
                    organizationCanTrade = current.orgStatus.canTrade,
                    onOpenMember = onOpenMember,
                    onEdit = { editingMember = MemberEdit(it) },
                    onAdd = { editingMember = MemberEdit(null) },
                )

                AccountTab.LOCATIONS -> LocationsTab(
                    locations = locations,
                    allowCustomShipping = current.allowCustomShipping,
                    onEdit = { editingLocation = LocationEdit(it) },
                    onAdd = { editingLocation = LocationEdit(null) },
                )
            }
        }
    }

    if (editingCompany) {
        OrganizationFormSheet(
            organization = current,
            onDismiss = { editingCompany = false },
            onSaved = { editingCompany = false },
        )
    }

    editingMember?.let { edit ->
        MemberFormSheet(
            organizationId = current.id,
            existing = edit.member,
            onDismiss = { editingMember = null },
            onDone = { editingMember = null },
        )
    }

    editingLocation?.let { edit ->
        LocationFormSheet(
            organizationId = current.id,
            existing = edit.location,
            onDismiss = { editingLocation = null },
            onSaved = { editingLocation = null },
        )
    }

    if (editingStatus) {
        StatusDialog(
            organization = current,
            onDismiss = { editingStatus = false },
            onChoose = {
                editingStatus = false
                viewModel.setStatus(it)
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

/** The three subjects an account is made of. Declaration order is tab order. */
private enum class AccountTab(val label: String) {
    COMPANY("Company"),
    PEOPLE("People"),
    LOCATIONS("Locations");

    /** The tab's name, carrying its count where there is one to carry. */
    fun labelWith(count: Int?): String = if (count == null) label else "$label $count"
}

/** Which person form is open: [member] is null when adding. */
private data class MemberEdit(val member: Member?)

/** Which location form is open: [location] is null when adding. */
private data class LocationEdit(val location: OrgLocation?)

/**
 * The strip under the bar: whose account this is, and whether it can trade.
 *
 * The name is not repeated here — the bar above already carries it, and on a 393dp screen saying it
 * twice costs a row of whatever the tab below is showing.
 */
@Composable
private fun AccountIdentity(organization: Organization) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = Spacing.screen, vertical = Spacing.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Monogram(initials = organization.initials, size = Sizes.avatarSmall)
        Spacer(Modifier.width(Spacing.md))
        OrganizationStatusChip(organization.orgStatus)
        if (!organization.orgStatus.canTrade) {
            Spacer(Modifier.width(Spacing.sm))
            Text(
                text = "The store will refuse its orders",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun AccountMenu(onEditCompany: () -> Unit, onChangeStatus: () -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = "Account actions")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text("Edit company") },
                onClick = {
                    expanded = false
                    onEditCompany()
                },
            )
            DropdownMenuItem(
                text = { Text("Change status…") },
                onClick = {
                    expanded = false
                    onChangeStatus()
                },
            )
        }
    }
}

/** The company itself: what the store bills, and the rule its deliveries follow. */
@Composable
private fun CompanyTab(organization: Organization, onEdit: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.screen),
    ) {
        SectionHeader(
            title = "Billing address",
            subtitle = "Applied by the store to every order",
            trailing = { TextButton(onClick = onEdit) { Text("Edit") } },
        )
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Column(Modifier.padding(Spacing.md)) {
                Text(
                    // Shown exactly as WooCommerce formats it for the country — postcode before
                    // the city in Germany, after it in the US, and so on for every variant.
                    text = organization.billingFormatted.ifBlank {
                        "No billing address on this account."
                    },
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

        Spacer(Modifier.height(Spacing.sectionSpacing))
        SectionHeader(title = "Delivery rule")
        Spacer(Modifier.height(Spacing.sectionGap))
        QuickSaleCard {
            Row(
                modifier = Modifier.padding(Spacing.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = if (organization.allowCustomShipping) {
                        Icons.Outlined.Place
                    } else {
                        Icons.Outlined.Block
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(Sizes.icon),
                )
                Spacer(Modifier.width(Spacing.md))
                Text(
                    text = if (organization.allowCustomShipping) {
                        "An order may also go to an address typed at the counter."
                    } else {
                        "Every order must go to one of this account's saved locations."
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
        }

        Spacer(Modifier.height(Spacing.xl))
    }
}

/** The people who may buy for this company. */
@Composable
private fun PeopleTab(
    members: List<Member>,
    organizationCanTrade: Boolean,
    onOpenMember: (memberUserId: Long) -> Unit,
    onEdit: (Member) -> Unit,
    onAdd: () -> Unit,
) {
    TabContent(
        isEmpty = members.isEmpty(),
        emptyIcon = Icons.Outlined.Groups,
        emptyTitle = "Nobody on this account",
        emptyMessage = "Add the person who buys for this company and they get a login.",
        addLabel = "Add a person",
        onAdd = onAdd,
    ) {
        Text(
            text = "Tap somebody to see their details and start an order.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        members.forEach { member ->
            MemberRow(
                member = member,
                organizationCanTrade = organizationCanTrade,
                onClick = { onOpenMember(member.userId) },
                onEdit = { onEdit(member) },
            )
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

/** Where this company takes delivery. */
@Composable
private fun LocationsTab(
    locations: List<OrgLocation>,
    allowCustomShipping: Boolean,
    onEdit: (OrgLocation) -> Unit,
    onAdd: () -> Unit,
) {
    TabContent(
        isEmpty = locations.isEmpty(),
        emptyIcon = Icons.Outlined.Place,
        emptyTitle = "No saved locations",
        emptyMessage = if (allowCustomShipping) {
            "Orders can still go to an address typed at the counter."
        } else {
            "This account can only be sold to over the counter until one is added."
        },
        addLabel = "Add a location",
        onAdd = onAdd,
    ) {
        Text(
            text = if (allowCustomShipping) {
                "Orders may go to one of these, or to an address typed at the counter."
            } else {
                "Orders must go to one of these."
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Spacing.sm))
        locations.forEach { location ->
            LocationRow(location = location, onEdit = { onEdit(location) })
            Spacer(Modifier.height(Spacing.sm))
        }
    }
}

/**
 * The shape both list tabs share: an add button that is always reachable, over a list or an empty
 * state.
 *
 * The button sits above the list rather than floating over it. A FAB on a 393dp screen covers the
 * last row, which is the one most likely to have just been added.
 */
@Composable
private fun TabContent(
    isEmpty: Boolean,
    emptyIcon: ImageVector,
    emptyTitle: String,
    emptyMessage: String,
    addLabel: String,
    onAdd: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.screen)
            .padding(top = Spacing.md, bottom = Spacing.xl),
    ) {
        OutlinedButton(
            onClick = onAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(Sizes.button),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Spacing.sm))
            Text(addLabel)
        }
        Spacer(Modifier.height(Spacing.md))

        if (isEmpty) {
            EmptyState(
                icon = emptyIcon,
                title = emptyTitle,
                message = emptyMessage,
                modifier = Modifier.height(EMPTY_TAB_HEIGHT),
            )
        } else {
            content()
        }
    }
}

/** Enough for the empty state to sit properly without the scroll collapsing around it. */
private val EMPTY_TAB_HEIGHT = 240.dp

@Composable
private fun StatusDialog(
    organization: Organization,
    onDismiss: () -> Unit,
    onChoose: (OrganizationStatus) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change status") },
        text = {
            Column {
                Text(
                    text = "The store emails the account when it is approved or rejected. " +
                        "It is ${organization.orgStatus.label.lowercase()} now.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(Spacing.sm))
                CHANGEABLE_STATUSES.forEach { status ->
                    TextButton(
                        onClick = { onChoose(status) },
                        enabled = status != organization.orgStatus,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = status.label,
                            modifier = Modifier.weight(1f),
                            color = if (status == OrganizationStatus.REJECTED) {
                                MaterialTheme.colorScheme.error
                            } else {
                                MaterialTheme.colorScheme.primary
                            },
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** [OrganizationStatus.UNKNOWN] is not a status the store can hold, so it isn't offered. */
private val CHANGEABLE_STATUSES = listOf(
    OrganizationStatus.ACTIVE,
    OrganizationStatus.PENDING,
    OrganizationStatus.SUSPENDED,
    OrganizationStatus.REJECTED,
)

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
