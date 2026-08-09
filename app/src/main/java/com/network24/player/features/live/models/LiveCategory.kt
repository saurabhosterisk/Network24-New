package com.network24.player.features.live.models

data class LiveCategory(
    val category_id: String,
    val category_name: String,
    val parent_id: Int?
)