package me.sourov.quicksale.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import kotlinx.coroutines.launch
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.settings.DeviceMode
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncState
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.navigation.QuickSaleNavHost
import me.sourov.quicksale.navigation.Routes
import me.sourov.quicksale.navigation.TopLevelDestination
import me.sourov.quicksale.navigation.navigateToTopLevel
import me.sourov.quicksale.ui.components.ConnectionBanner
import me.sourov.quicksale.ui.components.QuickSaleTopBar
import me.sourov.quicksale.ui.onboarding.DeviceModeScreen
import me.sourov.quicksale.ui.orders.SellViewModel
import me.sourov.quicksale.ui.update.AppUpdatePrompt
import me.sourov.quicksale.ui.update.AppUpdateViewModel

@Composable
fun QuickSaleApp() {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val scope = rememberCoroutineScope()

    // Null means the question has never been answered on this device, which is the whole of the
    // first run: nothing else can be laid out until we know what this handheld is for.
    val deviceMode by container.deviceMode.mode.collectAsStateWithLifecycle(initialValue = null)

    when (val mode = deviceMode) {
        null -> DeviceModeScreen(
            onSelect = { chosen -> scope.launch { container.deviceMode.update(chosen) } },
            modifier = Modifier.fillMaxSize(),
        )
        else -> QuickSaleShell(mode = mode)
    }
}

@Composable
private fun QuickSaleShell(mode: DeviceMode) {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val updateViewModel: AppUpdateViewModel =
        viewModel(factory = AppUpdateViewModel.factory(container.updatePreferences))
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    /**
     * The one cart, held above the navigation graph.
     *
     * Scoping it here rather than to a back-stack entry is what makes it a *standing* cart: it
     * survives leaving the Sell tab, so a visitor's half-built order is still there after a detour
     * into the catalog to answer a question.
     */
    val sellViewModel: SellViewModel = viewModel(
        factory = SellViewModel.factory(
            organizationRepository = container.organizations,
            productRepository = container.products,
            cartRepository = container.cart,
            settingsRepository = container.settings,
            orderSettingsRepository = container.orderSettings,
            checkoutConfigRepository = container.checkoutConfig,
            addressFormRepository = container.addressForms,
        ),
    )

    val tabs = remember(mode) { TopLevelDestination.forMode(mode) }
    val startDestination = remember(mode) { TopLevelDestination.startFor(mode) }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val topLevel = TopLevelDestination.fromRoute(currentRoute)

    val isProducts = topLevel == TopLevelDestination.PRODUCTS
    val isAccounts = topLevel == TopLevelDestination.ACCOUNTS

    // The checkout and confirmation take over the whole screen and manage their own chrome.
    val showChrome = !Routes.isFullScreen(currentRoute)

    var productsQuery by rememberSaveable { mutableStateOf("") }
    var organizationsQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    // Collapse the manual search toggle whenever the destination changes (queries persist).
    LaunchedEffect(currentRoute) { searchActive = false }

    LaunchedEffect(Unit) { updateViewModel.checkOnAppStart() }

    // Mirror the persisted store currency into the formatter so prices are written the way the
    // website writes them; emits again whenever a sync refreshes it, recomposing visible prices.
    LaunchedEffect(Unit) {
        container.currency.currency.collect(CurrencyFormatter::update)
    }

    // On the Products tab, a hardware/camera scan (broadcast or keyboard, per Settings) becomes
    // the search query; scanning again replaces it.
    LaunchedEffect(isProducts) {
        if (isProducts) {
            ScannerHub.scans.collect { scan ->
                // Show the scanned query without forcing the keyboard open (autoFocus stays off).
                productsQuery = scan.trim()
            }
        }
    }

    // Null until DataStore answers. Starting from a blank StoreSettings would mean every cold
    // start flashed "Store not connected" for a frame or two on a perfectly connected till.
    val settings by container.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val productsSync by SyncManager.state(SyncTarget.Products).collectAsStateWithLifecycle()
    val organizationsSync by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()

    // The tab's own sync sits in the top bar, so the list you're looking at is one tap from fresh.
    val topBarSync: (() -> Unit)? = when {
        isProducts -> { { SyncManager.syncProducts(context) } }
        isAccounts -> { { SyncManager.syncOrganizations(context) } }
        else -> null
    }
    val topBarSyncing = when {
        isProducts -> productsSync.isRunning
        isAccounts -> organizationsSync.isRunning
        else -> false
    }

    val activeQuery = when {
        isProducts -> productsQuery
        isAccounts -> organizationsQuery
        else -> ""
    }
    val onQueryChange: (String) -> Unit = when {
        isProducts -> { value -> productsQuery = value }
        isAccounts -> { value -> organizationsQuery = value }
        else -> { _ -> }
    }
    val searchEnabled = topLevel?.searchable == true
    val showSearchField = searchEnabled && (searchActive || activeQuery.isNotEmpty())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Animated rather than switched off outright: removing the bars in a single frame is
            // what made entering the order screen feel like a jump cut.
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically(tween(CHROME_DURATION)) { -it } + fadeIn(tween(CHROME_DURATION)),
                exit = slideOutVertically(tween(CHROME_DURATION)) { -it } + fadeOut(tween(CHROME_DURATION)),
            ) {
                Column {
                    QuickSaleTopBar(
                        showBack = topLevel == null,
                        onBack = { navController.popBackStack() },
                        searchEnabled = searchEnabled,
                        searchActive = showSearchField,
                        autoFocus = searchActive,
                        query = activeQuery,
                        placeholder = topLevel?.searchPlaceholder.orEmpty(),
                        onQueryChange = onQueryChange,
                        onSearchOpen = { searchActive = true },
                        onSearchClose = {
                            searchActive = false
                            onQueryChange("")
                        },
                        onSync = topBarSync,
                        syncing = topBarSyncing,
                        // Settings left the bottom bar: it is visited a handful of times per fair
                        // and was costing a fifth of the bar to say so.
                        onSettings = { navController.navigate(Routes.SETTINGS) }
                            .takeIf { currentRoute != Routes.SETTINGS },
                    )
                    ConnectionBanner(
                        // Not-yet-loaded counts as connected: silence is the honest answer until
                        // the device has actually looked.
                        configured = settings?.isConfigured != false,
                        syncError = firstSyncError(productsSync, organizationsSync),
                        onClick = { navController.navigate(Routes.SETTINGS) },
                    )
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically(tween(CHROME_DURATION)) { it } + fadeIn(tween(CHROME_DURATION)),
                exit = slideOutVertically(tween(CHROME_DURATION)) { it } + fadeOut(tween(CHROME_DURATION)),
            ) {
                NavigationBar {
                    tabs.forEach { destination ->
                        val selected = currentDestination?.hierarchy?.any {
                            it.route == destination.route
                        } == true
                        NavigationBarItem(
                            selected = selected,
                            onClick = { navController.navigateToTopLevel(destination) },
                            icon = {
                                Icon(
                                    imageVector = if (selected) {
                                        destination.selectedIcon
                                    } else {
                                        destination.unselectedIcon
                                    },
                                    contentDescription = destination.label,
                                )
                            },
                            label = { Text(destination.label) },
                        )
                    }
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        QuickSaleNavHost(
            navController = navController,
            snackbarHostState = snackbarHostState,
            startDestination = startDestination,
            sellViewModel = sellViewModel,
            productsQuery = productsQuery,
            organizationsQuery = organizationsQuery,
            modifier = Modifier.padding(innerPadding),
        )
    }

    updateState.promptRelease?.let { release ->
        AppUpdatePrompt(
            release = release,
            currentVersionName = updateState.currentVersionName,
            onLater = updateViewModel::dismissPrompt,
            onSkipVersion = updateViewModel::skipPromptVersion,
            onUpdateOpened = updateViewModel::dismissPrompt,
        )
    }
}

/**
 * The first sync failure worth surfacing, or null when both targets are fine.
 *
 * Only one is shown: two banners stacked over the till would push the scan field off the screen,
 * and the fix for both is the same trip to Settings.
 */
private fun firstSyncError(vararg states: SyncState): String? =
    states.filterIsInstance<SyncState.Error>().firstOrNull()?.message

private const val CHROME_DURATION = 240
