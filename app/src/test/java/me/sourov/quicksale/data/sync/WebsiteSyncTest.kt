package me.sourov.quicksale.data.sync

import org.junit.Assert.assertEquals
import org.junit.Test

/** Reading the plugin's clock, which is the one thing the Kontor rows compute for themselves. */
class WebsiteSyncTest {

    @Test
    fun `the plugin's timestamps are read as UTC`() {
        // 2026-08-14T18:26:11Z
        assertEquals(1786731971_000L, parseGmt("2026-08-14T18:26:11"))
    }

    @Test
    fun `a missing or unreadable timestamp reads as no time at all`() {
        assertEquals(0L, parseGmt(null))
        assertEquals(0L, parseGmt(""))
        assertEquals(0L, parseGmt("yesterday afternoon"))
    }
}
