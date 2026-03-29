package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header

interface JellyfinApi {

    @GET("System/Info")
    suspend fun getSystemInfo(
        @Header("X-Homelab-Service") service: String = "Jellyfin",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("Sessions")
    suspend fun getSessions(
        @Header("X-Homelab-Service") service: String = "Jellyfin",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): kotlinx.serialization.json.JsonArray

    @GET("Items")
    suspend fun getItems(
        @Header("X-Homelab-Service") service: String = "Jellyfin",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject
}
