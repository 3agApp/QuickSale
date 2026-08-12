package me.sourov.quicksale.ui.organizations

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.LocalPhone
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationStatus
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.ui.components.EmptyState
import me.sourov.quicksale.ui.components.LoadingState
import me.sourov.quicksale.ui.components.Monogram
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.components.StatusChip
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Reviewing an account that registered and is waiting to be let in.
 *
 * The plugin calls this an organization; the shop calls it a pending user, and that is the order the
 * screen reads in — **who signed up first, what company they signed up for second**. Approving does
 * both at once: the account starts trading and the people on it are switched on, because an approved
 * company nobody can sign into is not an approval.
 *
 * [onReviewed] fires once the store has accepted a decision, so the caller can leave the screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PendingApprovalScreen(
    organizationId: Long,
    onBack: () -> Unit,
    onReviewed: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val viewModel: PendingApprovalViewModel = viewModel(
        factory = PendingApprovalViewModel.factory(
            organizationId = organizationId,
            repository = container.organizations,
            settingsRepository = container.settings,
        ),
    )

    val organization by viewModel.organization.collectAsStateWithLifecycle()
    val members by viewModel.members.collectAsStateWithLifecycle()
    val locations by viewModel.locations.collectAsStateWithLifecycle()
    val working by viewModel.working.collectAsStateWithLifecycle()
    val outcome by viewModel.outcome.collectAsStateWithLifecycle()
    val syncState by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()

    var confirmingRejection by remember { mutableStateOf(false) }

    val current = organization
    if (current == null) {
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
                    Column {
                        Text("Review account", style = MaterialTheme.typography.titleMedium)
                        Text(
                            text = current.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
        bottomBar = {
            DecisionBar(
                working = working,
                onApprove = viewModel::approve,
                onReject = { confirmingRejection = true },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(Spacing.screen),
        ) {
            // The person first: this is who is waiting, and the only thing a reviewer recognises.
            SectionHeader(
                title = if (members.size == 1) "Who signed up" else "Who's on this account",
                subtitle = if (members.size == 1) null else "${members.size} people",
            )
            Spacer(Modifier.height(Spacing.sectionGap))
            if (members.isEmpty()) {
                QuickSaleCard {
                    Text(
                        text = "Nobody is on this account yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(Spacing.lg),
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    members.forEach { ApplicantCard(it) }
                }
            }

            // The company second: the account the person is asking to buy on.
            Spacer(Modifier.height(Spacing.sectionSpacing))
            SectionHeader(title = "Their company")
            Spacer(Modifier.height(Spacing.sectionGap))
            CompanyCard(current)

            if (locations.isNotEmpty()) {
                Spacer(Modifier.height(Spacing.sectionSpacing))
                SectionHeader(
                    title = "Branches",
                    subtitle = "Where their orders would be delivered",
                )
                Spacer(Modifier.height(Spacing.sectionGap))
                Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    locations.forEach { LocationRow(location = it) }
                }
            }

            Spacer(Modifier.height(Spacing.xl))
        }
    }

    if (confirmingRejection) {
        AlertDialog(
            onDismissRequest = { confirmingRejection = false },
            title = { Text("Reject ${current.name}?") },
            text = {
                Text(
                    "The store emails them the rejection. You can move the account back to " +
                        "pending or active later — nothing is deleted.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmingRejection = false
                        viewModel.reject()
                    },
                ) {
                    Text("Reject", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmingRejection = false }) { Text("Cancel") }
            },
        )
    }

    outcome?.let { result ->
        ReviewOutcomeDialog(
            outcome = result,
            organizationName = current.name,
            onDismiss = {
                val decided = result !is ReviewOutcome.Failed
                viewModel.consumeOutcome()
                // The decision is already applied to the local copy from the rows the store
                // returned, so the list is correct the moment this closes — no resync needed.
                if (decided) onReviewed()
            },
        )
    }
}

/**
 * One person waiting on the account. Richer than the row used elsewhere on purpose: on this screen
 * the person *is* the subject, not a way to start an order.
 */
@Composable
private fun ApplicantCard(member: Member) {
    QuickSaleCard {
        Row(
            modifier = Modifier.padding(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Monogram(initials = member.initials, size = Sizes.avatar)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = member.name.ifBlank { member.email },
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                if (member.email.isNotBlank() && member.name.isNotBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = member.email,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.height(Spacing.sm))
                Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    StatusChip(
                        label = member.roleLabel,
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                    StatusChip(
                        label = member.statusLabel,
                        containerColor = if (member.isActive) {
                            MaterialTheme.colorScheme.tertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        contentColor = if (member.isActive) {
                            MaterialTheme.colorScheme.onTertiaryContainer
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun CompanyCard(organization: Organization) {
    QuickSaleCard {
        Column(Modifier.padding(Spacing.md)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Monogram(
                    initials = organization.initials,
                    size = Sizes.avatar,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.width(Spacing.md))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = organization.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Spacer(Modifier.height(Spacing.sm))
                    OrganizationStatusChip(organization.orgStatus)
                }
            }

            if (organization.billingFormatted.isNotBlank()) {
                Spacer(Modifier.height(Spacing.lg))
                Text(
                    text = "Billing address",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(Spacing.xs))
                Text(
                    // Printed the way WooCommerce prints it for its country, not assembled here.
                    text = organization.billingFormatted,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }

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
private fun ContactLine(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
) {
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

/** The decision, always in view — the reviewer shouldn't have to scroll to act. */
@Composable
private fun DecisionBar(
    working: Boolean,
    onApprove: () -> Unit,
    onReject: () -> Unit,
) {
    Surface(shadowElevation = 8.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.navigationBars)
                .padding(Spacing.screen),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            OutlinedButton(
                onClick = onReject,
                enabled = !working,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier
                    .weight(1f)
                    .height(Sizes.button),
            ) {
                Text("Reject")
            }
            Button(
                onClick = onApprove,
                enabled = !working,
                modifier = Modifier
                    .weight(1.4f)
                    .height(Sizes.button),
            ) {
                if (working) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onPrimary,
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(Spacing.sm))
                    Text("Approve", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ReviewOutcomeDialog(
    outcome: ReviewOutcome,
    organizationName: String,
    onDismiss: () -> Unit,
) {
    val (title, body) = when (outcome) {
        is ReviewOutcome.Applied -> when (outcome.status) {
            OrganizationStatus.ACTIVE -> "Approved" to buildString {
                append("$organizationName can now place orders, and the store has emailed them.")
                if (outcome.alsoActivated > 0) {
                    val people = if (outcome.alsoActivated == 1) "person was" else "people were"
                    append(" ${outcome.alsoActivated} $people switched on with it.")
                }
            }

            else -> "Rejected" to
                "$organizationName has been rejected. You can change that later — nothing was deleted."
        }

        ReviewOutcome.AlreadyDecided ->
            "Already reviewed" to "Somebody got to $organizationName first. Nothing was sent twice."

        is ReviewOutcome.Failed -> "The store refused" to outcome.message
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(body) },
        confirmButton = { TextButton(onClick = onDismiss) { Text("OK") } },
    )
}
