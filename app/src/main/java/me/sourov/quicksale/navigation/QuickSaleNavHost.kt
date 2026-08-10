package me.sourov.quicksale.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import me.sourov.quicksale.ui.home.HomeScreen
import me.sourov.quicksale.ui.orders.NewOrderScreen
import me.sourov.quicksale.ui.orders.OrderConfirmationScreen
import me.sourov.quicksale.ui.organizations.OrganizationDetailScreen
import me.sourov.quicksale.ui.organizations.OrganizationsScreen
import me.sourov.quicksale.ui.products.ProductDetailScreen
import me.sourov.quicksale.ui.products.ProductsScreen
import me.sourov.quicksale.ui.settings.SettingsScreen

object Routes {
    const val PRODUCT_DETAIL = "product_detail"
    const val PRODUCT_ID_ARG = "productId"
    fun productDetail(id: Long) = "$PRODUCT_DETAIL/$id"

    const val ORGANIZATION_DETAIL = "organization_detail"
    const val ORGANIZATION_ID_ARG = "organizationId"
    fun organizationDetail(id: Long) = "$ORGANIZATION_DETAIL/$id"

    const val NEW_ORDER = "new_order"
    const val MEMBER_USER_ID_ARG = "memberUserId"
    const val NEW_ORDER_ROUTE = "$NEW_ORDER/{$ORGANIZATION_ID_ARG}/{$MEMBER_USER_ID_ARG}"

    /** An order is always for one member of one organization — both identify it. */
    fun newOrder(organizationId: Long, memberUserId: Long) =
        "$NEW_ORDER/$organizationId/$memberUserId"

    const val ORDER_CONFIRMATION = "order_confirmation"
    const val ORDER_ID_ARG = "orderId"
    const val ORDER_TOTAL_ARG = "total"
    const val ORDER_TAX_ARG = "tax"
    const val ORDER_SHIPPING_ARG = "shipping"
    const val ORDER_DISCOUNT_ARG = "discount"
    const val ORDER_ORGANIZATION_ARG = "organization"
    const val ORDER_LOCATION_ARG = "location"
    const val ORDER_CONFIRMATION_ROUTE = "$ORDER_CONFIRMATION/{$ORDER_ID_ARG}" +
        "?$ORDER_TOTAL_ARG={$ORDER_TOTAL_ARG}&$ORDER_TAX_ARG={$ORDER_TAX_ARG}" +
        "&$ORDER_SHIPPING_ARG={$ORDER_SHIPPING_ARG}&$ORDER_DISCOUNT_ARG={$ORDER_DISCOUNT_ARG}" +
        "&$ORDER_ORGANIZATION_ARG={$ORDER_ORGANIZATION_ARG}&$ORDER_LOCATION_ARG={$ORDER_LOCATION_ARG}"

    fun orderConfirmation(
        orderId: Long,
        total: String,
        tax: String,
        shipping: String,
        discount: String,
        organization: String,
        location: String,
    ): String {
        // Totals are plain decimal strings from the API; strip anything else so the route is safe.
        fun amount(value: String) = value.filter { it.isDigit() || it == '.' || it == '-' }
        return "$ORDER_CONFIRMATION/$orderId" +
            "?$ORDER_TOTAL_ARG=${amount(total)}&$ORDER_TAX_ARG=${amount(tax)}" +
            "&$ORDER_SHIPPING_ARG=${amount(shipping)}&$ORDER_DISCOUNT_ARG=${amount(discount)}" +
            "&$ORDER_ORGANIZATION_ARG=${organization.asRouteText()}" +
            "&$ORDER_LOCATION_ARG=${location.asRouteText()}"
    }

    /** Names can carry anything, so they're encoded before being spliced into a route. */
    private fun String.asRouteText(): String =
        java.net.URLEncoder.encode(this, "UTF-8").replace("+", "%20")

    /** Routes that take over the whole screen (no global top bar / bottom nav). */
    fun isFullScreen(route: String?): Boolean =
        route != null && (route.startsWith(NEW_ORDER) || route.startsWith(ORDER_CONFIRMATION))
}

/**
 * Motion between screens.
 *
 * Top-level tabs use a fade-through — they're siblings, and sliding between them implies an order
 * the bottom bar doesn't have. Pushing into a detail screen slides horizontally, which does carry
 * a direction, and reverses on the way back.
 */
private const val NAV_DURATION = 280
private val navEasing = FastOutSlowInEasing

private fun isTopLevel(entry: NavBackStackEntry): Boolean =
    TopLevelDestination.fromRoute(entry.destination.route) != null

private fun AnimatedContentTransitionScope<NavBackStackEntry>.enter(): EnterTransition =
    if (isTopLevel(initialState) && isTopLevel(targetState)) {
        fadeIn(tween(NAV_DURATION, easing = navEasing)) +
            scaleIn(tween(NAV_DURATION, easing = navEasing), initialScale = 0.96f)
    } else {
        slideIntoContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(NAV_DURATION, easing = navEasing),
        ) + fadeIn(tween(NAV_DURATION, easing = navEasing))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.exit(): ExitTransition =
    if (isTopLevel(initialState) && isTopLevel(targetState)) {
        fadeOut(tween(NAV_DURATION / 2, easing = navEasing)) +
            scaleOut(tween(NAV_DURATION, easing = navEasing), targetScale = 1.04f)
    } else {
        slideOutOfContainer(
            towards = AnimatedContentTransitionScope.SlideDirection.Start,
            animationSpec = tween(NAV_DURATION, easing = navEasing),
        ) + fadeOut(tween(NAV_DURATION, easing = navEasing))
    }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popEnter(): EnterTransition =
    slideIntoContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(NAV_DURATION, easing = navEasing),
    ) + fadeIn(tween(NAV_DURATION, easing = navEasing))

private fun AnimatedContentTransitionScope<NavBackStackEntry>.popExit(): ExitTransition =
    slideOutOfContainer(
        towards = AnimatedContentTransitionScope.SlideDirection.End,
        animationSpec = tween(NAV_DURATION, easing = navEasing),
    ) + fadeOut(tween(NAV_DURATION, easing = navEasing))

@Composable
fun QuickSaleNavHost(
    navController: NavHostController,
    snackbarHostState: SnackbarHostState,
    productsQuery: String,
    organizationsQuery: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.HOME.route,
        modifier = modifier,
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
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
        composable(TopLevelDestination.ORGANIZATIONS.route) {
            OrganizationsScreen(
                query = organizationsQuery,
                onOrganizationClick = { id -> navController.navigate(Routes.organizationDetail(id)) },
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
            route = "${Routes.ORGANIZATION_DETAIL}/{${Routes.ORGANIZATION_ID_ARG}}",
            arguments = listOf(navArgument(Routes.ORGANIZATION_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            val id = backStackEntry.arguments?.getLong(Routes.ORGANIZATION_ID_ARG) ?: 0L
            OrganizationDetailScreen(
                organizationId = id,
                onStartOrder = { memberUserId ->
                    navController.navigate(Routes.newOrder(id, memberUserId))
                },
            )
        }
        composable(
            route = Routes.NEW_ORDER_ROUTE,
            arguments = listOf(
                navArgument(Routes.ORGANIZATION_ID_ARG) { type = NavType.LongType },
                navArgument(Routes.MEMBER_USER_ID_ARG) { type = NavType.LongType },
            ),
        ) { backStackEntry ->
            val arguments = backStackEntry.arguments
            NewOrderScreen(
                organizationId = arguments?.getLong(Routes.ORGANIZATION_ID_ARG) ?: 0L,
                memberUserId = arguments?.getLong(Routes.MEMBER_USER_ID_ARG) ?: 0L,
                onBack = { navController.popBackStack() },
                onPlaced = { result ->
                    val route = Routes.orderConfirmation(
                        orderId = result.remoteId,
                        total = result.total,
                        tax = result.totalTax,
                        shipping = result.shippingTotal,
                        discount = result.discountTotal,
                        organization = result.organizationName,
                        location = result.locationName,
                    )
                    navController.navigate(route) {
                        // Don't return to the order builder when leaving the confirmation.
                        popUpTo(Routes.NEW_ORDER_ROUTE) { inclusive = true }
                    }
                },
            )
        }
        composable(
            route = Routes.ORDER_CONFIRMATION_ROUTE,
            arguments = listOf(
                navArgument(Routes.ORDER_ID_ARG) { type = NavType.LongType },
                navArgument(Routes.ORDER_TOTAL_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ORDER_TAX_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ORDER_SHIPPING_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ORDER_DISCOUNT_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ORDER_ORGANIZATION_ARG) { type = NavType.StringType; defaultValue = "" },
                navArgument(Routes.ORDER_LOCATION_ARG) { type = NavType.StringType; defaultValue = "" },
            ),
        ) { backStackEntry ->
            val args = backStackEntry.arguments
            OrderConfirmationScreen(
                orderId = args?.getLong(Routes.ORDER_ID_ARG) ?: 0L,
                total = args?.getString(Routes.ORDER_TOTAL_ARG).orEmpty(),
                totalTax = args?.getString(Routes.ORDER_TAX_ARG).orEmpty(),
                shippingTotal = args?.getString(Routes.ORDER_SHIPPING_ARG).orEmpty(),
                discountTotal = args?.getString(Routes.ORDER_DISCOUNT_ARG).orEmpty(),
                organizationName = args?.getString(Routes.ORDER_ORGANIZATION_ARG).orEmpty(),
                locationName = args?.getString(Routes.ORDER_LOCATION_ARG).orEmpty(),
                onDone = {
                    navController.popBackStack(TopLevelDestination.ORGANIZATIONS.route, inclusive = false)
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
