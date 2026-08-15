package me.sourov.quicksale.data.remote

import me.sourov.quicksale.data.settings.StoreSettings
import org.json.JSONArray
import org.json.JSONObject

/**
 * Client for the Kontor sync plugin's routes — the hop *above* this app.
 *
 * QuickSale copies the website onto the till; this plugin copies Kontor, the shop's source of
 * truth, onto the website. Triggering it from here is what lets one tap mean "get the newest
 * prices all the way from Kontor", rather than faithfully syncing yesterday's catalog.
 *
 * The namespace prefix (`wc-wksync`) is load-bearing for the same reason it is on
 * [WoapApi]: WooCommerce's authentication only reads consumer keys for request URIs
 * containing `wc/` or `wc-`. The key's user also needs `manage_woocommerce`.
 *
 * Runs are fire-and-poll: a trigger returns `202` immediately and the work happens in a
 * background action, so nothing here holds a connection open for the length of a sync.
 */
class WkSyncApi(settings: StoreSettings) {

    private val http = WooHttp(settings)

    /** All jobs in one call, plus the store's image backlog. */
    suspend fun fetchJobs(): JobsSnapshot {
        val json = JSONObject(http.get("$NAMESPACE/jobs").body)
        return JobsSnapshot(
            jobs = json.optJSONArray("jobs").mapObjects { it.toJobProgress() },
            imageQueue = json.optInt("image_queue"),
        )
    }

    /** One job's current progress. The route answers with a bare progress object. */
    suspend fun fetchJob(slug: String): JobProgress =
        JSONObject(http.get("$NAMESPACE/jobs/$slug").body).toJobProgress()

    /**
     * Asks the website to run [slug] now.
     *
     * Refusals arrive as [WooApiException]s carrying the plugin's own codes — [ALREADY_RUNNING]
     * when someone beat you to it, and the `wksync_*` 503s when the store isn't configured for
     * Kontor at all. Callers branch on [WooApiException.code]; the messages are localised to the
     * site's language.
     */
    suspend fun runJob(slug: String): RunAccepted {
        val json = JSONObject(http.post("$NAMESPACE/jobs/$slug/run", JSONObject()).body)
        return RunAccepted(
            previousRunId = json.optLong("previous_run_id"),
            progress = json.optJSONObject("progress")?.toJobProgress(),
        )
    }

    companion object {
        private const val NAMESPACE = "wc-wksync/v1"

        /** Not a failure: the job the caller wanted is already in flight. */
        const val ALREADY_RUNNING = "wksync_already_running"
    }
}

internal fun JSONObject.toJobProgress(): JobProgress = JobProgress(
    slug = optString("job"),
    label = optString("label").decodeHtmlEntities(),
    state = optString("state").ifBlank { JobProgress.STATE_NEVER },
    running = optBoolean("running"),
    queued = optBoolean("queued"),
    runId = optLong("run_id"),
    // Absent or null both mean indeterminate — the run knows it is working but not how far.
    percent = if (isNull("percent")) null else optInt("percent"),
    total = optInt("total"),
    processed = optInt("processed"),
    counts = optJSONObject("counts").toIntMap(),
    message = optString("message").stripHtml(),
    startedGmt = optNullableString("started_gmt"),
    finishedGmt = optNullableString("finished_gmt"),
    nextRunGmt = optNullableString("next_run_gmt"),
)

private fun JSONObject.optNullableString(key: String): String? =
    if (isNull(key)) null else optString(key).takeIf { it.isNotBlank() }

private fun <T> JSONArray?.mapObjects(map: (JSONObject) -> T): List<T> {
    if (this == null) return emptyList()
    return buildList(length()) {
        for (i in 0 until length()) optJSONObject(i)?.let { add(map(it)) }
    }
}

/**
 * Reads whatever tallies a run reports.
 *
 * Kept as a plain map rather than named fields because the plugin documents its `counts` keys as
 * *not* a contract — products reports `created`/`no_sku`/`drafted`, stock reports
 * `missing`/`unmanaged`, and either may grow another tomorrow.
 */
private fun JSONObject?.toIntMap(): Map<String, Int> {
    if (this == null) return emptyMap()
    return buildMap {
        keys().forEach { key -> put(key, optInt(key)) }
    }
}

/** One job's state on the website, exactly as the plugin reports it. */
class JobProgress(
    val slug: String,
    val label: String,
    val state: String,
    val running: Boolean,
    val queued: Boolean,
    val runId: Long,
    /** Null when the run can't say how far along it is; show an indeterminate bar. */
    val percent: Int?,
    val total: Int,
    val processed: Int,
    val counts: Map<String, Int>,
    val message: String,
    val startedGmt: String?,
    val finishedGmt: String?,
    val nextRunGmt: String?,
) {
    val failed: Boolean get() = state == STATE_FAILED

    /** Never run, so there is no outcome to report — only a job waiting for its first go. */
    val neverRun: Boolean get() = state == STATE_NEVER || runId == 0L

    companion object {
        const val STATE_NEVER = "never"
        const val STATE_RUNNING = "running"
        const val STATE_SUCCESS = "success"
        const val STATE_FAILED = "failed"
    }
}

/** Every job at once, plus the images the store still owes itself. */
class JobsSnapshot(val jobs: List<JobProgress>, val imageQueue: Int) {
    fun forSlug(slug: String): JobProgress? = jobs.firstOrNull { it.slug == slug }
}

/**
 * A `202` from a trigger. [previousRunId] is the baseline to watch: the run the caller asked for
 * has begun once the job reports a different one. [progress] is whatever the job looked like at
 * the moment of the trigger, which may be nothing useful yet.
 */
class RunAccepted(val previousRunId: Long, val progress: JobProgress?)

/**
 * True once this progress describes a *finished* run that is not the one [baselineRunId] named.
 *
 * The run id is the load-bearing half: it separates "your run finished" from "the previous run's
 * result is still sitting there". A trigger that fails before execution leaves a `failed` state on
 * the *old* id, which without this check reads as a completed run that went wrong.
 *
 * `queued` is deliberately not consulted, though the API docs suggest waiting for it to clear. It
 * means "overdue **or** executing", and on a store whose scheduler is running behind — which is
 * most WordPress installs without a real cron — a job that is merely due reports it indefinitely.
 * Waiting for it would mean sitting through the whole polling budget after a run that finished
 * minutes ago. A changed run id and a terminal state already answer the question it was there for.
 */
fun JobProgress.isFinishedRunAfter(baselineRunId: Long): Boolean =
    !running &&
        runId != baselineRunId &&
        (state == JobProgress.STATE_SUCCESS || state == JobProgress.STATE_FAILED)
