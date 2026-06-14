package me.sourov.quicksale

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.scanner.ScannerConfigRepository
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.scanner.ScannerMode
import me.sourov.quicksale.data.settings.settingsDataStore
import me.sourov.quicksale.ui.QuickSaleApp
import me.sourov.quicksale.ui.theme.QuickSaleTheme

class MainActivity : ComponentActivity() {

    private val scannerConfigRepository by lazy {
        ScannerConfigRepository(applicationContext.settingsDataStore)
    }

    // Keyboard/HID scanner capture for the main window (Dialogs capture in their own window).
    private var keyboardScannerMode = false
    private val scanBuffer = StringBuilder()
    private val mainHandler = Handler(Looper.getMainLooper())
    private val flushScan = Runnable { flushScanBuffer() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // While the app is visible, keep a scanner broadcast receiver registered to match
        // the saved config (re-registers automatically when the config changes).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                scannerConfigRepository.config.collect { config ->
                    keyboardScannerMode = config.mode == ScannerMode.KEYBOARD
                    ScannerHub.register(
                        context = this@MainActivity,
                        actions = config.registrableActions,
                        extraKey = config.extraKey.ifBlank { null },
                    )
                }
            }
        }

        setContent {
            QuickSaleTheme {
                QuickSaleApp()
            }
        }
    }

    /**
     * In keyboard/HID mode the scanner "types" the decoded value as hardware key events.
     * Capture them here (soft-keyboard text goes through the IME, not here) and publish the
     * decoded string so any screen can react. Finalises on Enter, or after a short pause.
     */
    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (!keyboardScannerMode) return super.onKeyDown(keyCode, event)

        when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                mainHandler.removeCallbacks(flushScan)
                flushScanBuffer()
                return true
            }

            else -> {
                val ch = event?.unicodeChar ?: 0
                if (ch != 0 && !Character.isISOControl(ch)) {
                    scanBuffer.append(ch.toChar())
                    mainHandler.removeCallbacks(flushScan)
                    mainHandler.postDelayed(flushScan, 250)
                    return true
                }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun flushScanBuffer() {
        val value = scanBuffer.toString()
        scanBuffer.setLength(0)
        if (value.isNotEmpty()) {
            ScannerHub.emit(value)
        }
    }

    override fun onStop() {
        super.onStop()
        mainHandler.removeCallbacks(flushScan)
        ScannerHub.unregister(this)
    }
}
