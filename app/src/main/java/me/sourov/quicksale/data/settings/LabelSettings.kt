package me.sourov.quicksale.data.settings

/**
 * What to put on a printed product label and how to print it. The field toggles are edited in
 * Settings → Label printing; [copies] and [spacing] are adjusted per print in the bottom sheet.
 * [spacing] is the number of blank lines fed after each label (gap before the next one / tear-off).
 */
data class LabelSettings(
    val showName: Boolean = true,
    /**
     * The scannable barcode, which is always the product's EAN — the SKU is never encoded. A
     * product without an EAN has no barcode to print.
     */
    val showBarcode: Boolean = true,
    /** The EAN's digits, printed under the barcode so it can be read out or keyed in. */
    val showEanNumber: Boolean = true,
    /** The SKU as plain text. Printed for the counter to read, never encoded as the barcode. */
    val showSku: Boolean = true,
    val showPrice: Boolean = true,
    /**
     * The manufacturer's suggested retail price, printed above the selling price. Only stores whose
     * catalog carries an MSRP have one to print; for every other product the line is simply absent,
     * so leaving this on costs nothing.
     */
    val showMsrp: Boolean = true,
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
