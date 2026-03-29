package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface TdarrApi {

    @POST("api/v2/get-nodes")
    suspend fun getNodes(
        @Header("X-Homelab-Service") service: String = "Tdarr",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: JsonObject = JsonObject(emptyMap())
    ): JsonObject

    @POST("api/v2/get-stats")
    suspend fun getStats(
        @Header("X-Homelab-Service") service: String = "Tdarr",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: JsonObject = JsonObject(emptyMap())
    ): JsonObject
}
