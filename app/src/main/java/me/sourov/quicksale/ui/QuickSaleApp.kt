package me.sourov.quicksale.ui

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
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import me.sourov.quicksale.data.scanner.ScannerHub
import me.sourov.quicksale.navigation.QuickSaleNavHost
import me.sourov.quicksale.navigation.TopLevelDestination
import me.sourov.quicksale.navigation.navigateToTopLevel
import me.sourov.quicksale.ui.components.QuickSaleTopBar

@Composable
fun QuickSaleApp() {
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val currentRoute = currentDestination?.route
    val isTopLevel = TopLevelDestination.fromRoute(currentRoute) != null

    val isProducts = currentRoute == TopLevelDestination.PRODUCTS.route
    val isCustomers = currentRoute == TopLevelDestination.CUSTOMERS.route
    val searchEnabled = isProducts || isCustomers

    var productsQuery by rememberSaveable { mutableStateOf("") }
    var customersQuery by rememberSaveable { mutableStateOf("") }
    var searchActive by rememberSaveable { mutableStateOf(false) }

    // Collapse the manual search toggle whenever the destination changes (queries persist).
    LaunchedEffect(currentRoute) { searchActive = false }

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

    val activeQuery = when {
        isProducts -> productsQuery
        isCustomers -> customersQuery
        else -> ""
    }
    val onQueryChange: (String) -> Unit = when {
        isProducts -> { value -> productsQuery = value }
        isCustomers -> { value -> customersQuery = value }
        else -> { _ -> }
    }
    val showSearchField = searchEnabled && (searchActive || activeQuery.isNotEmpty())

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            QuickSaleTopBar(
                showBack = !isTopLevel,
                onBack = { navController.popBackStack() },
                searchEnabled = searchEnabled,
                searchActive = showSearchField,
                autoFocus = searchActive,
                query = activeQuery,
                placeholder = if (isCustomers) "Search customers" else "Search products",
                onQueryChange = onQueryChange,
                onSearchOpen = { searchActive = true },
                onSearchClose = {
                    searchActive = false
                    onQueryChange("")
                },
            )
        },
        bottomBar = {
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        QuickSaleNavHost(
            navController = navController,
            snackbarHostState = snackbarHostState,
            productsQuery = productsQuery,
            customersQuery = customersQuery,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
