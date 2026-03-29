package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header

interface ImmichApi {

    @GET("api/server/info")
    suspend fun getServerInfo(
        @Header("X-Homelab-Service") service: String = "Immich",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/server/statistics")
    suspend fun getServerStatistics(
        @Header("X-Homelab-Service") service: String = "Immich",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject
}
