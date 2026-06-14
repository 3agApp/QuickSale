package me.sourov.quicksale.device.printer

import android.graphics.Bitmap

/** Used on devices without a built-in printer; [isAvailable] stays false so the UI disables printing. */
class NoPrinterDriver : PrinterDriver {
    override val isAvailable: Boolean = false
    override fun version(): String? = null
    override suspend fun printBitmap(bitmap: Bitmap, copies: Int, feedLines: Int): PrintResult =
        PrintResult.Error("This device has no printer")
}
