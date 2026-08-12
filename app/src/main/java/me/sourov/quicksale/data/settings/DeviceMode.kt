package me.sourov.quicksale.data.settings

/**
 * What this particular device is for.
 *
 * The app runs on two handhelds doing opposite jobs at the same fair: one has the printer and
 * spends the day labelling stock, the other has only a scanner and spends it taking orders. Making
 * both carry the whole app meant every operator navigated past four tabs they would never open.
 *
 * The mode decides the start destination and which tabs exist — nothing else. It is a shortcut, not
 * a permission: [BOTH] restores the full app, and the setting is one row deep in Settings.
 */
enum class DeviceMode(
    val title: String,
    val subtitle: String,
) {
    /** Scanner only. Opens on the cart. */
    SELL(
        title = "Sales",
        subtitle = "Scan products into an order and check out",
    ),

    /** Scanner and printer. Opens on the print loop. */
    LABEL(
        title = "Label station",
        subtitle = "Scan a product and print its shelf label",
    ),

    /** One device doing everything — the default when a device is used for both. */
    BOTH(
        title = "Both",
        subtitle = "Selling and label printing on this device",
    );

    companion object {
        /** Resolves the stored name; null when nothing has been chosen yet. */
        fun fromName(value: String?): DeviceMode? = entries.firstOrNull { it.name == value }
    }
}
