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

    companion object {
        const val WIDTH_PIXEL = 384
        const val ALIGN_CENTER = 2

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
