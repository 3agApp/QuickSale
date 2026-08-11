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
import me.sourov.quicksale.data.settings.LabelSettings
import me.sourov.quicksale.device.printer.BldPrintManager
import me.sourov.quicksale.ui.CurrencyFormatter

/**
 * Renders a product label to a monochrome-friendly [Bitmap] sized to the printer width (384 px for
 * 58 mm). The same bitmap drives the on-screen preview and the thermal printer, so what you see is
 * what prints. Which fields appear (name, barcode, EAN number, SKU text, price) comes from
 * [LabelSettings].
 *
 * The barcode *is* the product's EAN — the SKU is never encoded, only printed as text — and the
 * price carries the store's currency symbol.
 */
class LabelRenderer {

    fun render(product: Product, settings: LabelSettings = LabelSettings()): Bitmap {
        val width = BldPrintManager.WIDTH_PIXEL
        val contentWidth = width - PADDING * 2

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = NAME_TEXT_SIZE
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val codePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 26f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        // The SKU reads a little smaller than the EAN's digits: it is the counter's fallback way of
        // naming the product, not the number anyone keys into a till.
        val skuPaint = Paint(codePaint).apply { textSize = SKU_TEXT_SIZE }
        val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }
        // The MSRP is a reference price, so it sits below the price it is compared against.
        val msrpPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = MSRP_TEXT_SIZE
            textAlign = Paint.Align.CENTER
        }

        val nameLayout: StaticLayout? = product.name
            .takeIf { settings.showName && it.isNotBlank() }
            ?.let { buildName(it, namePaint, contentWidth) }
        // The barcode is the EAN and nothing else. A product the store has no EAN for prints no
        // barcode: a label whose barcode is really a SKU scans as a code the counter can't sell,
        // and it looks identical to a correct one, so the missing barcode is the safer failure.
        val ean = product.ean.trim()
        val codeBitmap: Bitmap? = ean
            .takeIf { settings.showBarcode && it.isNotBlank() }
            ?.let { encode(it, contentWidth, BARCODE_HEIGHT) }
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

        var height = PADDING
        nameLayout?.let { height += it.height + GAP }
        codeBitmap?.let { height += it.height + GAP }
        eanLine?.let { height += lineHeight(codePaint) + GAP }
        skuLine?.let { height += lineHeight(skuPaint) + GAP }
        priceLine?.let { height += lineHeight(pricePaint) + GAP }
        msrpLine?.let { height += lineHeight(msrpPaint) + GAP }
        height += PADDING
        height = height.coerceAtLeast(120)

        val bitmap = createBitmap(width, height)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        // Top to bottom: name, SKU, barcode, the barcode's digits, price, MSRP. The SKU sits with
        // the name as the other way of naming the product, leaving the barcode and its number
        // together, and the MSRP directly under the price it is there to be compared against.
        var y = PADDING.toFloat()
        nameLayout?.let { layout ->
            canvas.withTranslation(PADDING.toFloat(), y) {
                layout.draw(this)
            }
            y += layout.height + GAP
        }
        skuLine?.let { y += drawCenteredLine(canvas, it, skuPaint, width / 2f, y) + GAP }
        codeBitmap?.let { code ->
            val x = (width - code.width) / 2f
            canvas.drawBitmap(code, x, y, null)
            y += code.height + GAP
        }
        eanLine?.let { y += drawCenteredLine(canvas, it, codePaint, width / 2f, y) + GAP }
        priceLine?.let { y += drawCenteredLine(canvas, it, pricePaint, width / 2f, y) + GAP }
        msrpLine?.let { y += drawCenteredLine(canvas, it, msrpPaint, width / 2f, y) + GAP }
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
     * the barcode and price off the bottom of the label.
     */
    private fun buildName(text: String, paint: TextPaint, width: Int): StaticLayout {
        var size = NAME_TEXT_SIZE
        while (size >= NAME_MIN_TEXT_SIZE) {
            paint.textSize = size
            val layout = buildText(text, paint, width, maxLines = Int.MAX_VALUE, ellipsize = false)
            if (layout.lineCount <= NAME_MAX_LINES) return layout
            size -= NAME_TEXT_SIZE_STEP
        }
        paint.textSize = NAME_MIN_TEXT_SIZE
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

    private companion object {
        const val PADDING = 14
        const val GAP = 12
        const val BARCODE_HEIGHT = 72

        /** The name's full size, and how far it may shrink to fit [NAME_MAX_LINES]. */
        const val NAME_TEXT_SIZE = 23f
        const val NAME_MIN_TEXT_SIZE = 14f
        const val NAME_TEXT_SIZE_STEP = 1f
        const val NAME_MAX_LINES = 2

        const val SKU_TEXT_SIZE = 22f
        const val MSRP_TEXT_SIZE = 24f
    }
}
