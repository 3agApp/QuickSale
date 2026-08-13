package me.sourov.quicksale.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.PersonAdd
import androidx.compose.material.icons.outlined.RemoveShoppingCart
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import me.sourov.quicksale.ui.components.TopBarTitle
import me.sourov.quicksale.ui.onboarding.DeviceModeScreen
import me.sourov.quicksale.ui.orders.CompanySheet
import me.sourov.quicksale.ui.orders.SellViewModel
import me.sourov.quicksale.ui.settings.SettingsSection
import me.sourov.quicksale.ui.theme.Sizes
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
    // Order list and detail keep the bottom bar but bring their own top bar, so the shell's would
    // be a second one stacked above it — complete with a second back arrow.
    val showTopBar = showChrome && !Routes.ownsTopBar(currentRoute)
    val inSettings = currentRoute == Routes.SETTINGS ||
        currentRoute?.startsWith(Routes.SETTINGS_SECTION) == true

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

    // Assumed online until the monitor says otherwise, so a cold start doesn't flash "Offline".
    val online by container.connectivity.online.collectAsStateWithLifecycle(initialValue = true)

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

    // How many products the current search actually matched.
    //
    // Read here rather than in ProductsScreen because the query is the shell's — the search field
    // lives in the bar — so the count is exact by construction, and it can take the place of the
    // whole "2805 products" row the list used to draw under the bar.
    val productCount by remember(productsQuery) { container.products.countMatching(productsQuery) }
        .collectAsStateWithLifecycle(initialValue = 0)

    // The cart, read here so the bar can carry it. The Sell tab's bar was a logo above a strip
    // naming the customer; one 48dp bar now does both, which is 48dp back on the busiest screen.
    val cartOrganization by sellViewModel.organization.collectAsStateWithLifecycle()
    val cartMember by sellViewModel.member.collectAsStateWithLifecycle()
    val cartLines by sellViewModel.lines.collectAsStateWithLifecycle()
    var showingCompany by remember { mutableStateOf(false) }
    val isSell = topLevel == TopLevelDestination.SELL

    // Signing someone up. Opened from the bar on the Accounts tab, where it replaced a 56dp row
    // that held this button and a count that disagreed with the list under a status filter.
    var creatingCustomer by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            // Animated rather than switched off outright: removing the bars in a single frame is
            // what made entering the order screen feel like a jump cut.
            AnimatedVisibility(
                visible = showTopBar,
                enter = slideInVertically(tween(CHROME_DURATION)) { -it } + fadeIn(tween(CHROME_DURATION)),
                exit = slideOutVertically(tween(CHROME_DURATION)) { -it } + fadeOut(tween(CHROME_DURATION)),
            ) {
                Column {
                    QuickSaleTopBar(
                        title = {
                            TopBarTitle(
                                text = barTitle(
                                    topLevel = topLevel,
                                    route = currentRoute,
                                    cartOrganization = cartOrganization?.name,
                                ),
                                detail = barDetail(
                                    topLevel = topLevel,
                                    productCount = productCount,
                                    cartMember = cartMember?.name?.ifBlank { cartMember?.email },
                                ),
                            )
                        },
                        actions = {
                            if (isAccounts) {
                                IconButton(onClick = { creatingCustomer = true }) {
                                    Icon(
                                        imageVector = Icons.Outlined.PersonAdd,
                                        contentDescription = "New customer",
                                    )
                                }
                            }
                            // The cart's own buttons, which used to sit on a second strip below.
                            if (isSell) {
                                if (cartOrganization != null) {
                                    IconButton(onClick = { showingCompany = true }) {
                                        Icon(
                                            imageVector = Icons.Outlined.Storefront,
                                            contentDescription = "Company details and locations",
                                        )
                                    }
                                }
                                if (cartLines.isNotEmpty()) {
                                    IconButton(onClick = sellViewModel::clearCart) {
                                        Icon(
                                            imageVector = Icons.Outlined.RemoveShoppingCart,
                                            contentDescription = "Empty the cart",
                                        )
                                    }
                                }
                            }
                        },
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
                        // Hidden throughout settings — including its section pages, where the
                        // gear used to push a second copy of Settings on top of itself.
                        onSettings = { navController.navigate(Routes.SETTINGS) }
                            .takeIf { !inSettings },
                    )
                    ConnectionBanner(
                        // Not-yet-loaded counts as connected: silence is the honest answer until
                        // the device has actually looked.
                        configured = settings?.isConfigured != false,
                        syncError = firstSyncError(productsSync, organizationsSync),
                        online = online,
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
                // 64dp of bar rather than Material's 80: a 24dp icon over an 11sp label needs no
                // more, and the 16dp saved shows on every top-level screen.
                //
                // The system navigation inset is added on top rather than absorbed. `NavigationBar`
                // consumes `navigationBars` itself, so a flat `height(64.dp)` is 64dp *including*
                // that inset — which is invisible on the PDA (gesture navigation, no inset) and
                // clipped the icons and labels clean off on the C6, whose three-button bar takes
                // 30dp of it.
                val systemNavInset = WindowInsets.navigationBars
                    .asPaddingValues()
                    .calculateBottomPadding()
                NavigationBar(modifier = Modifier.height(Sizes.navBar + systemNavInset)) {
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
            creatingCustomer = creatingCustomer,
            onCreatingCustomerChange = { creatingCustomer = it },
            modifier = Modifier
                .padding(innerPadding)
                // Tell everything below how much of the window this shell has already taken.
                //
                // `padding(innerPadding)` moves the content out from under the bars but leaves the
                // insets themselves looking untouched, so a screen inside that asks for
                // `imePadding()` — or a bar of its own that lifts above the keyboard — measures
                // from the true screen edge and adds the whole inset a second time. On the C6 that
                // is 112dp (a 64dp navigation bar over a 48dp system bar), which is why the till's
                // totals bar and the order editor's Save button floated a bar's height above the
                // keyboard. Consuming it here makes every nested inset modifier add only the part
                // nobody has accounted for yet, and costs nothing when there is nothing left.
                .consumeWindowInsets(innerPadding),
        )
    }

    // Opened from the bar, so it lives here rather than on the Sell screen — but only while that
    // tab is the one on screen, or it would reappear over whatever the operator navigated to.
    if (showingCompany && isSell) {
        CompanySheet(viewModel = sellViewModel, onDismiss = { showingCompany = false })
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
 * What the bar calls the screen it sits on.
 *
 * On the Sell tab it names the customer instead, because on a till "whose order is this" is the
 * only thing worth a permanent line and getting it wrong bills the wrong company.
 */
private fun barTitle(
    topLevel: TopLevelDestination?,
    route: String?,
    cartOrganization: String?,
): String = when (topLevel) {
    TopLevelDestination.SELL -> cartOrganization ?: "New order"
    null -> stackedScreenTitle(route)
    else -> topLevel.label
}

/** The quiet half of the title — the fact the screen would otherwise spend a row on. */
private fun barDetail(
    topLevel: TopLevelDestination?,
    productCount: Int,
    cartMember: String?,
): String? = when (topLevel) {
    TopLevelDestination.SELL -> cartMember?.takeIf { it.isNotBlank() }
    TopLevelDestination.PRODUCTS -> productCount.toString()
    else -> null
}

/**
 * A title for the screens that are pushed on top of a tab and don't bring their own bar.
 *
 * A settings page can name itself from the route alone. A product can't — the shell doesn't know
 * which one — but it puts the name at the top of its own content, so the bar only has to say what
 * kind of thing you are looking at. (An account brings its own bar, carrying the company name.)
 */
private fun stackedScreenTitle(route: String?): String = when {
    route == null -> ""
    route == Routes.SETTINGS -> "Settings"
    route.startsWith(Routes.SETTINGS_SECTION) ->
        SettingsSection.fromName(route.substringAfter('/'))?.title ?: "Settings"
    route.startsWith(Routes.PRODUCT_DETAIL) -> "Product"
    else -> ""
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
