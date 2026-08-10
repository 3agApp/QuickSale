package me.sourov.quicksale.data.sync

import android.content.Context
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.settingsDataStore

/**
 * Keeps the till's copy of the store fresh on its own.
 *
 * Rather than a timer that fires every N minutes, this ticks once a minute and syncs whatever is
 * *older* than the chosen interval. That distinction matters on a device that sleeps: a timer
 * loses its place when the app is backgrounded for an hour, whereas a staleness check comes back
 * and immediately notices the data is old. It also means a manual sync counts — tapping Sync
 * resets the clock for that target rather than being followed by a redundant automatic run.
 *
 * Polling is cheap by design: the organization route revalidates with ETags, so an unchanged
 * store answers `304` and nothing is transferred or rewritten.
 *
 * Call [run] from a lifecycle-scoped coroutine; it loops until that scope is cancelled.
 */
object AutoSyncScheduler {

    private const val TICK_MS = 60_000L

    suspend fun run(context: Context) {
        val appContext = context.applicationContext
        val autoSync = AutoSyncRepository(appContext.settingsDataStore)
        val settings = SettingsRepository(appContext.settingsDataStore)
        val meta = SyncMetaRepository(appContext.settingsDataStore)

        val startedAt = System.currentTimeMillis()
        while (true) {
            val preferences = autoSync.settings.first()
            val configured = settings.settings.first().isConfigured

            if (preferences.enabled && configured) {
                val now = System.currentTimeMillis()
                // With catch-up on launch switched off, the app is the clock's starting point:
                // nothing syncs until a full interval has passed since it opened, however stale
                // the stored copy already was.
                val readyForFirstRun =
                    preferences.syncOnLaunch || now - startedAt >= preferences.interval.millis
                if (readyForFirstRun) {
                    SyncTarget.entries.forEach { target ->
                        val lastSync = meta.lastSyncMillis(target).first()
                        if (now - lastSync >= preferences.interval.millis) {
                            SyncManager.sync(appContext, target)
                        }
                    }
                }
            }

            delay(TICK_MS)
        }
    }
}
