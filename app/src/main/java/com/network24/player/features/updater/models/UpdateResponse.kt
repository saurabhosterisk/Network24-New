package com.network24.player.features.updater.models

data class UpdateResponse(
    val versionCode: Int,
    val apk: String
)