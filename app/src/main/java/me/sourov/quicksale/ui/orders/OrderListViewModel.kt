package me.sourov.quicksale.ui.orders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.settings.SettingsRepository

/**
 * An organization's orders, newest first.
 *
 * WooCommerce has no "this organization's orders" query — only "this customer's orders" — and a
 * member is the WooCommerce customer, not the organization. So this asks for every member's
 * orders and merges them, which is the same relationship [WooCommerceApi.createOrder] relies on
 * when it stamps `customer_id` from the member rather than the organization.
 */
class OrderListViewModel(
    private val organizationId: Long,
    private val organizationRepository: OrganizationRepository,
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
                val members = organizationRepository.members(organizationId).first()
                val api = WooCommerceApi(settings)
                // One member's failed fetch (e.g. never ordered) shouldn't blank the whole list.
                val merged = coroutineScope {
                    members.map { member ->
                        async {
                            runCatching { api.fetchOrders(member.userId, page = 1).items }
                                .getOrDefault(emptyList())
                        }
                    }.awaitAll()
                }.flatten().sortedByDescending { it.dateCreatedGmt }
                _orders.value = merged
            } catch (e: Exception) {
                _error.value = OrderError.from(e)
            } finally {
                _loading.value = false
            }
        }
    }

    fun consumeError() { _error.value = null }

    companion object {
        fun factory(
            organizationId: Long,
            organizationRepository: OrganizationRepository,
            settingsRepository: SettingsRepository,
        ) = viewModelFactory {
            initializer {
                OrderListViewModel(organizationId, organizationRepository, settingsRepository)
            }
        }
    }
}
