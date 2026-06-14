package me.sourov.quicksale.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.settings.LabelSettings
import me.sourov.quicksale.data.settings.LabelSettingsRepository

class LabelSettingsViewModel(
    private val repository: LabelSettingsRepository,
) : ViewModel() {

    val settings: StateFlow<LabelSettings> = repository.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LabelSettings())

    fun setShowName(value: Boolean) = persist { repository.setShowName(value) }
    fun setShowBarcode(value: Boolean) = persist { repository.setShowBarcode(value) }
    fun setShowSku(value: Boolean) = persist { repository.setShowSku(value) }
    fun setShowPrice(value: Boolean) = persist { repository.setShowPrice(value) }

    private fun persist(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }

    companion object {
        fun factory(repository: LabelSettingsRepository) = viewModelFactory {
            initializer { LabelSettingsViewModel(repository) }
        }
    }
}
