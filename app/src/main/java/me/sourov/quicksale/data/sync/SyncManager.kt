package me.sourov.quicksale.data.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.local.Customer
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.QuickSaleDatabase
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.settingsDataStore

/**
 * Runs catalog/customer synchronisation in an app-wide scope so it survives navigation,
 * and exposes [state] for any screen to observe (sync button + progress bar).
 */
object SyncManager {

    private const val MAX_PAGES = 200

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<SyncState>(SyncState.Idle)
    val state: StateFlow<SyncState> = _state.asStateFlow()

    fun sync(context: Context) {
        if (_state.value is SyncState.Running) return
        val appContext = context.applicationContext
        scope.launch {
            _state.value = SyncState.Running("Starting…", 0f)
            try {
                val settings = SettingsRepository(appContext.settingsDataStore).settings.first()
                if (!settings.isConfigured) {
                    _state.value = SyncState.Error("Connect your store in Settings first")
                    return@launch
                }

                val db = QuickSaleDatabase.getInstance(appContext)
                val api = WooCommerceApi(settings)

                val products = mutableListOf<Product>()
                var page = 1
                var totalPages = 1
                do {
                    val result = api.fetchProducts(page)
                    totalPages = result.totalPages.coerceAtLeast(1)
                    products += result.items
                    _state.value = SyncState.Running(
                        message = "Syncing products… ${products.size}",
                        fraction = 0.5f * (page.toFloat() / totalPages),
                    )
                    page++
                } while (page <= totalPages && page <= MAX_PAGES)
                db.productDao().replaceAll(products)

                val customers = mutableListOf<Customer>()
                page = 1
                totalPages = 1
                do {
                    val result = api.fetchCustomers(page)
                    totalPages = result.totalPages.coerceAtLeast(1)
                    customers += result.items
                    _state.value = SyncState.Running(
                        message = "Syncing customers… ${customers.size}",
                        fraction = 0.5f + 0.5f * (page.toFloat() / totalPages),
                    )
                    page++
                } while (page <= totalPages && page <= MAX_PAGES)
                db.customerDao().replaceAll(customers)

                val now = System.currentTimeMillis()
                SyncMetaRepository(appContext.settingsDataStore).setLastSync(now)
                _state.value = SyncState.Success(products.size, customers.size, now)
            } catch (e: Exception) {
                _state.value = SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }
}
