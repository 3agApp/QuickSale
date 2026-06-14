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
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter
import me.sourov.quicksale.data.local.Product
import me.sourov.quicksale.data.settings.LabelSettings
import me.sourov.quicksale.device.printer.BldPrintManager

/**
 * Renders a product label to a monochrome-friendly [Bitmap] sized to the printer width (384 px for
 * 58 mm). The same bitmap drives the on-screen preview and the thermal printer, so what you see is
 * what prints. Which fields appear (name, barcode, SKU text, price) comes from [LabelSettings].
 */
class LabelRenderer {

    fun render(product: Product, settings: LabelSettings = LabelSettings()): Bitmap {
        val width = BldPrintManager.WIDTH_PIXEL
        val contentWidth = width - PADDING * 2

        val namePaint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 27f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val skuPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 26f
            typeface = Typeface.MONOSPACE
            textAlign = Paint.Align.CENTER
        }
        val pricePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            textSize = 32f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.CENTER
        }

        val nameLayout: StaticLayout? = product.name
            .takeIf { settings.showName && it.isNotBlank() }
            ?.let { buildText(it, namePaint, contentWidth, maxLines = 2) }
        val codeBitmap: Bitmap? = product.sku
            .takeIf { settings.showBarcode && it.isNotBlank() }
            ?.let { encode(it, contentWidth, BARCODE_HEIGHT) }
        val skuLine = product.sku.takeIf { settings.showSku && it.isNotBlank() }
        val priceLine = product.price.trim().takeIf { settings.showPrice && it.isNotBlank() }

        var height = PADDING
        nameLayout?.let { height += it.height + GAP }
        codeBitmap?.let { height += it.height + GAP }
        skuLine?.let { height += lineHeight(skuPaint) + GAP }
        priceLine?.let { height += lineHeight(pricePaint) + GAP }
        height += PADDING
        height = height.coerceAtLeast(120)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap).apply { drawColor(Color.WHITE) }

        var y = PADDING.toFloat()
        nameLayout?.let { layout ->
            canvas.save()
            canvas.translate(PADDING.toFloat(), y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + GAP
        }
        codeBitmap?.let { code ->
            val x = (width - code.width) / 2f
            canvas.drawBitmap(code, x, y, null)
            y += code.height + GAP
        }
        skuLine?.let { y += drawCenteredLine(canvas, it, skuPaint, width / 2f, y) + GAP }
        priceLine?.let { y += drawCenteredLine(canvas, it, pricePaint, width / 2f, y) + GAP }
        return bitmap
    }

    private fun buildText(text: String, paint: TextPaint, width: Int, maxLines: Int): StaticLayout =
        StaticLayout.Builder.obtain(text, 0, text.length, paint, width)
            .setAlignment(Layout.Alignment.ALIGN_CENTER)
            .setMaxLines(maxLines)
            .setEllipsize(TextUtils.TruncateAt.END)
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

    private fun encode(content: String, w: Int, h: Int): Bitmap? = runCatching {
        val hints = mapOf(EncodeHintType.MARGIN to 0)
        val matrix = MultiFormatWriter().encode(content, BarcodeFormat.CODE_128, w, h, hints)
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
    }
}
