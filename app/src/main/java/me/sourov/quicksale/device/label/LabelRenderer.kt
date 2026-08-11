package me.sourov.quicksale.device.label

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import androidx.core.graphics.createBitmap
import androidx.core.graphics.withTranslation
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.settings.LabelMedia
import me.sourov.quicksale.data.settings.LabelSettings
import me.sourov.quicksale.device.printer.BldPrintManager
import me.sourov.quicksale.ui.CurrencyFormatter
import kotlin.math.roundToInt

/**
 * Renders a product label to a monochrome-friendly [Bitmap] that always fits the 53 × 40 mm label
 * stock. The same bitmap drives the on-screen preview and the thermal printer, so what you see is
 * what prints. Which fields appear comes from [LabelSettings].
 *
 * The label reads top-down as a wholesaler's shelf label: name, brand, SKU, VE, UVP, EK, then the
 * barcode and its digits. Everything above the barcode is one tight left-aligned block — the eye
 * runs down a single edge instead of hunting for the start of each centred line — and only the
 * barcode and its number are centred, since a scanner is aimed at the middle of the label.
 *
 * The block is deliberately compact: it is capped at [CONTENT_MAX_HEIGHT_PX] so at least
 * [MIN_FOOTER_PX] of every label stays blank at the bottom, for a price gun, a shelf marking or a
 * hand-written note.
 *
 * The barcode *is* the product's EAN — the SKU is never encoded, only printed as text — and the
 * prices are written exactly as the store's own website writes them, symbol and separators alike.
 */
class LabelRenderer {

    fun render(product: Product, settings: LabelSettings = LabelSettings()): Bitmap {
        // Lay the label out at full size, then progressively tighter, and print the first version
        // that leaves the footer blank. Shrinking beats dropping a field: a label missing its price
        // is wrong in a way nobody notices until the customer is at the counter.
        var scale = 1f
        var plan = plan(product, settings, scale)
        while (plan.height > CONTENT_MAX_HEIGHT_PX && scale > MIN_SCALE) {
            scale -= SCALE_STEP
            plan = plan(product, settings, scale)
        }
        return draw(plan, settings.media)
    }

    /** One laid-out label: everything measured and ordered, nothing drawn yet. */
    private class Plan(val width: Int, val height: Int, val padding: Int, val rows: List<Row>)

    /** An element and the blank space that follows it. */
    private class Row(val element: Element, var gapAfter: Int)

    private sealed interface Element {
        val height: Int

        /** Text that may wrap, drawn from the label's left padding. */
        class Wrapped(val layout: StaticLayout) : Element {
            override val height: Int get() = layout.height
        }

        /** A single line of text; the paint's own [Paint.textAlign] decides where it sits. */
        class Line(val text: String, val paint: Paint) : Element {
            override val height: Int = paint.fontMetrics.let { (it.descent - it.ascent).toInt() }
        }

        /** The barcode image, centred. */
        class Code(val bitmap: Bitmap) : Element {
            override val height: Int get() = bitmap.height
        }
    }

    /**
     * Measures the whole label at [scale], where 1 means the full type sizes.
     *
     * Type, gaps and the barcode's height scale together, so the label keeps its proportions as it
     * shrinks instead of collapsing one element at a time. Two dimensions deliberately don't:
     *
     * The barcode's *width*, because its bars stay one printer dot wide — a re-encoded narrower
     * barcode is what stops scanning first.
     *
     * The [PADDING], because it is the margin every label shares. Scaling it would move the top and
     * left edges of the printed block by whichever scale that particular product happened to need,
     * so a shelf of labels would start on a different line and a different column product by
     * product — which is exactly the alignment the layout exists to hold. It is 14 px out of 320,
     * so keeping it fixed costs the shrink loop almost nothing.
     */
    private fun plan(product: Product, settings: LabelSettings, scale: Float): Plan {
        val width = LABEL_WIDTH_PX
        val padding = PADDING
        // The stacked text lines sit almost on top of each other, so they read as one block rather
        // than as separate facts, and the space that buys goes to the blank footer.
        val lineGap = (LINE_GAP * scale).roundToInt().coerceAtLeast(MIN_LINE_GAP)
        val blockGap = (BLOCK_GAP * scale).roundToInt().coerceAtLeast(MIN_BLOCK_GAP)
        val contentWidth = width - padding * 2

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        // Brand, SKU, VE and UVP are the same size and weight: lines of the same kind of detail.
        val infoPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = INFO_TEXT_SIZE * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.LEFT
        }
        // The selling price is what the label is read for, so it is the one line set large.
        val pricePaint = Paint(infoPaint).apply { textSize = PRICE_TEXT_SIZE * scale }
        // The barcode's digits, centred under the bars they repeat, and monospaced so a long number
        // can be read back in groups or keyed in without losing the place.
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = CODE_TEXT_SIZE * scale
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }

        val nameLayout: StaticLayout? = product.name
            .takeIf { settings.showName && it.isNotBlank() }
            ?.let { buildName(it, namePaint, contentWidth, scale) }
        val brandLine = product.brand.trim().takeIf { settings.showBrand && it.isNotBlank() }
        // Printed bare: on a shelf label the code under the brand is the SKU, and the word costs a
        // third of the line's width to say what the number already says.
        val skuLine = product.sku.trim().takeIf { settings.showSku && it.isNotBlank() }
        // The pack size the product is sold in, under the SKU it belongs to. Unlike the prices this
        // line always has a value — a product with no minimum is sold in ones — so it always prints.
        val packSizeLine = product.minOrderQuantity
            .takeIf { settings.showPackSize }
            ?.let { "$PACK_SIZE_PREFIX ${it.coerceAtLeast(1)}" }
        // Only stores running an MSRP plugin carry one; everywhere else this line is simply absent.
        val msrpLine = product.msrp.trim()
            .takeIf { settings.showMsrp && it.isNotBlank() }
            ?.let { "$MSRP_PREFIX ${CurrencyFormatter.format(it)}" }
        val priceLine = product.price.trim()
            .takeIf { settings.showPrice && it.isNotBlank() }
            ?.let { "$PRICE_PREFIX ${CurrencyFormatter.format(it)}" }
        // The barcode is the EAN and nothing else. A product the store has no EAN for prints no
        // barcode: a label whose barcode is really a SKU scans as a code the counter can't sell,
        // and it looks identical to a correct one, so the missing barcode is the safer failure.
        val ean = product.ean.trim()
        val barcodeHeight = (BARCODE_HEIGHT * scale).roundToInt().coerceAtLeast(MIN_BARCODE_HEIGHT)
        val codeBitmap: Bitmap? = ean
            .takeIf { settings.showBarcode && it.isNotBlank() }
            ?.let { encode(it, contentWidth, barcodeHeight) }
        // The same number in digits, so a barcode that won't scan can still be read or keyed in.
        val eanLine = ean.takeIf { settings.showEanNumber && it.isNotBlank() }

        val rows = mutableListOf<Row>()
        nameLayout?.let { rows += Row(Element.Wrapped(it), lineGap) }
        brandLine?.let { rows += Row(Element.Line(it, infoPaint), lineGap) }
        skuLine?.let { rows += Row(Element.Line(it, infoPaint), lineGap) }
        packSizeLine?.let { rows += Row(Element.Line(it, infoPaint), lineGap) }
        msrpLine?.let { rows += Row(Element.Line(it, infoPaint), lineGap) }
        priceLine?.let { rows += Row(Element.Line(it, pricePaint), lineGap) }
        // Whatever the text block ends with, the barcode gets air above it — it is a separate thing
        // to look at, and bars crowded against type are harder for a scanner to pick out.
        if (codeBitmap != null || eanLine != null) rows.lastOrNull()?.gapAfter = blockGap
        codeBitmap?.let { rows += Row(Element.Code(it), lineGap) }
        eanLine?.let { rows += Row(Element.Line(it, codePaint), lineGap) }
        rows.lastOrNull()?.gapAfter = 0

        val height = (padding * 2 + rows.sumOf { it.element.height + it.gapAfter })
            .coerceAtLeast(MIN_HEIGHT_PX)

        return Plan(width = width, height = height, padding = padding, rows = rows)
    }

    private fun draw(plan: Plan, media: LabelMedia): Bitmap {
        // The last word on the label's size. The plan is measured to fit, so the ceiling only ever
        // binds if some future field escapes the measurement — and a clipped label beats one that
        // feeds past the die cut and prints across the gap onto the next.
        //
        // On die-cut stock the label is always the full 40 mm, even though the content needs less:
        // the printer positions the sticker, not the ink. The content hangs from the top, so every
        // product starts on the same line whichever fields it has, and the space it doesn't use is
        // left blank at the bottom instead of being shared out around it.
        //
        // A continuous roll has no such height to fill, so the footer is added to the bitmap
        // instead: it is part of the label, not the gap to the next one, and leaving it to the
        // spacing setting would mean the printed label and the preview no longer agree.
        val content = plan.height.coerceAtMost(MAX_HEIGHT_PX)
        val height = if (media == LabelMedia.DIE_CUT) {
            MAX_HEIGHT_PX
        } else {
            (content + MIN_FOOTER_PX).coerceAtMost(MAX_HEIGHT_PX)
        }
        val bitmap = createBitmap(plan.width, height)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val left = plan.padding.toFloat()
        val centerX = plan.width / 2f

        var y = plan.padding.toFloat()
        plan.rows.forEach { row ->
            when (val element = row.element) {
                is Element.Wrapped -> canvas.withTranslation(left, y) { element.layout.draw(this) }
                is Element.Line -> {
                    val x = if (element.paint.textAlign == Paint.Align.CENTER) centerX else left
                    canvas.drawText(element.text, x, y - element.paint.fontMetrics.ascent, element.paint)
                }
                is Element.Code ->
                    canvas.drawBitmap(element.bitmap, (plan.width - element.bitmap.width) / 2f, y, null)
            }
            y += row.element.height + row.gapAfter
        }
        return bitmap
    }

    /**
     * Lays the product's name out so all of it fits within [NAME_MAX_LINES], shrinking the text a
     * step at a time until it does.
     *
     * A label is read at arm's length off a shelf, so a truncated name is a worse failure than a
     * slightly smaller one — "Stainless Steel Vacuum Insulated Water…" names no product anyone can
     * pick up. Shrinking stops at [NAME_MIN_TEXT_SIZE]: past that the name stops being legible on
     * a thermal print at all, and a name that still overflows there (a single unbreakable word, or
     * a wholesale-length description in the title field) is ellipsized rather than allowed to push
     * the barcode and price off the label.
     */
    private fun buildName(text: String, paint: TextPaint, width: Int, scale: Float): StaticLayout {
        val floor = NAME_MIN_TEXT_SIZE * scale
        var size = NAME_TEXT_SIZE * scale
        while (size >= floor) {
            paint.textSize = size
            val layout = buildText(text, paint, width, maxLines = Int.MAX_VALUE, ellipsize = false)
            if (layout.lineCount <= NAME_MAX_LINES) return layout
            size -= NAME_TEXT_SIZE_STEP
        }
        paint.textSize = floor
        return buildText(text, paint, width, maxLines = NAME_MAX_LINES, ellipsize = true)
    }

    private fun buildText(
        text: String,
        paint: TextPaint,
        width: Int,
        maxLines: Int,
        ellipsize: Boolean,
    ): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setMaxLines(maxLines)
            .apply { if (ellipsize) setEllipsize(TextUtils.TruncateAt.END) }
            .setIncludePad(false)
            .build()

    /**
     * Picks the symbology the number actually is. A 13- or 8-digit number is a real EAN and prints
     * as one, so any retail scanner reads it as the product's GTIN. Anything else the store keeps in
     * its EAN field — a 12-digit UPC, a 14-digit GTIN, a number with a bad check digit — prints as
     * Code 128, which encodes it verbatim rather than dropping the barcode.
     */
    private fun formatFor(content: String): BarcodeFormat = when {
        !content.all { it.isDigit() } -> BarcodeFormat.CODE_128
        content.length == 13 -> BarcodeFormat.EAN_13
        content.length == 8 -> BarcodeFormat.EAN_8
        else -> BarcodeFormat.CODE_128
    }

    private fun encode(content: String, w: Int, h: Int): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 0)
        val format = formatFor(content)
        val matrix = runCatching { MultiFormatWriter().encode(content, format, w, h, hints) }
            // A 13-digit number whose check digit is wrong is not an EAN; ZXing refuses it rather
            // than printing a barcode that scans as a different product. Code 128 takes it as-is.
            .getOrElse { MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, w, h, hints) }
        val mw = matrix.width
        val mh = matrix.height
        val pixels = IntArray(mw * mh)
        for (y in 0 until mh) {
            val offset = y * mw
            for (x in 0 until mw) {
                pixels[offset + x] = if (matrix[x, y]) Color.BLACK else Color.WHITE
            }
        }
        Bitmap.createBitmap(pixels, mw, mh, Bitmap.Config.ARGB_8888)
    }.getOrNull()

    companion object {
        /**
         * The label stock in the till, measured off the roll, and the print head's resolution:
         * 203 dpi, 8 dots per mm.
         *
         * The width is the conservative end of a 52–53 mm hand measurement, and it does not need
         * to be more exact than that: the print head is 384 dots ≈ 48 mm, so it is the narrower of
         * the two whichever figure is right, and [LABEL_WIDTH_PX] comes out at 384 for any stock
         * from 48 mm up. Height is the one that has to be right, and it is the one measured
         * cleanly — the print is bounded by it exactly.
         */
        const val LABEL_WIDTH_MM = 52
        const val LABEL_HEIGHT_MM = 40
        const val DOTS_PER_MM = 8

        /**
         * Nothing printed may exceed the die cut. Height binds and is enforced on every label;
         * width is taken as the smaller of head and stock rather than assumed to be the head's.
         * The content sits [PADDING] inside this, so the ink stops ~1.75 mm short of the cut and
         * absorbs the roll's own feed tolerance.
         */
        const val MAX_HEIGHT_PX = LABEL_HEIGHT_MM * DOTS_PER_MM
        val LABEL_WIDTH_PX = minOf(BldPrintManager.WIDTH_PIXEL, LABEL_WIDTH_MM * DOTS_PER_MM)

        /**
         * The bottom of the label the content may not reach into — 8 mm kept deliberately blank, so
         * there is somewhere to put a price-gun sticker or a hand-written note. This is what the
         * layout shrinks to satisfy, so it binds before [MAX_HEIGHT_PX] ever does, and every label
         * carries it whichever paper is loaded.
         */
        const val MIN_FOOTER_PX = 64
        const val CONTENT_MAX_HEIGHT_PX = MAX_HEIGHT_PX - MIN_FOOTER_PX

        private const val MIN_HEIGHT_PX = 120

        /** How far the layout may shrink to fit the stock, and in what steps. */
        private const val MIN_SCALE = 0.6f
        private const val SCALE_STEP = 0.05f

        /** The margin every label shares, whatever its content had to shrink to. */
        private const val PADDING = 14

        /** Between the stacked text lines, and between that block and the barcode. */
        private const val LINE_GAP = 3
        private const val BLOCK_GAP = 14
        private const val MIN_LINE_GAP = 2
        private const val MIN_BLOCK_GAP = 8

        private const val BARCODE_HEIGHT = 60
        /** Roughly 6 mm of bars — below this a scanner starts missing the code at counter speed. */
        private const val MIN_BARCODE_HEIGHT = 48

        /** The name's full size, and how far it may shrink to fit [NAME_MAX_LINES]. */
        private const val NAME_TEXT_SIZE = 23f
        private const val NAME_MIN_TEXT_SIZE = 14f
        private const val NAME_TEXT_SIZE_STEP = 1f
        private const val NAME_MAX_LINES = 2

        private const val INFO_TEXT_SIZE = 21f
        private const val PRICE_TEXT_SIZE = 32f
        private const val CODE_TEXT_SIZE = 22f

        /**
         * The trade's own names for the two prices, printed ahead of each: UVP is the recommended
         * retail price, EK the price the label is really about. Two letters carry the distinction
         * that "MSRP" and an unlabelled number left to the reader's guess.
         */
        private const val MSRP_PREFIX = "UVP"
        private const val PRICE_PREFIX = "EK"

        /**
         * Verpackungseinheit — the pack the product is sold in, and the trade's own abbreviation
         * for it. "VE 6" tells the shelf that six is the smallest number that leaves the warehouse.
         */
        private const val PACK_SIZE_PREFIX = "VE"
    }
}
