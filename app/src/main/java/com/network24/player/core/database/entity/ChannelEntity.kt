package com.network24.player.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "channels",
    indices = [
        Index(value = ["categoryId"]),
        Index(value = ["epgChannelId"]),
        Index(value = ["name"])
    ]
)
data class ChannelEntity(
    @PrimaryKey val streamId: Int,

    val name: String? = null,
    val categoryId: String? = null,
    val icon: String? = null,

    val streamType: String? = null,
    val epgChannelId: String? = null,

    val tvArchive: Int? = null,
    val tvArchiveDuration: Int? = null,
    val directSource: String? = null,

    val num: Int? = null,
    val added: String? = null,
    val customSid: String? = null,
)
