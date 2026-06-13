package me.sourov.quicksale.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.scanner.ScannerConfig
import me.sourov.quicksale.data.scanner.ScannerConfigRepository
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.scanner.ScannerMode
import me.sourov.quicksale.data.scanner.ScannerPreset

class ScannerViewModel(
    private val repository: ScannerConfigRepository,
) : ViewModel() {

    private val _config = MutableStateFlow(ScannerConfig())
    val config: StateFlow<ScannerConfig> = _config.asStateFlow()

    /** Last broadcast the receiver saw — used for the on-screen diagnostic. */
    val lastDiagnostic = ScannerHub.lastDiagnostic

    private var loaded = false

    init {
        viewModelScope.launch {
            repository.config.collect { stored ->
                // Seed once from disk; afterwards the UI is the source of truth.
                if (!loaded) {
                    loaded = true
                    _config.value = stored
                }
            }
        }
    }

    fun setMode(mode: ScannerMode) = persist { it.copy(mode = mode) }

    fun setPreset(preset: ScannerPreset) = persist { current ->
        if (preset == ScannerPreset.CUSTOM) {
            current.copy(presetId = preset.id)
        } else {
            current.copy(presetId = preset.id, action = preset.action, extraKey = preset.extraKey)
        }
    }

    fun setAction(action: String) = persist {
        it.copy(action = action, presetId = ScannerPreset.CUSTOM.id)
    }

    fun setExtraKey(extraKey: String) = persist {
        it.copy(extraKey = extraKey, presetId = ScannerPreset.CUSTOM.id)
    }

    private fun persist(transform: (ScannerConfig) -> ScannerConfig) {
        val next = transform(_config.value)
        _config.value = next
        viewModelScope.launch { repository.update(next) }
    }

    companion object {
        fun factory(repository: ScannerConfigRepository) = viewModelFactory {
            initializer { ScannerViewModel(repository) }
        }
    }
}
