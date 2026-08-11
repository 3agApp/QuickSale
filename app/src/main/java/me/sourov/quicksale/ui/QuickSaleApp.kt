package me.sourov.quicksale.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.data.sync.SyncManager
import me.sourov.quicksale.data.sync.SyncTarget
import me.sourov.quicksale.navigation.QuickSaleNavHost
import me.sourov.quicksale.navigation.Routes
import me.sourov.quicksale.navigation.TopLevelDestination
import me.sourov.quicksale.navigation.navigateToTopLevel
import me.sourov.quicksale.ui.components.QuickSaleTopBar
import me.sourov.quicksale.ui.update.AppUpdatePrompt
import me.sourov.quicksale.ui.update.AppUpdateViewModel

@Composable
fun QuickSaleApp() {
    val context = LocalContext.current
    val container = remember(context) { context.appContainer }
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }
    val updateViewModel: AppUpdateViewModel =
        viewModel(factory = AppUpdateViewModel.factory(container.updatePreferences))
    val updateState by updateViewModel.uiState.collectAsStateWithLifecycle()

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val topLevel = TopLevelDestination.fromRoute(currentRoute)

    val isProducts = topLevel == TopLevelDestination.PRODUCTS
    val isOrganizations = topLevel == TopLevelDestination.ORGANIZATIONS

    // The order builder and confirmation take over the whole screen and manage their own chrome.
    val fullScreen = Routes.isFullScreen(currentRoute)
    val showChrome = !fullScreen

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

    val productsSync by SyncManager.state(SyncTarget.Products).collectAsStateWithLifecycle()
    val organizationsSync by SyncManager.state(SyncTarget.Organizations).collectAsStateWithLifecycle()

    // The tab's own sync sits in the top bar, so the list you're looking at is one tap from fresh.
    val topBarSync: (() -> Unit)? = when {
        isProducts -> { { SyncManager.syncProducts(context) } }
        isOrganizations -> { { SyncManager.syncOrganizations(context) } }
        else -> null
    }
    val topBarSyncing = when {
        isProducts -> productsSync.isRunning
        isOrganizations -> organizationsSync.isRunning
        else -> false
    }

    val activeQuery = when {
        isProducts -> productsQuery
        isOrganizations -> organizationsQuery
        else -> ""
    }
    val onQueryChange: (String) -> Unit = when {
        isProducts -> { value -> productsQuery = value }
        isOrganizations -> { value -> organizationsQuery = value }
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
                )
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = showChrome,
                enter = slideInVertically(tween(CHROME_DURATION)) { it } + fadeIn(tween(CHROME_DURATION)),
                exit = slideOutVertically(tween(CHROME_DURATION)) { it } + fadeOut(tween(CHROME_DURATION)),
            ) {
                NavigationBar {
                    TopLevelDestination.entries.forEach { destination ->
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

private const val CHROME_DURATION = 240
