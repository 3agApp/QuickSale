package me.sourov.quicksale.data.scanner

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

/** What a scanner broadcast contained, for the on-screen diagnostic. */
data class ScanDiagnostic(
    val action: String,
    val extras: Map<String, String>,
    val timeMillis: Long,
)

/**
 * Owns the dynamically-registered [BroadcastReceiver] for scanner output and exposes
 * decoded scans + a diagnostic of the last broadcast. Registration is driven by the
 * Activity lifecycle so the app only listens while in the foreground.
 */
object ScannerHub {

    private val _scans = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val scans: SharedFlow<String> = _scans.asSharedFlow()

    private val _lastDiagnostic = MutableStateFlow<ScanDiagnostic?>(null)
    val lastDiagnostic: StateFlow<ScanDiagnostic?> = _lastDiagnostic.asStateFlow()

    private var receiver: BroadcastReceiver? = null
    private var registeredKey: String? = null

    /** (Re)registers a receiver for [actions]. [extraKey] selects the data field; if null, all extras are scanned. */
    fun register(context: Context, actions: List<String>, extraKey: String?) {
        val key = actions.sorted().joinToString("|") + "::" + extraKey.orEmpty()
        if (key == registeredKey && receiver != null) return
        unregister(context)
        if (actions.isEmpty()) return

        val appContext = context.applicationContext
        val newReceiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null) return
                val extras = intent.extras
                val extrasMap = buildMap {
                    extras?.keySet()?.forEach { k ->
                        @Suppress("DEPRECATION")
                        put(k, extras.get(k)?.toString().orEmpty())
                    }
                }
                _lastDiagnostic.value = ScanDiagnostic(
                    action = intent.action.orEmpty(),
                    extras = extrasMap,
                    timeMillis = System.currentTimeMillis(),
                )

                val raw = when {
                    !extraKey.isNullOrBlank() ->
                        intent.getStringExtra(extraKey) ?: extrasMap[extraKey]
                    else ->
                        // No specific key configured: hand over all extras so the
                        // parser can find ck_/cs_ wherever they are.
                        extrasMap.values.filter { it.isNotBlank() }.joinToString(" ")
                }
                if (!raw.isNullOrBlank()) _scans.tryEmit(raw)
            }
        }

        val filter = IntentFilter().apply { actions.forEach { addAction(it) } }
        ContextCompat.registerReceiver(
            appContext,
            newReceiver,
            filter,
            ContextCompat.RECEIVER_EXPORTED,
        )
        receiver = newReceiver
        registeredKey = key
    }

    fun unregister(context: Context) {
        receiver?.let { runCatching { context.applicationContext.unregisterReceiver(it) } }
        receiver = null
        registeredKey = null
    }
}
