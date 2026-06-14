package me.sourov.quicksale.data.settings

/**
 * What to put on a printed product label and how to print it. The field toggles are edited in
 * Settings → Label printing; [copies] and [spacing] are adjusted per print in the bottom sheet.
 * [spacing] is the number of blank lines fed after each label (gap before the next one / tear-off).
 */
data class LabelSettings(
    val showName: Boolean = true,
    val showBarcode: Boolean = true,
    val showSku: Boolean = true,
    val showPrice: Boolean = true,
    val copies: Int = 1,
    val spacing: Int = 3,
) {
    companion object {
        const val MIN_COPIES = 1
        const val MAX_COPIES = 9
        const val MIN_SPACING = 0
        const val MAX_SPACING = 12
    }
}
