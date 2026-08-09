package com.network24.player.common.models

data class ServerInfo(

    val url: String?,
    val port: String?,
    val https_port: String?,
    val server_protocol: String?,
    val rtmp_port: String?,
    val timezone: String?,
    val timestamp_now: Long?,
    val time_now: String?

)