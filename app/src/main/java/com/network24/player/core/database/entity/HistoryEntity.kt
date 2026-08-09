package com.network24.player.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "history",
    indices = [
        Index(value = ["updatedAtMs"]),
        Index(value = ["itemType", "itemId"], unique = true)
    ]
)
data class HistoryEntity(
    @PrimaryKey val key: String,          // "MOVIE:99"
    val itemType: String,
    val itemId: String,

    val lastPositionMs: Long? = null,
    val durationMs: Long? = null,

    val updatedAtMs: Long
)
