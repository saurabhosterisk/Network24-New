package com.network24.player.core.database.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "categories",
    indices = [
        Index(value = ["type"]),
        Index(value = ["type", "parentId"])
    ]
)
data class CategoryEntity(
    @PrimaryKey val categoryId: String,
    val position: Int,
    val name: String,
    val parentId: Int? = null,

    // LIVE / MOVIE / SERIES
    val type: String
)

object CategoryType {
    const val LIVE = "LIVE"
    const val MOVIE = "MOVIE"
    const val SERIES = "SERIES"
}
