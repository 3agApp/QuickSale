package me.sourov.quicksale.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import me.sourov.quicksale.data.sync.WebsiteJob
import me.sourov.quicksale.data.sync.WebsiteSyncManager
import me.sourov.quicksale.ui.components.QuickSaleCard
import me.sourov.quicksale.ui.components.SectionHeader
import me.sourov.quicksale.ui.components.SyncRowDivider
import me.sourov.quicksale.ui.components.WebsiteJobRow
import me.sourov.quicksale.ui.theme.Spacing

/**
 * Kontor sync, in Settings: make the *website* fetch from the shop's source of truth.
 *
 * Its own page, deliberately away from [SyncSettingsSection]. Both pages say "sync", but they move
 * different data between different machines: this one asks the website to go and get the shop's
 * real prices and quantities from Kontor, and finishes without this device being any newer for it.
 * Sitting them side by side, or behind one button, produced the obvious question — "I synced, why
 * are the prices still wrong?" — with no way to answer it from the screen.
 *
 * So the two are split, and this page ends by saying plainly what to do next.
 */
@Composable
fun KontorSettingsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Read the jobs' current state when the page opens, so the rows say what the store has been
    // doing without anyone having to press something to find out.
    LaunchedEffect(Unit) { WebsiteSyncManager.refreshStatuses(context) }

    val available by WebsiteSyncManager.available.collectAsStateWithLifecycle()
    val imageQueue by WebsiteSyncManager.imageQueue.collectAsStateWithLifecycle()

    Column(modifier = modifier.fillMaxWidth()) {
        SectionHeader(
            title = "Kontor sync",
            subtitle = "Refresh the website's products and stock from Kontor",
        )

        Spacer(Modifier.height(Spacing.sectionGap))

        // False means the store answered 404: no plugin. Null means nobody has managed to ask yet,
        // in which case show the rows — they carry their own errors, and a blank page while the
        // first request is in flight would read as "this store has nothing".
        if (available == false) {
            QuickSaleCard {
                Text(
                    text = "This store doesn't have the Kontor sync plugin installed, so there's " +
                        "nothing here to run. The device sync on the Sync page works either way.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(Spacing.lg),
                )
            }
            return@Column
        }

        QuickSaleCard {
            Column(Modifier.padding(vertical = Spacing.xs)) {
                WebsiteJob.entries.forEachIndexed { index, job ->
                    if (index > 0) SyncRowDivider()
                    val state by WebsiteSyncManager.state(job).collectAsStateWithLifecycle()
                    WebsiteJobRow(
                        job = job,
                        state = state,
                        onRun = { WebsiteSyncManager.start(context, job) },
                    )
                }
            }
        }

        Spacer(Modifier.height(Spacing.lg))
        Text(
            text = buildString {
                append(
                    "This updates the website only. Once a run finishes, sync this device on the " +
                        "Sync page to bring the changes down to the till."
                )
                // The queue outlives the run that filled it, so a catalogue sync can report
                // success with photos still arriving. Saying so here answers the question the
                // blank thumbnails on the Products tab would otherwise raise.
                if (imageQueue > 0) {
                    append(
                        "\n\nThe website is still downloading $imageQueue product " +
                            "${if (imageQueue == 1) "image" else "images"} in the background. " +
                            "Those arrive on a later sync."
                    )
                }
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = Spacing.xs),
        )
    }
}
