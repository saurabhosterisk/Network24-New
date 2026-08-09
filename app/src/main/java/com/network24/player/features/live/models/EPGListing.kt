package com.network24.player.features.live.models

data class EPGListing(

    val id: String?,

    val title: String?,

    val description: String?,

    val start: String?,

    val end: String?,

    val start_timestamp: Long?,

    val stop_timestamp: Long?

)