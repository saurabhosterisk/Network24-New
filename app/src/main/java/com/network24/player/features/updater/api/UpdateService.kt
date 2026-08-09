package com.network24.player.features.updater.api

import com.network24.player.features.updater.models.UpdateResponse
import retrofit2.Call
import retrofit2.http.GET

interface UpdateService {

    @GET("app/update.json")
    fun checkUpdate(): Call<UpdateResponse>

}