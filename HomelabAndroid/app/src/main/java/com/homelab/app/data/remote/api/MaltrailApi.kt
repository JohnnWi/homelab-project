package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonElement
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface MaltrailApi {

    @GET("counts")
    suspend fun getCounts(
        @Header("X-Homelab-Service") service: String = "Maltrail",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @GET("events")
    suspend fun getEvents(
        @Header("X-Homelab-Service") service: String = "Maltrail",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("date") date: String
    ): JsonElement
}
