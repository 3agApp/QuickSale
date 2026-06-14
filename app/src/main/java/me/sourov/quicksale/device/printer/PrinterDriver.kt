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
     * Prints [copies] of [bitmap] (already sized to the printer width), feeding [feedLines] blank
     * lines after each label. Safe to call off the UI.
     */
    suspend fun printBitmap(bitmap: Bitmap, copies: Int = 1, feedLines: Int = 3): PrintResult
}
