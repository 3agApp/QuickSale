package me.sourov.quicksale.ui.settings

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.QrCode2
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.SystemUpdate
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * The settings screen's top level: one row per area, each opening its own page.
 *
 * The counter only ever comes here for one thing — connect the store, change the order status, fix
 * what a label prints — and a single scrolling page made every one of those a hunt.
 *
 * Declaration order is the order the rows appear, so the first-run path (connect the store, check
 * the data came down) sits at the top and the rarely-touched pages at the bottom.
 */
enum class SettingsSection(
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
) {
    STORE(
        title = "Store connection",
        subtitle = "Site URL and API keys",
        icon = Icons.Outlined.Language,
    ),
    SYNC(
        title = "Sync",
        subtitle = "Keep this device's copy of the store current",
        icon = Icons.Outlined.Sync,
    ),
    ORDERS(
        title = "Orders",
        subtitle = "Status applied to orders placed from QuickSale",
        icon = Icons.AutoMirrored.Outlined.ReceiptLong,
    ),
    LABELS(
        title = "Label printing",
        subtitle = "What to include on a printed product label",
        icon = Icons.Outlined.QrCode2,
    ),
    SCANNER(
        title = "Scanner",
        subtitle = "How the handheld scanner sends data to QuickSale",
        icon = Icons.Outlined.QrCodeScanner,
    ),
    UPDATES(
        title = "App updates",
        subtitle = "Installed version and update checks",
        icon = Icons.Outlined.SystemUpdate,
    );

    companion object {
        /** Resolves the route argument back to a section; null when the name is unknown. */
        fun fromName(value: String?): SettingsSection? = entries.firstOrNull { it.name == value }
    }
}
