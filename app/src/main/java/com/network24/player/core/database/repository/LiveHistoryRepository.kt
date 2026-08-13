package com.network24.player.core.database.repository

import android.content.Context
import com.network24.player.core.database.DatabaseProvider
import com.network24.player.core.database.entity.HistoryEntity
import com.network24.player.core.database.mapper.toLiveChannel
import com.network24.player.core.preferences.PreferenceManager
import com.network24.player.features.live.models.LiveChannel

class LiveHistoryRepository(context: Context) {

    private val appContext = context.applicationContext
    private val db = DatabaseProvider.get(appContext)
    private val prefs = PreferenceManager(appContext)

    suspend fun record(channel: LiveChannel) {
        val streamId = channel.stream_id ?: return
        val itemType = liveItemType() ?: return

        db.historyDao().upsert(
            HistoryEntity(
                key = "$itemType:$streamId",
                itemType = itemType,
                itemId = streamId.toString(),
                updatedAtMs = System.currentTimeMillis()
            )
        )
        db.historyDao().trimToRecent(itemType, MAX_RECENT_CHANNELS)
    }

    suspend fun getRecentlyWatched(): List<LiveChannel> {
        val itemType = liveItemType() ?: return emptyList()
        val history = db.historyDao().getRecentByType(
            itemType = itemType,
            limit = MAX_RECENT_CHANNELS
        )
        val streamIds = history.mapNotNull { it.itemId.toIntOrNull() }
        if (streamIds.isEmpty()) return emptyList()

        val channelsById = db.channelDao()
            .getByStreamIds(streamIds)
            .associateBy { it.streamId }

        return streamIds.mapNotNull { streamId ->
            channelsById[streamId]?.toLiveChannel()
        }
    }

    private fun liveItemType(): String? {
        val username = prefs.getUsername().trim()
        return username.takeIf { it.isNotEmpty() }?.let { "LIVE:$it" }
    }

    private companion object {
        const val MAX_RECENT_CHANNELS = 50
    }
}
