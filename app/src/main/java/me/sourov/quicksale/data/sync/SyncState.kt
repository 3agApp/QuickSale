package me.sourov.quicksale.data.sync

/** What a sync run targets. Drives progress labels and which Home button reflects the result. */
enum class SyncTarget(val label: String) {
    Products("products"),
    Customers("customers"),
}

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val target: SyncTarget, val message: String, val fraction: Float) : SyncState
    data class Success(val target: SyncTarget, val count: Int, val timeMillis: Long) : SyncState
    data class Error(val target: SyncTarget, val message: String) : SyncState
}
