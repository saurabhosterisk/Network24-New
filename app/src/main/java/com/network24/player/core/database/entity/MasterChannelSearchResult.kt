package com.network24.player.core.database.entity

import com.network24.player.features.live.models.LiveChannel

/** Projection returned by the global live-channel search query. */
data class MasterChannelSearchResult(
    val streamId: Int,
    val channelName: String?,
    val categoryId: String?,
    val categoryName: String?,
    val icon: String?,
    val streamType: String?,
    val epgChannelId: String?,
    val tvArchive: Int?,
    val tvArchiveDuration: Int?,
    val directSource: String?,
    val num: Int?,
    val added: String?,
    val customSid: String?,
    val isFavorite: Boolean
) {
    fun toLiveChannel(): LiveChannel = LiveChannel(
        num = num,
        name = channelName,
        stream_type = streamType,
        stream_id = streamId,
        stream_icon = icon,
        epg_channel_id = epgChannelId,
        added = added,
        category_id = categoryId,
        custom_sid = customSid,
        tv_archive = tvArchive,
        tv_archive_duration = tvArchiveDuration,
        direct_source = directSource
    )
}
