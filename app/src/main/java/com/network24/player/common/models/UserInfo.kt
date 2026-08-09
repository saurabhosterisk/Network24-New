package com.network24.player.common.models

data class UserInfo(

    val username: String?,
    val password: String?,
    val message: String?,
    val auth: Int?,
    val status: String?,
    val exp_date: String?,
    val is_trial: String?,
    val active_cons: String?,
    val created_at: String?,
    val max_connections: String?,
    val allowed_output_formats: List<String>?

)