package me.sourov.quicksale.device.printer

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PrinterDriver] for the S60B's built-in 58 mm thermal printer, driven through [BldPrintManager].
 * Uses continuous-paper mode (black-mark off) since the configured media is 58 mm continuous.
 */
class LcPrintDriver(private val context: Context) : PrinterDriver {

    private val lock = Any()

    @Volatile
    private var printer: BldPrintManager? = null

    override val isAvailable: Boolean get() = BldPrintManager.isSupported()

    private fun ensurePrinter(): BldPrintManager? {
        printer?.let { return it }
        synchronized(lock) {
            if (printer == null) printer = BldPrintManager.create(context)
            return printer
        }
    }

    override fun version(): String? = runCatching { ensurePrinter()?.version() }.getOrNull()

    override suspend fun printBitmap(bitmap: Bitmap, copies: Int, feedLines: Int): PrintResult =
        withContext(Dispatchers.IO) {
            val p = ensurePrinter() ?: return@withContext PrintResult.Error("Printer unavailable")
            try {
                synchronized(lock) {
                    p.reset()
                    p.setBlackLabel(false) // continuous paper, no gap detection
                    repeat(copies.coerceIn(1, 9)) {
                        p.addImage(BldPrintManager.ALIGN_CENTER, bitmap)
                        p.addLineFeed(feedLines.coerceIn(0, 12))
                        p.start()
                    }
                }
                PrintResult.Success
            } catch (t: Throwable) {
                PrintResult.Error(t.message ?: "Print failed")
            }
        }
}
