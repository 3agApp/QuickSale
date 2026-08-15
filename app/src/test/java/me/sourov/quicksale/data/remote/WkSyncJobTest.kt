package me.sourov.quicksale.data.remote

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The website reports its Kontor jobs as one progress object, and the app has to read two things
 * from it: what to show, and whether the run it asked for is over.
 */
class WkSyncJobTest {

    @Test
    fun `a finished run carries its tallies and the store's own summary`() {
        val progress = JSONObject(
            """
            {
              "job": "stock",
              "label": "Stock sync",
              "state": "success",
              "running": false,
              "queued": false,
              "run_id": 1786734356,
              "started_gmt": "2026-08-14T18:25:56",
              "finished_gmt": "2026-08-14T18:26:11",
              "percent": 100,
              "total": 2945,
              "processed": 2945,
              "counts": { "updated": 2805, "missing": 140, "unmanaged": 0 },
              "message": "2805 products updated, 140 article numbers had no matching SKU.",
              "next_run_gmt": "2026-08-14T18:41:11"
            }
            """.trimIndent(),
        ).toJobProgress()

        assertEquals("stock", progress.slug)
        assertEquals(100, progress.percent)
        assertEquals(2945, progress.processed)
        assertEquals(mapOf("updated" to 2805, "missing" to 140, "unmanaged" to 0), progress.counts)
        assertEquals("2026-08-14T18:26:11", progress.finishedGmt)
        assertFalse(progress.failed)
        assertFalse(progress.neverRun)
    }

    /** `counts` keys are documented as not a contract, so a new one must survive rather than drop. */
    @Test
    fun `tallies the app has never heard of are kept`() {
        val progress = JSONObject(
            """
            {"job":"products","state":"success","run_id":7,
             "counts":{"created":3,"drafted":1,"something_new":9}}
            """.trimIndent(),
        ).toJobProgress()

        assertEquals(9, progress.counts["something_new"])
    }

    /** A run that can't say how far along it is reports null, not zero — the bar goes indeterminate. */
    @Test
    fun `a run with nothing to report reads as indeterminate rather than stalled`() {
        val progress = JSONObject(
            """
            {"job":"products","label":"Product sync","state":"running","running":true,
             "queued":false,"run_id":42,"percent":null,"total":0,"processed":0,
             "started_gmt":"2026-08-15T09:00:00","finished_gmt":null,"next_run_gmt":null}
            """.trimIndent(),
        ).toJobProgress()

        assertNull(progress.percent)
        assertNull(progress.finishedGmt)
        assertNull(progress.nextRunGmt)
        assertTrue(progress.running)
    }

    @Test
    fun `a job that has never run says so`() {
        val progress = JSONObject("""{"job":"stock","state":"never","run_id":0}""").toJobProgress()

        assertTrue(progress.neverRun)
        assertEquals(JobProgress.STATE_NEVER, progress.state)
    }

    @Test
    fun `a running job has not finished`() {
        assertFalse(job(state = "running", running = true, runId = 99).isFinishedRunAfter(7L))
    }

    /** Between the trigger and the run starting, the state still describes the previous run. */
    @Test
    fun `a job queued on the baseline run has not finished`() {
        assertFalse(job(state = "success", queued = true, runId = 7).isFinishedRunAfter(7L))
    }

    /**
     * The store this was built against reports its stock job queued indefinitely, because the
     * site's scheduler runs behind and the job is permanently overdue. Waiting for that flag to
     * clear would mean polling out the full budget after a run that ended minutes earlier.
     */
    @Test
    fun `a finished run counts even while the next one sits overdue`() {
        assertTrue(job(state = "success", queued = true, runId = 99).isFinishedRunAfter(7L))
    }

    /** A `never` state on a new id is not an outcome — there is nothing to report yet. */
    @Test
    fun `a job that has not started is not a finished run`() {
        assertFalse(job(state = "never", runId = 99).isFinishedRunAfter(7L))
    }

    /**
     * The case worth having a test for: a trigger that fails before the run starts leaves the
     * *previous* run's id in place with a failed state. Read as "our run finished badly", the app
     * would stop polling and report a failure the store never had.
     */
    @Test
    fun `a failure on the baseline run id is not this run finishing`() {
        assertFalse(job(state = "failed", runId = 7).isFinishedRunAfter(7L))
    }

    @Test
    fun `a terminal state on a new run id is this run finishing`() {
        assertTrue(job(state = "success", runId = 99).isFinishedRunAfter(7L))
        assertTrue(job(state = "failed", runId = 99).isFinishedRunAfter(7L))
    }

    /** After a 409 the app has no baseline, so any finished run with a real id is the one to take. */
    @Test
    fun `with no baseline a finished run still counts`() {
        assertTrue(job(state = "success", runId = 99).isFinishedRunAfter(0L))
    }

    private fun job(
        state: String,
        runId: Long,
        running: Boolean = false,
        queued: Boolean = false,
    ): JobProgress = JSONObject()
        .put("job", "stock")
        .put("state", state)
        .put("run_id", runId)
        .put("running", running)
        .put("queued", queued)
        .toJobProgress()
}
