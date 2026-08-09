package com.network24.player.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "continue_watching",
    indices = [
        Index(value = ["updatedAtMs"]),
        Index(value = ["itemType", "itemId"], unique = true)
    ]
)
data class ContinueWatchingEntity(
    @PrimaryKey val key: String,
    val itemType: String,
    val itemId: String,

    val positionMs: Long,
    val durationMs: Long? = null,

    val updatedAtMs: Long
)
