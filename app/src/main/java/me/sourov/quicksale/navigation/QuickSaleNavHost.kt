package me.sourov.quicksale.navigation

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import me.sourov.quicksale.ui.customers.CustomersScreen
import me.sourov.quicksale.ui.home.HomeScreen
import me.sourov.quicksale.ui.orders.NewOrderScreen
import me.sourov.quicksale.ui.orders.OrderConfirmationScreen
import me.sourov.quicksale.ui.products.ProductDetailScreen
import me.sourov.quicksale.ui.products.ProductsScreen
import me.sourov.quicksale.ui.settings.SettingsScreen

object Routes {
    const val PRODUCT_DETAIL = "product_detail"
    const val PRODUCT_ID_ARG = "productId"
    fun productDetail(id: Long) = "$PRODUCT_DETAIL/$id"

    const val NEW_ORDER = "new_order"
    const val CUSTOMER_ID_ARG = "customerId"
    const val NEW_ORDER_ROUTE = "$NEW_ORDER/{$CUSTOMER_ID_ARG}"
    fun newOrder(id: Long) = "$NEW_ORDER/$id"

    const val ORDER_CONFIRMATION = "order_confirmation"
    const val ORDER_ID_ARG = "orderId"
    const val ORDER_CONFIRMATION_ROUTE = "$ORDER_CONFIRMATION/{$ORDER_ID_ARG}"
    fun orderConfirmation(orderId: Long) = "$ORDER_CONFIRMATION/$orderId"

    /** Routes that take over the whole screen (no global top bar / bottom nav). */
    fun isFullScreen(route: String?): Boolean =
        route != null && (route.startsWith("$NEW_ORDER/") || route.startsWith("$ORDER_CONFIRMATION/"))
}

@Composable
fun QuickSaleNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    productsQuery: String,
    customersQuery: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
    ) {
        composable(TopLevelDestination.HOME.route) {
            HomeScreen(onNavigate = navController::navigateToTopLevel)
        }
        composable(TopLevelDestination.PRODUCTS.route) {
            ProductsScreen(
                query = productsQuery,
                onProductClick = { id -> navController.navigate(Routes.productDetail(id)) },
            )
        }
        composable(TopLevelDestination.CUSTOMERS.route) {
            CustomersScreen(
                query = customersQuery,
                onCustomerClick = { id -> navController.navigate(Routes.newOrder(id)) },
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(snackbarHostState = snackbarHostState)
        }
        composable(
            route = "${Routes.PRODUCT_DETAIL}/{${Routes.PRODUCT_ID_ARG}}",
            arguments = listOf(navArgument(Routes.PRODUCT_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong(Routes.PRODUCT_ID_ARG) ?: 0L
            ProductDetailScreen(productId = id)
        }
        composable(
            route = Routes.NEW_ORDER_ROUTE,
            arguments = listOf(navArgument(Routes.CUSTOMER_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong(Routes.CUSTOMER_ID_ARG) ?: 0L
            NewOrderScreen(
                customerId = id,
                onBack = { navController.popBackStack() },
                onPlaced = { orderId ->
                    navController.navigate(Routes.orderConfirmation(orderId)) {
                        // Don't return to the order builder when leaving the confirmation.
                        popUpTo(Routes.NEW_ORDER_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.ORDER_CONFIRMATION_ROUTE,
            arguments = listOf(navArgument(Routes.ORDER_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            val orderId = backStackEntry.arguments?.getLong(Routes.ORDER_ID_ARG) ?: 0L
            OrderConfirmationScreen(
                orderId = orderId,
                onDone = {
                    navController.popBackStack(TopLevelDestination.CUSTOMERS.route, inclusive = false)
                },
            )
        }
    }
}

/** Navigates to a top-level tab with the standard single-top / save-restore behaviour. */
fun NavHostController.navigateToTopLevel(destination: TopLevelDestination) {
    navigate(destination.route) {
        popUpTo(graph.startDestinationId) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}
