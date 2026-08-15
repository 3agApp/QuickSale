package me.sourov.quicksale.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Warehouse
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import me.sourov.quicksale.data.sync.SyncState
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.data.sync.WebsiteJob
import me.sourov.quicksale.data.sync.WebsiteSyncState
import me.sourov.quicksale.data.sync.parseGmt
import me.sourov.quicksale.ui.theme.Sizes
import me.sourov.quicksale.ui.theme.Spacing

/** The icon that stands for each sync target, used consistently across the app. */
val SyncTarget.icon: ImageVector
    get() = when (this) {
        SyncTarget.Products -> Icons.Filled.Inventory2
        SyncTarget.Organizations -> Icons.Filled.Business
    }

/** Website jobs get their own icons — they sit next to the device rows and must not read as those. */
val WebsiteJob.icon: ImageVector
    get() = when (this) {
        WebsiteJob.Stock -> Icons.Filled.Warehouse
        WebsiteJob.Products -> Icons.Filled.CloudSync
    }

/**
 * One target's row inside a sync card: what it holds, when it last ran, and a button to run it.
 * Progress appears inline while it's working rather than as a separate spinner overlay.
 */
@Composable
fun SyncTargetRow(
    target: SyncTarget,
    count: Int,
    state: SyncState,
    lastSyncMillis: Long,
    onSync: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = state.isRunning
    Column(modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = target.icon, size = Sizes.avatarSmall)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = target.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                SyncStatusText(state = state, unit = target.unit, count = count, lastSyncMillis = lastSyncMillis)
            }
            Spacer(Modifier.width(Spacing.sm))
            FilledTonalIconButton(
                onClick = onSync,
                enabled = !running,
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                SpinningSyncIcon(
                    syncing = running,
                    contentDescription = "Sync ${target.label}",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        AnimatedVisibility(
            visible = running,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.md))
                val fraction = (state as? SyncState.Running)?.fraction ?: 0f
                LinearProgressIndicator(
                    progress = { fraction.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

/**
 * One website job's row: what the store last did, and a button to make it do it again.
 *
 * Shaped like [SyncTargetRow] because it is the same gesture one hop further up — but the website
 * often can't say how far along a run is, so the bar here goes indeterminate rather than sitting
 * at whatever number it last managed to report.
 */
@Composable
fun WebsiteJobRow(
    job: WebsiteJob,
    state: WebsiteSyncState,
    onRun: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val running = state.isRunning
    Column(modifier = modifier.padding(horizontal = Spacing.lg, vertical = Spacing.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = job.icon, size = Sizes.avatarSmall)
            Spacer(Modifier.width(Spacing.md))
            Column(Modifier.weight(1f)) {
                Text(
                    text = job.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                WebsiteJobStatusText(job = job, state = state)
            }
            Spacer(Modifier.width(Spacing.sm))
            FilledTonalIconButton(
                onClick = onRun,
                enabled = !running,
                colors = IconButtonDefaults.filledTonalIconButtonColors(),
            ) {
                SpinningSyncIcon(
                    syncing = running,
                    contentDescription = "Run ${job.label} sync on the website",
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        AnimatedVisibility(
            visible = running,
            enter = fadeIn() + expandVertically(),
            exit = fadeOut() + shrinkVertically(),
        ) {
            Column {
                Spacer(Modifier.height(Spacing.md))
                val fraction = (state as? WebsiteSyncState.Running)?.fraction
                if (fraction == null) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    LinearProgressIndicator(
                        progress = { fraction.coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

/**
 * The line under a website job's name.
 *
 * A finished run gets the store's own summary — "2805 products updated, 140 article numbers had
 * no matching SKU" says more than any count this app could assemble, and it is already in the
 * shop's language.
 */
@Composable
private fun WebsiteJobStatusText(job: WebsiteJob, state: WebsiteSyncState) {
    val (text, color) = when (state) {
        is WebsiteSyncState.Running -> state.message to MaterialTheme.colorScheme.onSurfaceVariant
        is WebsiteSyncState.Error -> state.message to MaterialTheme.colorScheme.error
        is WebsiteSyncState.Done -> {
            val summary = state.progress.message.ifBlank {
                if (state.progress.failed) "The run failed" else "Done"
            }
            "${lastRunLabel(parseGmt(state.progress.finishedGmt))} · $summary" to
                if (state.progress.failed) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
        }

        WebsiteSyncState.Idle -> job.blurb to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        // Three, because this is the store's own account of what it did — "4150 updated, passed
        // over 234 with no image" is the whole value of the row, and two lines cut it mid-sentence.
        maxLines = 3,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * When the *website* last ran a job.
 *
 * Separate wording from [lastSyncLabel] because these rows describe work this device didn't do:
 * "synced 42 minutes ago" next to a till's own sync row reads as a second claim about the till.
 */
fun lastRunLabel(millis: Long): String {
    if (millis <= 0L) return "never run"
    val relative = DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "ran $relative"
}

/** The one-line status under a target's name: what it's doing, or what it holds. */
@Composable
private fun SyncStatusText(state: SyncState, unit: String, count: Int, lastSyncMillis: Long) {
    val (text, color) = when {
        state is SyncState.Running -> state.message to MaterialTheme.colorScheme.onSurfaceVariant
        state is SyncState.Error -> state.message to MaterialTheme.colorScheme.error
        // Worth saying out loud: an all-304 poll means the store confirmed nothing changed, which
        // is a different (and better) answer than "we didn't check".
        state is SyncState.Success && state.unchanged ->
            "$count $unit · already up to date" to MaterialTheme.colorScheme.onSurfaceVariant

        else -> "$count $unit · ${lastSyncLabel(lastSyncMillis)}" to
            MaterialTheme.colorScheme.onSurfaceVariant
    }
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

fun lastSyncLabel(millis: Long): String {
    if (millis <= 0L) return "never synced"
    val relative = DateUtils.getRelativeTimeSpanString(
        millis,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    return "synced $relative"
}

/** A divider sized to sit between [SyncTargetRow]s inside a card. */
@Composable
fun SyncRowDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(horizontal = Spacing.lg),
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
    )
}
