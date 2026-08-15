package me.sourov.quicksale.data.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.sourov.quicksale.data.remote.JobProgress
import me.sourov.quicksale.data.remote.WkSyncApi
import me.sourov.quicksale.data.remote.WooApiException
import me.sourov.quicksale.data.remote.isFinishedRunAfter
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.StoreSettings
import me.sourov.quicksale.data.settings.settingsDataStore

/**
 * Runs and watches the website's Kontor jobs.
 *
 * Shaped like [SyncManager] on purpose — an app-wide scope so a run survives navigation, one
 * [MutableStateFlow] per job so every affordance showing that job animates together — but the work
 * is somebody else's: this only asks the website to start, then watches until it stops.
 *
 * The watching is the fiddly part. A trigger answers `202` before anything has happened, and the
 * job's state at that moment still describes the *previous* run. So a trigger's `previous_run_id`
 * becomes a baseline and [isFinishedRunAfter] decides when what's being reported is a different,
 * finished run — see that function for what each condition rules out.
 */
object WebsiteSyncManager {

    /** The plugin documents 5 seconds as the floor. Nothing here needs to be quicker than that. */
    private const val POLL_INTERVAL_MS = 5_000L

    /** Twenty minutes of polling. Past that the background action is presumed dead, not slow. */
    private const val MAX_POLLS = 240

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val states = WebsiteJob.entries.associateWith {
        MutableStateFlow<WebsiteSyncState>(WebsiteSyncState.Idle)
    }

    /** One lock per job, so a tap and an adopted run never drive the same job's state at once. */
    private val locks = WebsiteJob.entries.associateWith { Mutex() }

    /**
     * Whether the store has the Kontor sync plugin at all. Null until something has asked.
     *
     * Plenty of stores won't, and on those the whole feature should be invisible rather than a row
     * that answers 404 when pressed.
     */
    private val _available = MutableStateFlow<Boolean?>(null)
    val available: StateFlow<Boolean?> = _available.asStateFlow()

    /**
     * Product images the store still owes itself, as of the last status read.
     *
     * Worth surfacing because it explains a thing the counter sees and can't otherwise account
     * for: a catalogue run reports success and some products still have no photo. The queue
     * outlives the run that created it, so this is not a run still in progress — it is the store
     * fetching pictures in the background, and the till will get them on a later sync.
     */
    private val _imageQueue = MutableStateFlow(0)
    val imageQueue: StateFlow<Int> = _imageQueue.asStateFlow()

    fun state(job: WebsiteJob): StateFlow<WebsiteSyncState> = states.getValue(job).asStateFlow()

    /** Starts [job] and forgets about it — for a button, which has the state flow to watch. */
    fun start(context: Context, job: WebsiteJob) {
        if (states.getValue(job).value.isRunning) return
        val appContext = context.applicationContext
        scope.launch { runCatching { run(appContext, job) } }
    }

    /**
     * Asks the website to run [job] and suspends until it finishes, reporting progress through the
     * job's own state flow.
     *
     * Throws on anything that stops the run being observed: no store configured, no plugin, keys
     * without `manage_woocommerce`, or twenty minutes with no sign of life. A run that *starts* and
     * then fails is not an exception — it comes back as a [JobProgress] with `failed` set, because
     * the store's own summary of what went wrong is more useful than anything this could invent.
     */
    private suspend fun run(context: Context, job: WebsiteJob): JobProgress {
        val appContext = context.applicationContext
        val state = states.getValue(job)
        return locks.getValue(job).withLock {
            try {
                val settings = requireConfigured(appContext)
                val api = WkSyncApi(settings)
                state.value = WebsiteSyncState.Running("Asking the website…", null)

                // A refusal because it is already going is the outcome the caller wanted anyway:
                // fall through to watching whatever is in flight. Baseline 0 then, since no run
                // this app knows of is the "previous" one.
                val baseline = try {
                    retryOnNetworkBlip { api.runJob(job.slug) }.previousRunId
                } catch (e: WooApiException) {
                    if (e.code != WkSyncApi.ALREADY_RUNNING) throw e
                    0L
                }
                _available.value = true

                poll(api, job, baseline, state)
            } catch (e: Exception) {
                noteAvailability(e)
                state.value = WebsiteSyncState.Error(e.message ?: "The website sync failed")
                throw e
            }
        }
    }

    /**
     * Reads every job's current state without starting anything, so the settings rows can show
     * when each last ran and when it runs next.
     *
     * A job found genuinely *running* — the plugin's own schedule fired, or another till triggered
     * it — is adopted and followed to completion, so the row reflects the store rather than
     * showing a stale "success" over work that is happening right now.
     *
     * Only `running`, never `queued`: a store whose scheduler is behind reports every due job as
     * queued indefinitely, and adopting those paints a spinner over a run that ended — in the
     * observed case, over a failure the person reading the row most needed to see.
     */
    suspend fun refreshStatuses(context: Context) {
        val appContext = context.applicationContext
        try {
            val settings = requireConfigured(appContext)
            val api = WkSyncApi(settings)
            val snapshot = retryOnNetworkBlip { api.fetchJobs() }
            _available.value = true
            _imageQueue.value = snapshot.imageQueue

            WebsiteJob.entries.forEach { job ->
                val progress = snapshot.forSlug(job.slug) ?: return@forEach
                val state = states.getValue(job)
                when {
                    // Don't overwrite a run this device is already driving with a snapshot read.
                    state.value.isRunning -> Unit
                    progress.running -> follow(appContext, job, progress)
                    progress.neverRun -> state.value = WebsiteSyncState.Idle
                    else -> state.value = WebsiteSyncState.Done(progress)
                }
            }
        } catch (e: Exception) {
            noteAvailability(e)
            // Silent otherwise: this runs when a screen opens, and a store that is merely offline
            // shouldn't paint an error over rows nobody asked to refresh.
        }
    }

    /** Watches a run this device didn't start, from wherever it already is. */
    private fun follow(context: Context, job: WebsiteJob, current: JobProgress) {
        val state = states.getValue(job)
        state.value = current.toRunning()
        scope.launch {
            locks.getValue(job).withLock {
                try {
                    val api = WkSyncApi(requireConfigured(context))
                    // Baseline 0: the run to wait for is the one already going, and it carries a
                    // real id, so "finished with an id that isn't 0" is exactly the right test.
                    poll(api, job, baselineRunId = 0L, state = state)
                } catch (e: Exception) {
                    state.value = WebsiteSyncState.Error(e.message ?: "Lost track of the website run")
                }
            }
        }
    }

    private suspend fun poll(
        api: WkSyncApi,
        job: WebsiteJob,
        baselineRunId: Long,
        state: MutableStateFlow<WebsiteSyncState>,
    ): JobProgress {
        repeat(MAX_POLLS) {
            delay(POLL_INTERVAL_MS)
            val progress = retryOnNetworkBlip { api.fetchJob(job.slug) }
            if (progress.isFinishedRunAfter(baselineRunId)) {
                state.value = WebsiteSyncState.Done(progress)
                return progress
            }
            state.value = progress.toRunning()
        }
        error("The website didn't finish ${job.label.lowercase()} in time — check the store's logs")
    }

    private suspend fun requireConfigured(context: Context): StoreSettings {
        val settings = SettingsRepository(context.settingsDataStore).settings.first()
        check(settings.isConfigured) { "Connect your store in Settings first" }
        return settings
    }

    /**
     * A 404 is the plugin's absence, not a failure to hide — every other error leaves the
     * question open, because a rejected key says nothing about whether the routes are there.
     */
    private fun noteAvailability(e: Exception) {
        if (e is WooApiException && (e.status == 404 || e.code == "rest_no_route")) {
            _available.value = false
        }
    }

}

/** The line to show while a run is in flight, preferring whatever the store says about itself. */
private fun JobProgress.toRunning(): WebsiteSyncState.Running = WebsiteSyncState.Running(
    message = when {
        message.isNotBlank() -> message
        total > 0 -> "$processed of $total"
        queued && !running -> "Queued on the website…"
        else -> "Running on the website…"
    },
    fraction = percent?.let { it / 100f },
)
