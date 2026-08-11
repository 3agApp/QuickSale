package me.sourov.quicksale.device.printer

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PrinterDriver] for the S60B's built-in 58 mm thermal printer, driven through [BldPrintManager].
 *
 * The media mode is the caller's to choose, and it is set on every job rather than left to the
 * device's own `print_blacklabel` setting — the firmware default is continuous, so a job that did
 * not say otherwise would silently print unpositioned on die-cut stock.
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

    override suspend fun printBitmap(
        bitmap: Bitmap,
        copies: Int,
        feedLines: Int,
        blackMark: Boolean,
    ): PrintResult =
        withContext(Dispatchers.IO) {
            val p = ensurePrinter() ?: return@withContext PrintResult.Error("Printer unavailable")
            try {
                synchronized(lock) {
                    p.reset()
                    p.setBlackLabel(blackMark)
                    // On die-cut stock the printer advances to the next mark by itself; feeding on
                    // top of that would skip a label per print.
                    val feed = if (blackMark) 0 else feedLines.coerceIn(0, 12)
                    repeat(copies.coerceIn(1, 9)) {
                        p.addImage(BldPrintManager.ALIGN_CENTER, bitmap)
                        if (feed > 0) p.addLineFeed(feed)
                        p.start()
                    }
                }
                PrintResult.Success
            } catch (t: Throwable) {
                // The firmware reports a roll it can't position on as a black-mark detection
                // failure; say what to do about it rather than passing the vendor's phrasing
                // through. The state code goes along for the ride because it is the only thing
                // that separates "no label sensed" from "out of paper" after the fact.
                val message = t.message.orEmpty()
                val state = runCatching { ensurePrinter()?.state() }.getOrNull()
                if (blackMark && message.contains("black", ignoreCase = true)) {
                    PrintResult.Error(
                        "Couldn't find the next label — check the roll is die-cut, or switch " +
                            "Label printing to Continuous roll" + state.asStateSuffix()
                    )
                } else {
                    PrintResult.Error(message.ifBlank { "Print failed" } + state.asStateSuffix())
                }
            }
        }

    private fun Int?.asStateSuffix(): String = this?.let { " (printer state $it)" }.orEmpty()
}
