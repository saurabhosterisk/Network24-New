package com.network24.player.core.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "sync_meta")
data class SyncMetaEntity(
    @PrimaryKey val key: String, // e.g. "live_categories", "live_channels", "epg_{streamId}"
    val lastSyncEpochMs: Long,
)
