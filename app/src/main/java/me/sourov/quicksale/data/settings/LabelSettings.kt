package me.sourov.quicksale.data.settings

/**
 * The paper in the printer, which decides how a label is positioned.
 *
 * The S60B's firmware is built for [DIE_CUT] stock — its own print-test app specifies 58 × 40 mm
 * media and reports "abnormal black mark detection" when it can't find the mark — so that is the
 * default. [CONTINUOUS] remains for tills loaded with plain receipt roll, where there is no mark to
 * find and the gap between labels is whatever blank paper gets fed after each one.
 */
enum class LabelMedia(val slug: String, val label: String, val description: String) {
    /**
     * Die-cut labels with a black mark on the back of the liner. The printer seeks the mark before
     * each label, so every one is positioned independently and nothing drifts down the roll.
     */
    DIE_CUT("die_cut", "Die-cut labels", "53 × 40 mm stock. The printer finds each label itself."),

    /**
     * Plain continuous roll. Nothing tells the printer where a label ends, so the gap is the blank
     * lines fed after each one and any mismatch accumulates over a roll.
     */
    CONTINUOUS("continuous", "Continuous roll", "Plain paper. The gap is the blank lines fed after each label.");

    companion object {
        fun fromSlug(slug: String?): LabelMedia = entries.firstOrNull { it.slug == slug } ?: DIE_CUT
    }
}

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
    val media: LabelMedia = LabelMedia.DIE_CUT,
    val copies: Int = 1,
    val spacing: Int = 3,
) {
    /** True when the printer positions each label itself and [spacing] has nothing left to do. */
    val feedsToNextLabel: Boolean get() = media == LabelMedia.DIE_CUT

    companion object {
        const val MIN_COPIES = 1
        const val MAX_COPIES = 9
        const val MIN_SPACING = 0
        const val MAX_SPACING = 12
    }
}
