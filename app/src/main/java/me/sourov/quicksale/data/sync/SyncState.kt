package me.sourov.quicksale.data.sync

sealed interface SyncState {
    data object Idle : SyncState
    data class Running(val message: String, val fraction: Float) : SyncState
    data class Success(val productCount: Int, val customerCount: Int, val timeMillis: Long) : SyncState
    data class Error(val message: String) : SyncState
}
