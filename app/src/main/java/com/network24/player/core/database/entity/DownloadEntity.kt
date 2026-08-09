package com.network24.player.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "downloads",
    indices = [
        Index(value = ["status"]),
        Index(value = ["updatedAtMs"]),
        Index(value = ["itemType", "itemId"], unique = true)
    ]
)
data class DownloadEntity(
    @PrimaryKey val key: String,
    val itemType: String,
    val itemId: String,

    val localPath: String,
    val status: String,           // QUEUED/DOWNLOADING/DONE/FAILED
    val progressPct: Int = 0,

    val createdAtMs: Long,
    val updatedAtMs: Long
)
