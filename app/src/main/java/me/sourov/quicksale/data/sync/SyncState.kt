package me.sourov.quicksale.data.sync

/** What a sync run targets. Each target syncs independently and has its own [SyncState] flow. */
enum class SyncTarget(val label: String) {
    Products("products"),
    Customers("customers"),
}

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val message: String, val fraction: Float) : SyncState
    data class Success(val count: Int, val timeMillis: Long) : SyncState
    data class Error(val message: String) : SyncState
}
