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
 * Every order the store has taken, newest first.
 *
 * This is the owner's question at a fair — "what have we sold today" — which previously had no
 * screen at all: orders were reachable only by opening an account first, so nobody could see the
 * day's takings without already knowing whose they were.
 */
class OrdersViewModel(
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _orders = MutableStateFlow<List<WooCommerceApi.OrderSummary>>(emptyList())

    private val _filter = MutableStateFlow(OrderFilter.ALL)
    val filter: StateFlow<OrderFilter> = _filter.asStateFlow()

    /** The rows to draw, already narrowed by the chosen filter. */
    val orders: StateFlow<List<WooCommerceApi.OrderSummary>> = _orders.asStateFlow()

    private val _loading = MutableStateFlow(false)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _error = MutableStateFlow<OrderError?>(null)
    val error: StateFlow<OrderError?> = _error.asStateFlow()

    init {
        refresh()
    }

    fun setFilter(value: OrderFilter) { _filter.value = value }

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
                _orders.value = WooCommerceApi(settings).fetchOrders(perPage = PAGE_SIZE).items
            } catch (e: Exception) {
                _error.value = OrderError.forRead(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun consumeError() { _error.value = null }

    companion object {
        /** One page is a fair's worth of orders; the list is a feed, not an archive. */
        private const val PAGE_SIZE = 50

        fun factory(settingsRepository: SettingsRepository) = viewModelFactory {
            initializer { OrdersViewModel(settingsRepository) }
        }
    }
}

/** The narrowing offered above the order feed. */
enum class OrderFilter(val label: String) {
    ALL("All"),
    TODAY("Today"),
    PROCESSING("Processing"),
    PENDING("Pending"),
    COMPLETED("Completed"),
    ;

    /** Whether [order] belongs in this filter, judged against the device's own calendar day. */
    fun matches(order: WooCommerceApi.OrderSummary, today: java.time.LocalDate): Boolean =
        when (this) {
            ALL -> true
            TODAY -> order.dateCreatedGmt.toOrderLocalDate() == today
            PROCESSING -> order.status == "processing"
            PENDING -> order.status == "pending"
            COMPLETED -> order.status == "completed"
        }
}
