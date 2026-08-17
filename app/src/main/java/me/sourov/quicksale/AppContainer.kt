package me.sourov.quicksale

import android.app.Application
import android.content.Context
import coil.ImageLoader
import coil.ImageLoaderFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import me.sourov.quicksale.data.local.CartRepository
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.local.QuickSaleDatabase
import me.sourov.quicksale.data.net.ConnectivityMonitor
import me.sourov.quicksale.data.remote.InsecureTls
import me.sourov.quicksale.data.scanner.ScannerConfigRepository
import me.sourov.quicksale.data.settings.AddressFormRepository
import me.sourov.quicksale.data.settings.BackorderRepository
import me.sourov.quicksale.data.settings.CheckoutConfigRepository
import me.sourov.quicksale.data.settings.CurrencyRepository
import me.sourov.quicksale.data.settings.DeviceModeRepository
import me.sourov.quicksale.data.settings.DeviceModeState
import me.sourov.quicksale.data.settings.LabelSettingsRepository
import me.sourov.quicksale.data.settings.NewOrderStatusRepository
import me.sourov.quicksale.data.settings.SettingsRepository
import me.sourov.quicksale.data.settings.settingsDataStore
import me.sourov.quicksale.data.sync.AutoSyncRepository
import me.sourov.quicksale.data.sync.SyncMetaRepository
import me.sourov.quicksale.data.update.AppUpdatePreferences

/**
 * The app's single set of repositories.
 *
 * Screens used to build their own — `remember { SettingsRepository(context.applicationContext.settingsDataStore) }`
 * appeared in almost every composable — which meant the same DataStore was wrapped a dozen times
 * over and adding a dependency meant editing every caller. Reach for these through
 * [Context.appContainer] instead.
 */
class AppContainer(context: Context) {

    private val appContext = context.applicationContext
    private val dataStore = appContext.settingsDataStore
    private val database by lazy { QuickSaleDatabase.getInstance(appContext) }

    /** Lives as long as the process, for the handful of reads shared across every screen. */
    private val containerScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val products by lazy { ProductRepository(database.productDao()) }
    val organizations by lazy { OrganizationRepository(database.organizationDao()) }
    val cart by lazy { CartRepository(database.cartDao()) }

    /** One monitor for the process: each instance registers its own system callback. */
    val connectivity by lazy { ConnectivityMonitor(appContext) }

    val settings by lazy { SettingsRepository(dataStore) }
    val deviceMode by lazy { DeviceModeRepository(dataStore) }

    /**
     * This device's mode, read once for the whole process.
     *
     * Shared rather than collected per screen because two things race for it at launch: the splash
     * holds until it resolves, and the first composition decides what to draw from it. Collected
     * separately they finish at different times, and the gap is a blank screen sitting where the
     * splash just was. As one [StateFlow] the answer is already there when the splash lifts.
     *
     * Started eagerly so the read is underway before anything asks — it is on the critical path of
     * every cold start, and there is nothing to show until it lands.
     */
    val deviceModeState: StateFlow<DeviceModeState> by lazy {
        deviceMode.state.stateIn(containerScope, SharingStarted.Eagerly, DeviceModeState.Loading)
    }
    val labelSettings by lazy { LabelSettingsRepository(dataStore) }
    val scannerConfig by lazy { ScannerConfigRepository(dataStore) }
    val checkoutConfig by lazy { CheckoutConfigRepository(dataStore) }
    val backorders by lazy { BackorderRepository(dataStore) }
    val newOrderStatus by lazy { NewOrderStatusRepository(dataStore) }
    val addressForms by lazy { AddressFormRepository(dataStore) }
    val currency by lazy { CurrencyRepository(dataStore) }
    val syncMeta by lazy { SyncMetaRepository(dataStore) }
    val autoSync by lazy { AutoSyncRepository(dataStore) }
    val updatePreferences by lazy { AppUpdatePreferences(dataStore) }

    init {
        // Coil builds its client once and can't be handed the store settings per request, so the
        // saved "allow insecure connection" choice is mirrored where the image stack can read it.
        // A collector rather than a single read, so flipping the switch takes effect without a
        // restart.
        containerScope.launch {
            settings.settings
                .map { it.allowInsecureTls }
                .distinctUntilChanged()
                .collect { InsecureTls.allowed = it }
        }
    }
}

class QuickSaleApplication : Application(), ImageLoaderFactory {

    val container: AppContainer by lazy { AppContainer(this) }

    /**
     * Product images go through the same trust decision the REST calls do.
     *
     * Without this a store on an untrusted certificate connects for its JSON and then fails every
     * photo on the handshake, which reads as a broken catalog rather than the one certificate
     * problem the REST calls just stepped over.
     */
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .okHttpClient { InsecureTls.imageOkHttpClient() }
        .build()
}

/**
 * The container for this app process. Falls back to building one when the Application class isn't
 * in play (Compose previews, instrumentation), so previews don't crash on a cast.
 */
val Context.appContainer: AppContainer
    get() = when (val app = applicationContext) {
        is QuickSaleApplication -> app.container
        else -> FallbackContainer.of(app)
    }

private object FallbackContainer {
    @Volatile
    private var instance: AppContainer? = null

    fun of(context: Context): AppContainer =
        instance ?: synchronized(this) { instance ?: AppContainer(context).also { instance = it } }
}
