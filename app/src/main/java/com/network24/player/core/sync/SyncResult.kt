package com.network24.player.core.sync

sealed class SyncResult {
    data object Success : SyncResult()
    data class Error(val message: String, val throwable: Throwable? = null) : SyncResult()
}
