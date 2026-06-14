package me.sourov.quicksale.data.scanner

/** How the handheld scanner delivers decoded data to the app. */
enum class ScannerMode { KEYBOARD, BROADCAST }

/**
 * Known scanner brands and their default broadcast intent action + data extra key.
 * These are starting points — the values are editable, and the on-screen diagnostic
 * shows what a device actually sends so they can be corrected per device.
 */
enum class ScannerPreset(
    val id: String,
    val displayName: String,
    val action: String,
    val extraKey: String,
) {
    AUTO_DETECT("auto", "Auto-detect (try common)", "", ""),
    SUNMI("sunmi", "Sunmi", "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED", "data"),
    NEWLAND("newland", "Newland", "nlscan.action.SCANNER_RESULT", "SCAN_BARCODE1"),
    UROVO_IDATA("urovo", "Urovo / iData", "android.intent.ACTION_DECODE_DATA", "barcode_string"),
    HONEYWELL("honeywell", "Honeywell", "com.honeywell.decode.intent.action.SCAN_RESULT", "data"),
    ZEBRA("zebra", "Zebra DataWedge", "com.symbol.datawedge.intent.action.SCAN", "com.symbol.datawedge.data_string"),
    CUSTOM("custom", "Custom", "", "");

    companion object {
        fun fromId(id: String): ScannerPreset = entries.find { it.id == id } ?: AUTO_DETECT

        /** Actions registered in Auto-detect mode to cover the most common handhelds. */
        val KNOWN_ACTIONS = listOf(
            "com.sunmi.scanner.ACTION_DATA_CODE_RECEIVED",
            "nlscan.action.SCANNER_RESULT",
            "android.intent.ACTION_DECODE_DATA",
            "com.honeywell.decode.intent.action.SCAN_RESULT",
            "com.symbol.datawedge.intent.action.SCAN",
            "com.android.server.scannerservice.broadcast",
            "scan.rcv.message",
            "barcode.broadcast.action",
            "com.scanner.broadcast",
            "com.xcheng.scanner.action.BARCODE_DECODING_BROADCAST",
            "com.rfid.SCAN",
        )
    }
}

data class ScannerConfig(
    val mode: ScannerMode = ScannerMode.KEYBOARD,
    val presetId: String = ScannerPreset.AUTO_DETECT.id,
    val action: String = "",
    val extraKey: String = "",
) {
    /** The intent actions to register a receiver for, given this config. */
    val registrableActions: List<String>
        get() = when {
            mode != ScannerMode.BROADCAST -> emptyList()
            presetId == ScannerPreset.AUTO_DETECT.id -> ScannerPreset.KNOWN_ACTIONS
            action.isNotBlank() -> listOf(action.trim())
            else -> emptyList()
        }
}
