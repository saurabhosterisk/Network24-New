package com.network24.player.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "epg",
    indices = [
        // old indexes (kept)
        Index(value = ["streamId"]),
        Index(value = ["startTimestamp"]),

        // new indexes for full EPG lookup
        Index(value = ["epgChannelId"]),
        Index(value = ["epgChannelId", "startTimestamp"])
    ]
)
data class EpgEntity(
    @PrimaryKey val id: String,

    // old key (kept)
    val streamId: Int,

    // NEW: join key for XMLTV / full EPG (nullable for old short-EPG rows if needed)
    val epgChannelId: String? = null,

    // old keys (kept)
    val title: String? = null,
    val description: String? = null,
    val start: String? = null,
    val end: String? = null,
    val startTimestamp: Long? = null,
    val stopTimestamp: Long? = null,
)
