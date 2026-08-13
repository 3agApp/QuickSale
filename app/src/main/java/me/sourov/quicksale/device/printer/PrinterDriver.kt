package me.sourov.quicksale.device.printer

import android.graphics.Bitmap

sealed interface PrintResult {
    data object Success : PrintResult
    data class Error(val message: String) : PrintResult
}

/** Abstraction over the device printer so the UI is identical across devices with/without one. */
interface PrinterDriver {
    /** Whether this device can print. */
    val isAvailable: Boolean

    /** Firmware/printer version, if known. */
    fun version(): String?

    /**
     * Prints [copies] of [bitmap] (already sized to the printer width). Safe to call off the UI.
     *
     * [blackMark] selects how the next label is reached: true seeks the black mark on die-cut
     * stock, so the printer positions each label itself and [feedLines] is ignored; false feeds
     * [feedLines] blank lines instead, which is all a continuous roll allows.
     *
     * [density] is how hard the head burns, on the printer's 1–11 scale. It has no safe default to
     * fall back to — the device's stored value is exactly what this exists to stop deciding the
     * outcome — so every caller states it.
     */
    suspend fun printBitmap(
        bitmap: Bitmap,
        copies: Int = 1,
        feedLines: Int = 3,
        blackMark: Boolean = true,
        density: Int,
    ): PrintResult
}
