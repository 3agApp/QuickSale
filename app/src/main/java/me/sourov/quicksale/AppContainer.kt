package me.sourov.quicksale

import android.app.Application
import android.content.Context
import me.sourov.quicksale.data.local.CartRepository
import me.sourov.quicksale.data.local.OrganizationRepository
import me.sourov.quicksale.data.local.ProductRepository
import me.sourov.quicksale.data.local.QuickSaleDatabase
import me.sourov.quicksale.data.net.ConnectivityMonitor
import me.sourov.quicksale.data.scanner.ScannerConfigRepository
import me.sourov.quicksale.data.settings.AddressFormRepository
import me.sourov.quicksale.data.settings.CheckoutConfigRepository
import me.sourov.quicksale.data.settings.CurrencyRepository
import me.sourov.quicksale.data.settings.DeviceModeRepository
import me.sourov.quicksale.data.settings.LabelSettingsRepository
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

    val products by lazy { ProductRepository(database.productDao()) }
    val organizations by lazy { OrganizationRepository(database.organizationDao()) }
    val cart by lazy { CartRepository(database.cartDao()) }

    /** One monitor for the process: each instance registers its own system callback. */
    val connectivity by lazy { ConnectivityMonitor(appContext) }

    val settings by lazy { SettingsRepository(dataStore) }
    val deviceMode by lazy { DeviceModeRepository(dataStore) }
    val labelSettings by lazy { LabelSettingsRepository(dataStore) }
    val scannerConfig by lazy { ScannerConfigRepository(dataStore) }
    val checkoutConfig by lazy { CheckoutConfigRepository(dataStore) }
    val addressForms by lazy { AddressFormRepository(dataStore) }
    val currency by lazy { CurrencyRepository(dataStore) }
    val syncMeta by lazy { SyncMetaRepository(dataStore) }
    val autoSync by lazy { AutoSyncRepository(dataStore) }
    val updatePreferences by lazy { AppUpdatePreferences(dataStore) }
}

class QuickSaleApplication : Application() {

    val container: AppContainer by lazy { AppContainer(this) }
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
