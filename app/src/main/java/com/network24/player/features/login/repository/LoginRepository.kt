package com.network24.player.features.login.repository

import com.network24.player.core.api.ApiClient
import com.network24.player.common.models.LoginResponse
import retrofit2.Response

class LoginRepository {

    suspend fun login(
        server: String,
        username: String,
        password: String
    ): Response<LoginResponse> {

        val baseUrl = server.trim().trimEnd('/') + "/"

        return ApiClient.get(baseUrl)
            .login(username, password)
    }
}