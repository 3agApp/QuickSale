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
    /** The brand, printed under the name. Absent for a product the store files under no brand. */
    val showBrand: Boolean = true,
    /**
     * The scannable barcode, which is always the product's EAN — the SKU is never encoded. A
     * product without an EAN has no barcode to print.
     */
    val showBarcode: Boolean = true,
    /** The EAN's digits, printed under the barcode so it can be read out or keyed in. */
    val showEanNumber: Boolean = true,
    /** The SKU as plain text. Printed for the counter to read, never encoded as the barcode. */
    val showSku: Boolean = true,
    /**
     * The product's pack size (`min_order_quantity`), printed under the SKU as "VE" — the trade's
     * own abbreviation for the unit a product is sold in. A product with no pack size prints "VE 1",
     * which is the true answer rather than a missing line.
     */
    val showPackSize: Boolean = true,
    val showPrice: Boolean = true,
    /**
     * The manufacturer's suggested retail price, printed above the selling price. Only stores whose
     * catalog carries an MSRP have one to print; for every other product the line is simply absent,
     * so leaving this on costs nothing.
     */
    val showMsrp: Boolean = true,
    val media: LabelMedia = LabelMedia.DIE_CUT,
    /**
     * How hard the head burns each dot, on the firmware's own 1–11 scale.
     *
     * Set on every job for the same reason the media mode is: density is a device-wide setting
     * (`Settings.Global.print_density`), so a printer left alone burns at whatever the last app to
     * touch it chose, and two terminals of the same model do not agree. A job that did not say
     * otherwise would print at the device's value — which is how one till turns out crisp labels
     * while another prints grey ones with bars dropped out of the barcode.
     */
    val density: Int = DEFAULT_DENSITY,
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

        /**
         * The firmware's own darkness scale, mirrored from its `PrintConfig$Density` constants —
         * `TOP_GRAY_SMALL` = 1 through `TOP_GRAY_LARGEST` = 11.
         *
         * [DEFAULT_DENSITY] deliberately sits above the 3 the hardware ships at. The margin is
         * there for the things that quietly steal burn energy in a working day — a low battery, a
         * head that has warmed up over a batch, a roll of cheaper stock — and it stops short of the
         * top of the scale, where the burn spreads past the dot and thickens every bar into its
         * neighbour. Both ends of that trade fail as an unscannable barcode, which is why this is
         * adjustable rather than simply set as high as it will go.
         */
        const val MIN_DENSITY = 1
        const val MAX_DENSITY = 11
        const val DEFAULT_DENSITY = 6
    }
}
