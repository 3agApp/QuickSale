package me.sourov.quicksale.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.filled.Business
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PointOfSale
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.outlined.Business
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PointOfSale
import androidx.compose.material.icons.outlined.Print
import androidx.compose.ui.graphics.vector.ImageVector
import me.sourov.quicksale.data.settings.DeviceMode

/**
 * The primary sections reachable from the bottom navigation bar.
 *
 * Declaration order is the order shown, and [modes] decides which of them a given device sees at
 * all — a label station has no use for the checkout, and a scanner-only device has no printer. One
 * canonical order filtered per mode keeps the bar predictable for someone who picks up the other
 * device mid-shift.
 *
 * Settings is deliberately *not* here. It is a setup surface visited a handful of times per fair,
 * and it was spending a fifth of the bar to say so; it lives in the top bar now.
 */
enum class TopLevelDestination(
    val route: String,
    val label: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    /** Which device modes show this tab. */
    val modes: Set<DeviceMode>,
    /** Whether the top bar offers search on this tab. */
    val searchable: Boolean = false,
    val searchPlaceholder: String = "",
) {
    /**
     * The till itself: a standing cart with the scanner armed. This is the app's front door on a
     * selling device — the first scan happens here with no navigation before it.
     */
    SELL(
        route = "sell",
        label = "Sell",
        selectedIcon = Icons.Filled.PointOfSale,
        unselectedIcon = Icons.Outlined.PointOfSale,
        modes = setOf(DeviceMode.SELL, DeviceMode.BOTH),
    ),

    /** Scan-to-print shelf labels — the whole job of the printer device, so it is its front door. */
    PRINT(
        route = "print",
        label = "Print",
        selectedIcon = Icons.Filled.Print,
        unselectedIcon = Icons.Outlined.Print,
        modes = setOf(DeviceMode.LABEL, DeviceMode.BOTH),
    ),

    /** Every order this store has taken, newest first — "what have we sold today". */
    ORDERS(
        route = "orders",
        label = "Orders",
        selectedIcon = Icons.AutoMirrored.Filled.ReceiptLong,
        unselectedIcon = Icons.AutoMirrored.Outlined.ReceiptLong,
        modes = setOf(DeviceMode.SELL, DeviceMode.BOTH),
    ),

    /**
     * The B2B customer list. Labelled "Accounts" rather than "Organizations" because it is a
     * bottom-bar tab and has to stay short and legible at 11sp.
     */
    ACCOUNTS(
        route = "accounts",
        label = "Accounts",
        selectedIcon = Icons.Filled.Business,
        unselectedIcon = Icons.Outlined.Business,
        modes = setOf(DeviceMode.SELL, DeviceMode.BOTH),
        searchable = true,
        searchPlaceholder = "Search organizations",
    ),

    /** The catalog. Both devices need it — to answer "do you have…", and to print a one-off label. */
    PRODUCTS(
        route = "products",
        label = "Products",
        selectedIcon = Icons.Filled.Inventory2,
        unselectedIcon = Icons.Outlined.Inventory2,
        modes = DeviceMode.entries.toSet(),
        searchable = true,
        searchPlaceholder = "Search products",
    );

    companion object {
        fun fromRoute(route: String?): TopLevelDestination? = entries.find { it.route == route }

        /** The tabs this device shows, in bar order. */
        fun forMode(mode: DeviceMode): List<TopLevelDestination> =
            entries.filter { mode in it.modes }

        /** Where the app opens: the job this device exists to do. */
        fun startFor(mode: DeviceMode): TopLevelDestination =
            if (mode == DeviceMode.LABEL) PRINT else SELL
    }
}
