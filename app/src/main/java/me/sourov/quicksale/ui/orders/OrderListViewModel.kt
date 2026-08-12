package me.sourov.quicksale.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.settings.SettingsRepository

/**
 * An organization's orders, newest first.
 *
 * Read through the accounts plugin's `woap_organization` filter — one request for the account,
 * rather than one per member merged together. The old fan-out also lost orders whose member had
 * since been removed from the account, because there was no longer anyone to ask about them.
 */
class OrderListViewModel(
    private val organizationId: Long,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _orders = MutableStateFlow<List<WooCommerceApi.OrderSummary>>(emptyList())
    val orders: StateFlow<List<WooCommerceApi.OrderSummary>> = _orders.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<OrderError?>(null)
    val error: StateFlow<OrderError?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        if (_loading.value) return
        _loading.value = true
        viewModelScope.launch {
            try {
                val settings = settingsRepository.settings.first()
                if (!settings.isConfigured) {
                    _error.value = OrderError(
                        headline = "Connect your store in Settings",
                        detail = "Orders can't be read until the store is connected.",
                    )
                    return@launch
                }
                val api = WooCommerceApi(settings)
                _orders.value = api.fetchOrders(organizationId = organizationId).items
            } catch (e: Exception) {
                _error.value = OrderError.forRead(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun consumeError() { _error.value = null }

    companion object {
        fun factory(
            organizationId: Long,
            settingsRepository: SettingsRepository,
        ) = viewModelFactory {
            initializer { OrderListViewModel(organizationId, settingsRepository) }
        }
    }
}
