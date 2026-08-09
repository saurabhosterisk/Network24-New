package com.network24.player.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "favorites",
    indices = [
        Index(value = ["itemType", "itemId"], unique = true),
        Index(value = ["createdAtMs"])
    ]
)
data class FavoriteEntity(
    @PrimaryKey val key: String,          // e.g. "LIVE_CHANNEL:123"
    val itemType: String,                 // LIVE_CHANNEL / MOVIE / SERIES / EPISODE
    val itemId: String,                   // store as String
    val createdAtMs: Long
)
