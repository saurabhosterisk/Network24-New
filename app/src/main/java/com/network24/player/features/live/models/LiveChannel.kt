package com.network24.player.features.live.models

data class LiveChannel(

    val num: Int?,

    val name: String?,

    val stream_type: String?,

    val stream_id: Int?,

    val stream_icon: String?,

    val epg_channel_id: String?,

    val added: String?,

    val category_id: String?,

    val custom_sid: String?,

    val tv_archive: Int?,

    val tv_archive_duration: Int?,

    val direct_source: String?

)