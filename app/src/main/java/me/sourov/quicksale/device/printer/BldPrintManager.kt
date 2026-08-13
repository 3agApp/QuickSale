package me.sourov.quicksale.device.printer

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import java.lang.reflect.Method

/**
 * Reflection bridge to the S60B firmware's built-in printer (`android.bld.PrintManager`) — the same
 * system API the device's bundled `lc_print_sdk` wraps. Reflection lets us drive the printer without
 * redistributing the vendor SDK, and degrade gracefully on devices that lack it.
 *
 * Print flow mirrors the vendor demo: open() once, then per copy reset/addImage/start.
 */
@SuppressLint("PrivateApi")
class BldPrintManager private constructor(
    private val instance: Any,
    private val cls: Class<*>,
) {
    private val intType = Int::class.javaPrimitiveType ?: Int::class.java
    private val boolType = Boolean::class.javaPrimitiveType ?: Boolean::class.java

    private fun method(name: String, vararg types: Class<*>): Method = cls.getMethod(name, *types)

    fun reset() = method("reset").invoke(instance)
    fun start() = method("start").invoke(instance)
    fun close() = runCatching { method("close").invoke(instance) }
    fun setBlackLabel(on: Boolean) = method("setBlackLabel", boolType).invoke(instance, on)
    fun setDensity(value: Int) = method("setDensity", intType).invoke(instance, value)
    fun setFeedPaperSpace(value: Int) = method("setFeedPaperSpace", intType).invoke(instance, value)
    fun addImage(align: Int, bitmap: Bitmap) =
        method("addImage", intType, Bitmap::class.java).invoke(instance, align, bitmap)
    fun addLineFeed(lines: Int) = method("addLineFeed", intType).invoke(instance, lines)
    fun version(): String = runCatching { method("getPrinterVer").invoke(instance) as? String }
        .getOrNull().orEmpty()

    /**
     * What the printer says is wrong, as one of the firmware's `IErrorCode` values, or null when
     * the call itself fails. It is the one thing that separates "out of paper" from "couldn't find
     * the label" after a failed job.
     *
     * [check] chooses which part of the printer to ask about, from the firmware's
     * `PrintConfig$StateType` — the parts can be asked after individually (busy, temperature,
     * paper, feed, print, black mark), but [CHECK_ALL] reports the first fault it finds, which is
     * what an error path wants rather than a guess to confirm.
     */
    fun state(check: Int = CHECK_ALL): Int? =
        runCatching { method("getPrinterState", intType).invoke(instance, check) as? Int }
            .getOrNull()

    companion object {
        const val WIDTH_PIXEL = 384
        const val ALIGN_CENTER = 2

        /**
         * The bounds [setDensity] accepts, read off the firmware's own `PrintConfig$Density`
         * constants on the device — `TOP_GRAY_SMALL` = 1 through `TOP_GRAY_LARGEST` = 11. (The
         * copy of that class bundled in the vendor's demo app stops at 10; the one on the boot
         * classpath is the one the printer actually answers to.) Out-of-range values are not worth
         * finding out about through reflection, so callers are clamped to these.
         */
        const val MIN_DENSITY = 1
        const val MAX_DENSITY = 11

        /** `PrintConfig$StateType.CHECK_ALL` — see [state]. */
        const val CHECK_ALL = 1

        /**
         * The `IErrorCode` values [state] answers with. Only the ones worth saying out loud are
         * named here; the rest keep their number, because a wrong guess at what a code means is
         * worse to read on a till than a number the vendor can look up.
         */
        const val ERROR_NONE = 0
        const val ERROR_BUSY = 1
        const val ERROR_HOT = 2
        const val ERROR_NO_PAPER = 3
        const val ERROR_NO_BATTERY = 4
        const val ERROR_FEED = 5
        const val ERROR_PRINT = 6
        const val ERROR_BLACK_MARK = 7
        const val ERROR_NOT_OPEN = 16
        const val ERROR_DENSITY_INVALID = 20
        const val ERROR_BITMAP_TOO_WIDE = 164
        const val ERROR_TIMEOUT = 169

        private const val CLASS = "android.bld.PrintManager"
        private const val TAG = "BldPrintManager"

        /** True only on hardware whose firmware exposes the printer (S60B). */
        fun isSupported(): Boolean = try {
            Class.forName(CLASS)
            supportProperty() > 0
        } catch (t: Throwable) {
            false
        }

        private fun supportProperty(): Int = try {
            val sp = Class.forName("android.os.SystemProperties")
            sp.getMethod("getInt", String::class.java, Int::class.javaPrimitiveType)
                .invoke(null, "ro.blovedream_support_print", 0) as Int
        } catch (t: Throwable) {
            // Class exists but property unreadable — assume usable.
            1
        }

        fun create(context: Context): BldPrintManager? = try {
            val cls = Class.forName(CLASS)
            val instance = cls.getMethod("getDefaultInstance", Context::class.java)
                .invoke(null, context.applicationContext) ?: return null
            cls.getMethod("open").invoke(instance)
            BldPrintManager(instance, cls)
        } catch (t: Throwable) {
            Log.w(TAG, "Printer init failed", t)
            null
        }
    }
}
