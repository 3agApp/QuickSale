package me.sourov.quicksale.data.sync

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.local.Member
import me.sourov.quicksale.data.local.OrgLocation
import me.sourov.quicksale.data.local.Organization
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.QuickSaleDatabase
import me.sourov.quicksale.data.remote.WoapApi
import me.sourov.quicksale.data.remote.WooApiException
import me.sourov.quicksale.data.remote.WooCommerceApi
import me.sourov.quicksale.data.settings.AddressFormRepository
import me.sourov.quicksale.data.settings.CheckoutConfigRepository
import me.sourov.quicksale.data.settings.CurrencyRepository
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.StoreSettings
import me.sourov.quicksale.data.settings.settingsDataStore

/**
 * Runs synchronisation in an app-wide scope so it survives navigation.
 *
 * Each [SyncTarget] syncs independently — observe [state] per target for its button and progress —
 * and every sync affordance in the app reads these same flows, so a run started from the Home
 * screen animates the top-bar button too.
 */
object SyncManager {

    private const val MAX_PAGES = 200
    private const val MAX_PARALLEL_PAGE_FETCHES = 4

    /** The snapshot route's refusal for a `page` beyond the last one. */
    private const val PAGE_NO_LONGER_EXISTS = "woap_rest_invalid_page_number"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val states = SyncTarget.entries.associateWith { MutableStateFlow<SyncState>(SyncState.Idle) }

    /** Observe the sync state for a single [target]. */
    fun state(target: SyncTarget): StateFlow<SyncState> = states.getValue(target).asStateFlow()

    /** True while any target is syncing — drives the global "syncing…" affordances. */
    val anyRunning: StateFlow<Boolean> =
        combine(states.values.toList()) { current -> current.any { it.isRunning } }
            .stateIn(scope, SharingStarted.Eagerly, false)

    /** Sync the catalog only. */
    fun syncProducts(context: Context) = sync(context, SyncTarget.Products)

    /** Sync the organization snapshot only. */
    fun syncOrganizations(context: Context) = sync(context, SyncTarget.Organizations)

    /** Sync everything. Targets run in parallel; each reports its own progress. */
    fun syncAll(context: Context) = SyncTarget.entries.forEach { sync(context, it) }

    fun sync(context: Context, target: SyncTarget) {
        val state = states.getValue(target)
        if (state.value.isRunning) return
        val appContext = context.applicationContext
        scope.launch {
            state.value = SyncState.Running("Starting…", 0f)
            try {
                val settings = SettingsRepository(appContext.settingsDataStore).settings.first()
                if (!settings.isConfigured) {
                    state.value = SyncState.Error("Connect your store in Settings first")
                    return@launch
                }

                val result = when (target) {
                    SyncTarget.Products -> syncProducts(appContext, settings, state)
                    SyncTarget.Organizations -> syncOrganizations(appContext, settings, state)
                }

                val now = System.currentTimeMillis()
                SyncMetaRepository(appContext.settingsDataStore).setLastSync(target, now)
                state.value = SyncState.Success(result.count, now, result.unchanged)
            } catch (e: Exception) {
                state.value = SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }

    private class Outcome(val count: Int, val unchanged: Boolean = false)

    private suspend fun syncProducts(
        context: Context,
        settings: StoreSettings,
        state: MutableStateFlow<SyncState>,
    ): Outcome {
        val api = WooCommerceApi(settings)
        val db = QuickSaleDatabase.getInstance(context)

        // Refresh the store currency so prices are written exactly as the website writes them.
        // Non-fatal: an older store or missing endpoint must not abort the catalog sync.
        runCatching {
            val currency = retryOnNetworkBlip { api.fetchCurrency() }
            CurrencyRepository(context.settingsDataStore).setCurrency(currency)
        }
        // Refresh checkout behaviour (payment gateways, shipping methods, tax rules) for the order
        // screen. Also non-fatal for the same reason.
        runCatching {
            val config = retryOnNetworkBlip { api.fetchCheckoutConfig() }
            CheckoutConfigRepository(context.settingsDataStore).setConfig(config)
        }

        val products = fetchAllPages(state, SyncTarget.Products.unit) { page -> api.fetchProducts(page) }
        db.productDao().replaceAll(products)
        return Outcome(products.size)
    }

    /**
     * Syncs the organization snapshot, plus the address forms.
     *
     * The route serves snapshots rather than deltas, so a change means replacing the whole local
     * set — anything the snapshot omits has been deleted. But a `304` page carries no body, so
     * changed pages alone cannot rebuild that set. Hence two phases: cheaply probe every known
     * page with its stored ETag, and only when something actually moved refetch all of them in
     * full. On an unchanged store the whole sync is a handful of hash comparisons.
     */
    private suspend fun syncOrganizations(
        context: Context,
        settings: StoreSettings,
        state: MutableStateFlow<SyncState>,
    ): Outcome {
        val api = WoapApi(settings)
        val repository = OrganizationRepository(QuickSaleDatabase.getInstance(context).organizationDao())
        val etagRepository = SyncEtagRepository(context.settingsDataStore)

        // The shop's per-country address forms change rarely and are revalidated the same way.
        // Non-fatal: a till that can't render a one-off address can still deliver to a location.
        runCatching {
            val stored = etagRepository.addressFormEtag()
            val result = retryOnNetworkBlip { api.fetchAddressForms(stored) }
            result.forms?.let {
                AddressFormRepository(context.settingsDataStore).setForms(it)
                etagRepository.setAddressFormEtag(result.etag)
            }
        }

        state.value = SyncState.Running("Checking for changes…", 0.05f)
        val storedEtags = etagRepository.organizationPageEtags()

        if (storedEtags.isNotEmpty() && isSnapshotUnchanged(api, storedEtags)) {
            return Outcome(repository.count().first(), unchanged = true)
        }

        val organizations = mutableListOf<Organization>()
        val members = mutableListOf<Member>()
        val locations = mutableListOf<OrgLocation>()
        val freshEtags = mutableMapOf<Int, String>()

        var page = 1
        var totalPages = 1
        while (page <= totalPages && page <= MAX_PAGES) {
            val result = retryOnNetworkBlip { api.fetchOrganizations(page) }
            totalPages = result.totalPages.coerceAtLeast(1)
            organizations += result.organizations
            members += result.members
            locations += result.locations
            result.etag?.let { freshEtags[page] = it }
            state.value = SyncState.Running(
                message = "Syncing organizations… ${organizations.size}",
                fraction = page.toFloat() / totalPages.coerceAtLeast(1),
            )
            page++
        }

        repository.replaceAll(organizations, members, locations)
        etagRepository.setOrganizationPageEtags(freshEtags)
        return Outcome(organizations.size)
    }

    /**
     * True when every page the app already holds answers `304`. A changed page count also counts
     * as changed: an organization added or removed shifts the page boundaries below it.
     */
    private suspend fun isSnapshotUnchanged(api: WoapApi, storedEtags: Map<Int, String>): Boolean {
        val knownPages = storedEtags.keys.sorted()
        var reportedTotalPages: Int? = null
        for (page in knownPages) {
            val result = try {
                retryOnNetworkBlip { api.fetchOrganizations(page, ifNoneMatch = storedEtags[page]) }
            } catch (e: WooApiException) {
                // The route answers a page past the end with a 400, not an empty list. Probing a
                // page the app holds an ETag for can only hit that once organizations have been
                // deleted — which is a definite change, not a failure. Report it as changed and
                // let the full refetch below rewrite the (now shorter) set of page ETags.
                if (e.code == PAGE_NO_LONGER_EXISTS) return false
                throw e
            }
            if (!result.notModified) return false
            reportedTotalPages = result.totalPages
        }
        // A 304 need not repeat the pagination headers; only trust a total it actually reported.
        return reportedTotalPages == null || reportedTotalPages == knownPages.size
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

}
