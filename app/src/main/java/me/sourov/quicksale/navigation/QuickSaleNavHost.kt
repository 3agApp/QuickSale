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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import me.sourov.quicksale.appContainer
import me.sourov.quicksale.data.local.OrganizationStatus
import me.sourov.quicksale.ui.orders.CheckoutScreen
import me.sourov.quicksale.ui.orders.Customer
import me.sourov.quicksale.ui.orders.OrderConfirmationScreen
import me.sourov.quicksale.ui.orders.OrderDetailScreen
import me.sourov.quicksale.ui.orders.OrderDetailViewModel
import me.sourov.quicksale.ui.orders.OrderListScreen
import me.sourov.quicksale.ui.orders.OrderListViewModel
import me.sourov.quicksale.ui.orders.OrdersScreen
import me.sourov.quicksale.ui.orders.SellScreen
import me.sourov.quicksale.ui.orders.SellViewModel
import me.sourov.quicksale.ui.print.QuickPrintScreen
import me.sourov.quicksale.ui.organizations.OrganizationDetailScreen
import me.sourov.quicksale.ui.organizations.OrganizationsScreen
import me.sourov.quicksale.ui.organizations.PendingApprovalScreen
import me.sourov.quicksale.ui.products.ProductDetailScreen
import me.sourov.quicksale.ui.products.ProductsScreen
import me.sourov.quicksale.ui.settings.SettingsScreen
import me.sourov.quicksale.ui.settings.SettingsSection
import me.sourov.quicksale.ui.settings.SettingsSectionScreen

object Routes {
    const val PRODUCT_DETAIL = "product_detail"
    const val PRODUCT_ID_ARG = "productId"
    fun productDetail(id: Long) = "$PRODUCT_DETAIL/$id"

    const val SETTINGS = "settings"
    const val SETTINGS_SECTION = "settings_section"
    const val SETTINGS_SECTION_ARG = "section"
    fun settingsSection(section: SettingsSection) = "$SETTINGS_SECTION/${section.name}"

    const val ORGANIZATION_DETAIL = "organization_detail"
    const val ORGANIZATION_ID_ARG = "organizationId"
    fun organizationDetail(id: Long) = "$ORGANIZATION_DETAIL/$id"

    const val PENDING_REVIEW = "pending_review"
    fun pendingReview(id: Long) = "$PENDING_REVIEW/$id"

    const val ORDER_LIST = "order_list"
    const val ORDER_LIST_ROUTE = "$ORDER_LIST/{$ORGANIZATION_ID_ARG}"
    fun orderList(organizationId: Long) = "$ORDER_LIST/$organizationId"

    /**
     * The checkout carries no ids.
     *
     * An order used to be addressed as `cart/{org}/{member}` because you could not have one until
     * you had said who it was for. The cart is now a standing thing on the Sell tab that learns its
     * customer here, so there is nothing to put in the route — and one instance of it, which is
     * what stops a second order being built behind the first.
     */
    const val CHECKOUT = "checkout"

    const val ORDER_DETAIL = "order_detail"
    const val ORDER_ID_ARG = "orderId"
    const val ORDER_DETAIL_ROUTE = "$ORDER_DETAIL/{$ORDER_ID_ARG}"
    fun orderDetail(orderId: Long) = "$ORDER_DETAIL/$orderId"

    const val ORDER_CONFIRMATION = "order_confirmation"
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

    /**
     * Routes that take over the whole screen (no global top bar / bottom nav).
     *
     * These are the screens that carry a decision in their own bottom bar — Place order, Approve —
     * and a navigation bar stacked under one of those is two bars competing for the same thumb.
     *
     * Matched on the route's own base rather than by prefix: a prefix test made every future route
     * beginning with these words silently lose its chrome.
     */
    private val FULL_SCREEN_ROUTES = setOf(CHECKOUT, ORDER_CONFIRMATION, PENDING_REVIEW)

    fun isFullScreen(route: String?): Boolean = route?.toRouteBase() in FULL_SCREEN_ROUTES

    /** `order_confirmation/{id}?total={total}` → `order_confirmation`. */
    private fun String.toRouteBase(): String = substringBefore('/').substringBefore('?')
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
    startDestination: TopLevelDestination,
    sellViewModel: SellViewModel,
    productsQuery: String,
    organizationsQuery: String,
    modifier: Modifier = Modifier,
) {
    NavHost(
        navController = navController,
        startDestination = startDestination.route,
        modifier = modifier,
        enterTransition = { enter() },
        exitTransition = { exit() },
        popEnterTransition = { popEnter() },
        popExitTransition = { popExit() },
    ) {
        composable(TopLevelDestination.SELL.route) {
            SellScreen(
                viewModel = sellViewModel,
                onCheckout = { navController.navigate(Routes.CHECKOUT) },
            )
        }
        composable(TopLevelDestination.PRINT.route) {
            QuickPrintScreen()
        }
        composable(TopLevelDestination.ORDERS.route) {
            OrdersScreen(
                onOrderClick = { orderId -> navController.navigate(Routes.orderDetail(orderId)) },
            )
        }
        composable(TopLevelDestination.PRODUCTS.route) {
            ProductsScreen(
                query = productsQuery,
                onProductClick = { id -> navController.navigate(Routes.productDetail(id)) },
            )
        }
        composable(TopLevelDestination.ACCOUNTS.route) {
            OrganizationsScreen(
                query = organizationsQuery,
                onOrganizationClick = { organization ->
                    val route = if (organization.orgStatus == OrganizationStatus.PENDING) {
                        // An account waiting for approval isn't one you can sell to, so the tap
                        // goes to the only useful thing: reviewing it.
                        Routes.pendingReview(organization.id)
                    } else {
                        Routes.organizationDetail(organization.id)
                    }
                    navController.navigate(route)
                },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                onSectionClick = { section ->
                    navController.navigate(Routes.settingsSection(section))
                },
            )
        }
        composable(
            route = "${Routes.SETTINGS_SECTION}/{${Routes.SETTINGS_SECTION_ARG}}",
            arguments = listOf(navArgument(Routes.SETTINGS_SECTION_ARG) { type = NavType.StringType }),
        ) { backStackEntry ->
            val section = SettingsSection.fromName(
                backStackEntry.arguments?.getString(Routes.SETTINGS_SECTION_ARG),
            )
            // An unknown section can only come from a stale deep link; drop back to the list.
            if (section == null) {
                LaunchedEffect(Unit) { navController.popBackStack() }
            } else {
                SettingsSectionScreen(
                    section = section,
                    snackbarHostState = snackbarHostState,
                )
            }
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
                // Starting an order from the account side attaches the customer to the standing
                // cart and drops the operator on the till, rather than opening a second order.
                onStartOrder = { memberUserId ->
                    sellViewModel.selectCustomer(Customer(id, memberUserId))
                    navController.returnToSell()
                },
                onViewOrders = { navController.navigate(Routes.orderList(id)) },
            )
        }
        composable(
            route = Routes.ORDER_LIST_ROUTE,
            arguments = listOf(navArgument(Routes.ORGANIZATION_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            val context = LocalContext.current
            val container = remember(context) { context.appContainer }
            val organizationId = backStackEntry.arguments?.getLong(Routes.ORGANIZATION_ID_ARG) ?: 0L
            val viewModel: OrderListViewModel = viewModel(
                factory = OrderListViewModel.factory(
                    organizationId = organizationId,
                    settingsRepository = container.settings,
                ),
            )
            OrderListScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
                onOrderClick = { orderId -> navController.navigate(Routes.orderDetail(orderId)) },
            )
        }
        composable(
            route = Routes.ORDER_DETAIL_ROUTE,
            arguments = listOf(navArgument(Routes.ORDER_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            val context = LocalContext.current
            val container = remember(context) { context.appContainer }
            val orderId = backStackEntry.arguments?.getLong(Routes.ORDER_ID_ARG) ?: 0L
            val viewModel: OrderDetailViewModel = viewModel(
                factory = OrderDetailViewModel.factory(
                    orderId = orderId,
                    settingsRepository = container.settings,
                    productRepository = container.products,
                ),
            )
            OrderDetailScreen(
                viewModel = viewModel,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            route = "${Routes.PENDING_REVIEW}/{${Routes.ORGANIZATION_ID_ARG}}",
            arguments = listOf(navArgument(Routes.ORGANIZATION_ID_ARG) { type = NavType.LongType }),
        ) { backStackEntry ->
            PendingApprovalScreen(
                organizationId = backStackEntry.arguments?.getLong(Routes.ORGANIZATION_ID_ARG) ?: 0L,
                onBack = { navController.popBackStack() },
                onReviewed = { navController.popBackStack() },
            )
        }

        composable(Routes.CHECKOUT) {
            CheckoutScreen(
                viewModel = sellViewModel,
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
                        // Don't return to the checkout from the confirmation: the order exists on
                        // the store, and a second Place would create a second order.
                        popUpTo(Routes.CHECKOUT) { inclusive = true }
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
                // Back to the till, empty and ready for the next visitor.
                onDone = navController::returnToSell,
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

/**
 * Returns to the till from a screen stacked on top of it.
 *
 * Not [navigateToTopLevel]: that combination of `popUpTo(start) + launchSingleTop + restoreState`
 * resolves to doing nothing at all when the target is already the start destination sitting below
 * you, which left the order confirmation's Done button dead. Popping back to it is what the caller
 * actually means, and the navigate is only the fallback for a stack that somehow lacks it.
 */
fun NavHostController.returnToSell() {
    if (!popBackStack(TopLevelDestination.SELL.route, inclusive = false)) {
        navigateToTopLevel(TopLevelDestination.SELL)
    }
}
