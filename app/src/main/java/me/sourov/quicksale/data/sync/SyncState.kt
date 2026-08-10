package me.sourov.quicksale.data.sync

/** What a sync run targets. Each target syncs independently and has its own [SyncState] flow. */
enum class SyncTarget(val label: String, val unit: String) {
    /** The catalog, plus the store currency and checkout configuration that ride along with it. */
    Products("Products", "products"),

    /** The organization snapshot — organizations, members and locations — plus the address forms. */
    Organizations("Organizations", "organizations"),
}

sealed interface SyncState {
    data object Idle : SyncState

    data class Running(val message: String, val fraction: Float) : SyncState

    /**
     * A finished run. [count] is how many records the app now holds; [unchanged] is true when the
     * store's ETags all matched, so nothing needed rewriting.
     */
    data class Success(
        val count: Int,
        val timeMillis: Long,
        val unchanged: Boolean = false,
    ) : SyncState

    data class Error(val message: String) : SyncState

    val isRunning: Boolean get() = this is Running
}
