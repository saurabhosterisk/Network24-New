package com.network24.player.core.sync

object SyncKeys {
    const val LIVE_CATEGORIES = "live_categories"
    const val LIVE_CHANNELS_ALL = "live_channels_all"
    const val FULL_EPG = "full_epg"

    fun epgKey(streamId: Int): String = "epg_$streamId"
}
