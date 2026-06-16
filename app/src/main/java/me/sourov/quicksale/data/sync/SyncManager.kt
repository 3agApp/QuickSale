package me.sourov.quicksale.data.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.io.IOException
import me.sourov.quicksale.data.local.Customer
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.local.QuickSaleDatabase
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.settings.CurrencyRepository
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.StoreCurrency
import me.sourov.quicksale.data.settings.settingsDataStore

/**
 * Runs catalog/customer synchronisation in an app-wide scope so it survives navigation.
 * Each [SyncTarget] syncs independently — observe [state] per target for its button/progress.
 */
object SyncManager {

    private const val MAX_PAGES = 200
    private const val MAX_PARALLEL_PAGE_FETCHES = 4

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val states = SyncTarget.entries.associateWith { MutableStateFlow<SyncState>(SyncState.Idle) }

    /** Observe the sync state for a single [target]. */
    fun state(target: SyncTarget): StateFlow<SyncState> = states.getValue(target).asStateFlow()

    /** Sync the catalog only. */
    fun syncProducts(context: Context) = sync(context, SyncTarget.Products)

    /** Sync customers only. */
    fun syncCustomers(context: Context) = sync(context, SyncTarget.Customers)

    private fun sync(context: Context, target: SyncTarget) {
        val state = states.getValue(target)
        if (state.value is SyncState.Running) return
        val appContext = context.applicationContext
        scope.launch {
            state.value = SyncState.Running("Starting…", 0f)
            try {
                val settings = SettingsRepository(appContext.settingsDataStore).settings.first()
                if (!settings.isConfigured) {
                    state.value = SyncState.Error("Connect your store in Settings first")
                    return@launch
                }

                val db = QuickSaleDatabase.getInstance(appContext)
                val api = WooCommerceApi(settings)

                val count = when (target) {
                    SyncTarget.Products -> {
                        // Refresh the store currency so prices show the right symbol. Non-fatal: an
                        // older store or missing endpoint must not abort the catalog sync.
                        runCatching {
                            val currency = retryOnNetworkBlip { api.fetchCurrency() }
                            CurrencyRepository(appContext.settingsDataStore)
                                .setCurrency(StoreCurrency(code = currency.code, symbol = currency.symbol))
                        }
                        val products = fetchAllPages(state, target.label) { page -> api.fetchProducts(page) }
                        db.productDao().replaceAll(products)
                        products.size
                    }

                    SyncTarget.Customers -> {
                        val customers = fetchAllPages(state, target.label) { page -> api.fetchCustomers(page) }
                        db.customerDao().replaceAll(customers)
                        customers.size
                    }
                }

                val now = System.currentTimeMillis()
                SyncMetaRepository(appContext.settingsDataStore).setLastSync(target, now)
                state.value = SyncState.Success(count, now)
            } catch (e: Exception) {
                state.value = SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }

    private suspend fun <T> fetchAllPages(
        state: MutableStateFlow<SyncState>,
        label: String,
        fetchPage: suspend (page: Int) -> WooCommerceApi.Page<T>,
    ): List<T> = coroutineScope {
        val firstPage = retryOnNetworkBlip { fetchPage(1) }
        val totalPages = firstPage.totalPages.coerceAtLeast(1).coerceAtMost(MAX_PAGES)
        val pages = mutableListOf(1 to firstPage.items)
        var completedPages = 1
        var itemCount = firstPage.items.size

        publishPageProgress(state, label, itemCount, completedPages, totalPages)

        (2..totalPages).chunked(MAX_PARALLEL_PAGE_FETCHES).forEach { chunk ->
            val results = chunk
                .map { page ->
                    async {
                        page to retryOnNetworkBlip { fetchPage(page) }.items
                    }
                }
                .awaitAll()

            pages += results
            completedPages += results.size
            itemCount += results.sumOf { it.second.size }
            publishPageProgress(state, label, itemCount, completedPages, totalPages)
        }

        pages
            .sortedBy { it.first }
            .flatMap { it.second }
    }

    private fun publishPageProgress(
        state: MutableStateFlow<SyncState>,
        label: String,
        itemCount: Int,
        completedPages: Int,
        totalPages: Int,
    ) {
        state.value = SyncState.Running(
            message = "Syncing $label… $itemCount",
            fraction = completedPages.toFloat() / totalPages.coerceAtLeast(1),
        )
    }

    /**
     * Retries a single page fetch on a transient network error ([IOException]) with a short
     * exponential backoff. HTTP/auth failures surface as [IllegalStateException] and are NOT
     * retried — a wrong key or 404 should fail fast rather than spin.
     */
    private suspend fun <T> retryOnNetworkBlip(
        attempts: Int = 3,
        initialDelayMs: Long = 700L,
        block: suspend () -> T,
    ): T {
        var delayMs = initialDelayMs
        repeat(attempts - 1) {
            try {
                return block()
            } catch (_: IOException) {
                delay(delayMs)
                delayMs *= 2
            }
        }
        return block() // last attempt: let the exception propagate
    }
}
