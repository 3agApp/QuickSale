package me.sourov.quicksale.device.printer

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [PrinterDriver] for the S60B's built-in 58 mm thermal printer, driven through [BldPrintManager].
 *
 * The media mode and the print density are the caller's to choose, and both are set on every job
 * rather than left to the device's own `print_blacklabel` and `print_density` settings — those are
 * device-wide and persist across apps, so a job that did not say otherwise would silently print
 * unpositioned on die-cut stock, or burn at whatever darkness the terminal happened to be left at.
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
        density: Int,
    ): PrintResult =
        withContext(Dispatchers.IO) {
            val p = ensurePrinter() ?: return@withContext PrintResult.Error("Printer unavailable")
            try {
                synchronized(lock) {
                    p.reset()
                    // Both of these go after reset(), not before: whatever reset() restores, it
                    // must not be left holding the last word on how this job prints.
                    p.setDensity(
                        density.coerceIn(BldPrintManager.MIN_DENSITY, BldPrintManager.MAX_DENSITY)
                    )
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
                // through. The state code is asked for first because it names the fault directly,
                // where the thrown message only sometimes does.
                val message = t.message.orEmpty()
                val state = runCatching { ensurePrinter()?.state() }.getOrNull()
                // A printer that names no fault — because the call failed, or because it reports
                // none — leaves the thrown message as the only evidence there is.
                val staysSilent = state == null || state == BldPrintManager.ERROR_NONE
                val blackMarkFault = state == BldPrintManager.ERROR_BLACK_MARK ||
                    (staysSilent && message.contains("black", ignoreCase = true))
                if (blackMark && blackMarkFault) {
                    PrintResult.Error(
                        "Couldn't find the next label — check the roll is die-cut, or switch " +
                            "Label printing to Continuous roll"
                    )
                } else {
                    PrintResult.Error(message.ifBlank { "Print failed" } + state.asStateSuffix())
                }
            }
        }

    /**
     * The printer's own account of what went wrong, in words a person at a till can act on.
     *
     * Anything not named here keeps its number rather than being guessed at, and a printer that
     * reports no fault at all adds nothing — the thrown message is already the better answer in
     * that case.
     */
    private fun Int?.asStateSuffix(): String = when (this) {
        null, BldPrintManager.ERROR_NONE -> ""
        BldPrintManager.ERROR_BUSY -> " (printer busy)"
        BldPrintManager.ERROR_HOT -> " (print head too hot — let it cool)"
        BldPrintManager.ERROR_NO_PAPER -> " (out of paper)"
        BldPrintManager.ERROR_NO_BATTERY -> " (battery too low to print)"
        BldPrintManager.ERROR_FEED -> " (paper feed error)"
        BldPrintManager.ERROR_PRINT -> " (printer reported a print error)"
        BldPrintManager.ERROR_BLACK_MARK -> " (no black mark found)"
        BldPrintManager.ERROR_NOT_OPEN -> " (printer not open)"
        BldPrintManager.ERROR_DENSITY_INVALID -> " (print darkness out of range)"
        BldPrintManager.ERROR_BITMAP_TOO_WIDE -> " (label wider than the printer)"
        BldPrintManager.ERROR_TIMEOUT -> " (printer timed out)"
        else -> " (printer state $this)"
    }
}
