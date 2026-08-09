package com.network24.player.core.cache.memory

object CacheTtl {
    // TTLs can be tuned later
    const val CATEGORIES_MS: Long = 10 * 60 * 1000L   // 10 min
    const val CHANNELS_MS: Long = 10 * 60 * 1000L     // 10 min
    const val EPG_MS: Long = 2 * 60 * 1000L           // 2 min

    // For user-specific local lists
    const val FAVORITES_MS: Long = 30 * 1000L         // 30 sec
    const val HISTORY_MS: Long = 30 * 1000L
    const val CONTINUE_WATCHING_MS: Long = 30 * 1000L
}
