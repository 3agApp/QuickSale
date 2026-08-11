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
 * what prints. Which fields appear (name, barcode, EAN number, SKU text, price, MSRP) comes from
 * [LabelSettings].
 *
 * The barcode *is* the product's EAN — the SKU is never encoded, only printed as text — and the
 * price carries the store's currency symbol.
 */
class LabelRenderer {

    fun render(product: Product, settings: LabelSettings = LabelSettings()): Bitmap {
        // Lay the label out at full size, then progressively tighter, and print the first version
        // that fits the stock. Everything switched on lands around 0.9; a label with fewer fields
        // never leaves full size. Shrinking beats dropping a field: a label missing its price is
        // wrong in a way nobody notices until the customer is at the counter.
        var scale = 1f
        var plan = plan(product, settings, scale)
        while (plan.height > MAX_HEIGHT_PX && scale > MIN_SCALE) {
            scale -= SCALE_STEP
            plan = plan(product, settings, scale)
        }
        return draw(plan, settings.media)
    }

    /** One laid-out label: everything measured, nothing drawn yet. */
    private class Plan(
        val width: Int,
        val height: Int,
        val padding: Int,
        val gap: Int,
        val nameLayout: StaticLayout?,
        val skuLine: String?,
        val skuPaint: Paint,
        val codeBitmap: Bitmap?,
        val eanLine: String?,
        val codePaint: Paint,
        val priceLine: String?,
        val pricePaint: Paint,
        val msrpLine: String?,
        val msrpPaint: Paint,
    )

    /**
     * Measures the whole label at [scale], where 1 means the full type sizes.
     *
     * Every vertical dimension scales together — type, gaps, padding and the barcode's height — so
     * the label keeps its proportions as it shrinks instead of collapsing one element at a time.
     * The barcode's *width* never scales: its bars stay one printer dot wide, because a re-encoded
     * narrower barcode is what stops scanning first.
     */
    private fun plan(product: Product, settings: LabelSettings, scale: Float): Plan {
        val width = LABEL_WIDTH_PX
        val padding = (PADDING * scale).roundToInt().coerceAtLeast(MIN_PADDING)
        val gap = (GAP * scale).roundToInt().coerceAtLeast(MIN_GAP)
        val contentWidth = width - padding * 2

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = CODE_TEXT_SIZE * scale
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        // The SKU reads a little smaller than the EAN's digits: it is the counter's fallback way of
        // naming the product, not the number anyone keys into a till.
        val skuPaint = Paint(codePaint).apply { textSize = SKU_TEXT_SIZE * scale }
        val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = PRICE_TEXT_SIZE * scale
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        // The MSRP is a reference price, so it sits below the price it is compared against.
        val msrpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = MSRP_TEXT_SIZE * scale
            textAlign = Paint.Align.CENTER
        }

        val nameLayout: StaticLayout? = product.name
            .takeIf { settings.showName && it.isNotBlank() }
            ?.let { buildName(it, namePaint, contentWidth, scale) }
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
        // The SKU prints as plain text for the counter to read. It is never encoded as the barcode.
        val skuLine = product.sku.trim().takeIf { settings.showSku && it.isNotBlank() }
            ?.let { "SKU $it" }
        val priceLine = product.price.trim()
            .takeIf { settings.showPrice && it.isNotBlank() }
            ?.let { "${CurrencyFormatter.symbol} $it" }
        // Only stores running an MSRP plugin carry one; everywhere else this line is simply absent.
        val msrpLine = product.msrp.trim()
            .takeIf { settings.showMsrp && it.isNotBlank() }
            ?.let { "MSRP ${CurrencyFormatter.symbol} $it" }

        var height = padding
        nameLayout?.let { height += it.height + gap }
        codeBitmap?.let { height += it.height + gap }
        eanLine?.let { height += lineHeight(codePaint) + gap }
        skuLine?.let { height += lineHeight(skuPaint) + gap }
        priceLine?.let { height += lineHeight(pricePaint) + gap }
        msrpLine?.let { height += lineHeight(msrpPaint) + gap }
        height += padding
        height = height.coerceAtLeast(MIN_HEIGHT_PX)

        return Plan(
            width = width,
            height = height,
            padding = padding,
            gap = gap,
            nameLayout = nameLayout,
            skuLine = skuLine,
            skuPaint = skuPaint,
            codeBitmap = codeBitmap,
            eanLine = eanLine,
            codePaint = codePaint,
            priceLine = priceLine,
            pricePaint = pricePaint,
            msrpLine = msrpLine,
            msrpPaint = msrpPaint,
        )
    }

    private fun draw(plan: Plan, media: LabelMedia): Bitmap {
        // The last word on the label's size. The plan is measured to fit, so the ceiling only ever
        // binds if some future field escapes the measurement — and a clipped label beats one that
        // feeds past the die cut and prints across the gap onto the next.
        //
        // On die-cut stock the label is always the full 40 mm, even when the content needs less:
        // the printer positions the sticker, not the ink, so a short bitmap would sit high on the
        // label and every product with a different set of fields would print somewhere else.
        // Centring the content in a full-height label makes them all land the same way.
        val content = plan.height.coerceAtMost(MAX_HEIGHT_PX)
        val height = if (media == LabelMedia.DIE_CUT) MAX_HEIGHT_PX else content
        val bitmap = createBitmap(plan.width, height)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }
        val centerX = plan.width / 2f

        // Top to bottom: name, SKU, barcode, the barcode's digits, price, MSRP. The SKU sits with
        // the name as the other way of naming the product, leaving the barcode and its number
        // together, and the MSRP directly under the price it is there to be compared against.
        var y = plan.padding + (height - content) / 2f
        plan.nameLayout?.let { layout ->
            canvas.withTranslation(plan.padding.toFloat(), y) {
                layout.draw(this)
            }
            y += layout.height + plan.gap
        }
        plan.skuLine?.let { y += drawCenteredLine(canvas, it, plan.skuPaint, centerX, y) + plan.gap }
        plan.codeBitmap?.let { code ->
            canvas.drawBitmap(code, (plan.width - code.width) / 2f, y, null)
            y += code.height + plan.gap
        }
        plan.eanLine?.let { y += drawCenteredLine(canvas, it, plan.codePaint, centerX, y) + plan.gap }
        plan.priceLine?.let { y += drawCenteredLine(canvas, it, plan.pricePaint, centerX, y) + plan.gap }
        plan.msrpLine?.let { y += drawCenteredLine(canvas, it, plan.msrpPaint, centerX, y) + plan.gap }
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
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines)
            .apply { if (ellipsize) setEllipsize(TextUtils.TruncateAt.END) }
            .setIncludePad(false)
            .build()

    private fun lineHeight(paint: Paint): Int {
        val fm = paint.fontMetrics
        return (fm.descent - fm.ascent).toInt()
    }

    /** Draws one centered line at vertical offset [top]; returns the line's height. */
    private fun drawCenteredLine(canvas: Canvas, text: String, paint: Paint, cx: Float, top: Float): Int {
        val fm = paint.fontMetrics
        canvas.drawText(text, cx, top - fm.ascent, paint)
        return (fm.descent - fm.ascent).toInt()
    }

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

        private const val MIN_HEIGHT_PX = 120

        /** How far the layout may shrink to fit the stock, and in what steps. */
        private const val MIN_SCALE = 0.6f
        private const val SCALE_STEP = 0.05f

        private const val PADDING = 14
        private const val GAP = 12
        private const val MIN_PADDING = 6
        private const val MIN_GAP = 5

        private const val BARCODE_HEIGHT = 72
        /** Roughly 6 mm of bars — below this a scanner starts missing the code at counter speed. */
        private const val MIN_BARCODE_HEIGHT = 48

        /** The name's full size, and how far it may shrink to fit [NAME_MAX_LINES]. */
        private const val NAME_TEXT_SIZE = 23f
        private const val NAME_MIN_TEXT_SIZE = 14f
        private const val NAME_TEXT_SIZE_STEP = 1f
        private const val NAME_MAX_LINES = 2

        private const val CODE_TEXT_SIZE = 26f
        private const val SKU_TEXT_SIZE = 22f
        private const val PRICE_TEXT_SIZE = 32f
        private const val MSRP_TEXT_SIZE = 24f
    }
}
