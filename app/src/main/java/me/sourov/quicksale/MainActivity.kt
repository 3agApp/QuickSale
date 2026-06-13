package me.sourov.quicksale

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.scanner.ScannerConfigRepository
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.settings.settingsDataStore
import me.sourov.quicksale.ui.QuickSaleApp
import me.sourov.quicksale.ui.theme.QuickSaleTheme

class MainActivity : ComponentActivity() {

    private val scannerConfigRepository by lazy {
        ScannerConfigRepository(applicationContext.settingsDataStore)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // While the app is visible, keep a scanner broadcast receiver registered to match
        // the saved config (re-registers automatically when the config changes).
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                scannerConfigRepository.config.collect { config ->
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

    override fun onStop() {
        super.onStop()
        ScannerHub.unregister(this)
    }
}
