package me.sourov.quicksale.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Print
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The primary sections reachable from the bottom navigation bar.
 * Order here is the order shown in the bar.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Whether the top bar offers search on this tab. */
    val searchable: Boolean = false,
    val searchPlaceholder: String = "",
) {
    HOME(
        route = "home",
        label = "Home",
        selectedIcon = Icons.Filled.Home,
        unselectedIcon = Icons.Outlined.Home,
    ),
    PRODUCTS(
        route = "products",
        label = "Products",
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2,
        searchable = true,
        searchPlaceholder = "Search products",
    ),

    /**
     * The B2B customer list. Labelled "Accounts" rather than "Organizations" because it is a
     * bottom-bar tab and has to stay short and legible at 11sp.
     */
    ORGANIZATIONS(
        route = "organizations",
        label = "Accounts",
        selectedIcon = Icons.Filled.Business,
        unselectedIcon = Icons.Outlined.Business,
        searchable = true,
        searchPlaceholder = "Search organizations",
    ),

    /**
     * Scan-to-print shelf labels. Its own tab rather than a corner of Products because it is a
     * different job done in a different posture — a box in one hand, working through a delivery —
     * and it must be reachable without navigating through a list first.
     */
    QUICK_PRINT(
        route = "quick_print",
        label = "Quick print",
        selectedIcon = Icons.Filled.Print,
        unselectedIcon = Icons.Outlined.Print,
    ),
    SETTINGS(
        route = "settings",
        label = "Settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
    );

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? = entries.find { it.route == route }
    }
}
