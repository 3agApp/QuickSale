package me.sourov.quicksale.data.sync

import me.sourov.quicksale.data.remote.JobProgress
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * A job the website can run to pull from Kontor, the shop's source of truth.
 *
 * Two, because they cost wildly different amounts. Mid-fair the question is almost always "how
 * many are left", and answering it shouldn't mean re-downloading four thousand product images.
 */
enum class WebsiteJob(val slug: String, val label: String, val blurb: String) {
    Stock("stock", "Stock", "Quantities only — quick"),
    Products("products", "Catalogue", "Products, prices and images — slow"),
}

/**
 * What a website job is doing, from this device's point of view.
 *
 * Deliberately not [SyncState]: a website run reports `percent` as *nullable* — it often knows it
 * is working without knowing how far along it is — and it finishes with a summary line and a set
 * of tallies worth putting on the screen. Neither has anywhere to live in [SyncState].
 */
sealed interface WebsiteSyncState {
    data object Idle : WebsiteSyncState

    /** [fraction] is null while the website reports indeterminate progress. */
    data class Running(val message: String, val fraction: Float?) : WebsiteSyncState

    /** A run that reached a terminal state — successfully or not; see [JobProgress.failed]. */
    data class Done(val progress: JobProgress) : WebsiteSyncState

    /** The app couldn't get an answer at all: no route, no keys, no network, or it gave up waiting. */
    data class Error(val message: String) : WebsiteSyncState

    val isRunning: Boolean get() = this is Running
}

/**
 * Reads the plugin's timestamps, which are UTC written without a zone (`2026-08-14T18:25:56`).
 *
 * Returns 0 for anything unparseable, which is the same "no time to show" the rest of the sync UI
 * already means by 0 — see [me.sourov.quicksale.ui.components.lastSyncLabel].
 */
fun parseGmt(value: String?): Long {
    if (value.isNullOrBlank()) return 0L
    return runCatching {
        LocalDateTime.parse(value, DateTimeFormatter.ISO_LOCAL_DATE_TIME)
            .toInstant(ZoneOffset.UTC)
            .toEpochMilli()
    }.getOrDefault(0L)
}
