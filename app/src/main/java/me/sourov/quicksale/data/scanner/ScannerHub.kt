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

    /** Pushes a scan from a non-broadcast source (e.g. keyboard/HID capture) into the stream. */
    fun emit(raw: String) {
        if (raw.isNotBlank()) _scans.tryEmit(raw)
    }

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
                        put(k, decodeExtra(extras.get(k)))
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
                        // No specific key configured: pick just the barcode field, not the
                        // scanner's metadata extras (symbology, length, timestamp, …).
                        pickBarcode(extrasMap)
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

    /** Converts a broadcast extra to text, decoding raw byte/char arrays (some scanners send these). */
    private fun decodeExtra(value: Any?): String = when (value) {
        null -> ""
        is String -> value
        is ByteArray -> String(value, Charsets.UTF_8).filter { !Character.isISOControl(it) }.trim()
        is CharArray -> String(value).filter { !Character.isISOControl(it) }.trim()
        else -> value.toString()
    }

    /** Common extra keys scanner apps use for the decoded barcode value, most-specific first. */
    private val KNOWN_DATA_KEYS = listOf(
        "data", "barcode_string", "barcode", "scan_barcode1", "value",
        "scannerdata", "scanner_data", "com.symbol.datawedge.data_string",
        "barocode", "decode_data", "scan_result", "result",
        "extra_barcode_decoded_data",
    )

    /**
     * Extracts just the decoded barcode from a broadcast's extras when no specific key is
     * configured: prefer a well-known data key, otherwise fall back to the longest string
     * value (the decoded payload is almost always longer than metadata like symbology/length).
     */
    private fun pickBarcode(extras: Map<String, String>): String? {
        if (extras.isEmpty()) return null
        for (key in KNOWN_DATA_KEYS) {
            val value = extras.entries.firstOrNull { it.key.equals(key, ignoreCase = true) }?.value
            if (!value.isNullOrBlank()) return value
        }
        return extras.values.filter { it.isNotBlank() }.maxByOrNull { it.length }
    }
}
