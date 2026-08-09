package com.network24.player.core.cache.memory

object CacheKeys {
    const val LIVE_CATEGORIES = "live_categories"
    const val LIVE_CHANNELS_ALL = "live_channels_all"

    fun liveChannels(categoryId: String): String = "live_channels_$categoryId"
    fun epg(streamId: Int): String = "epg_$streamId"

    const val FAVORITES = "favorites"
    const val HISTORY = "history"
    const val CONTINUE_WATCHING = "continue_watching"
}
