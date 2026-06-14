package me.sourov.quicksale.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.settings.OrderSettingsRepository
import me.sourov.quicksale.data.settings.OrderStatus

class OrderSettingsViewModel(
    private val repository: OrderSettingsRepository,
) : ViewModel() {

    private val _status = MutableStateFlow(OrderStatus.PROCESSING)
    val status: StateFlow<OrderStatus> = _status.asStateFlow()

    private var loaded = false

    init {
        viewModelScope.launch {
            repository.status.collect { stored ->
                // Seed once from disk; afterwards the UI is the source of truth.
                if (!loaded) {
                    loaded = true
                    _status.value = stored
                }
            }
        }
    }

    fun setStatus(status: OrderStatus) {
        _status.value = status
        viewModelScope.launch { repository.update(status) }
    }

    companion object {
        fun factory(repository: OrderSettingsRepository) = viewModelFactory {
            initializer { OrderSettingsViewModel(repository) }
        }
    }
}
