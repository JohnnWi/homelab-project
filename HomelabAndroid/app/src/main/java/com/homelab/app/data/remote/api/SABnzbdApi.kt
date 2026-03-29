package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface SABnzbdApi {

    @GET("api")
    suspend fun getQueue(
        @Header("X-Homelab-Service") service: String = "SABnzbd",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("mode") mode: String = "queue",
        @Query("output") output: String = "json"
    ): JsonObject

    @GET("api")
    suspend fun getHistory(
        @Header("X-Homelab-Service") service: String = "SABnzbd",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("mode") mode: String = "history",
        @Query("output") output: String = "json",
        @Query("limit") limit: Int = 30
    ): JsonObject
}
