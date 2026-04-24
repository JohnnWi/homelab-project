package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonElement
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface DockmonApi {

    @GET("api/hosts")
    suspend fun getHosts(
        @Header("X-Homelab-Service") service: String = "DockMon",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @GET("api/containers")
    suspend fun getContainers(
        @Header("X-Homelab-Service") service: String = "DockMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("host_id") hostId: String? = null
    ): JsonElement

    @GET("api/containers/{id}/logs")
    suspend fun getContainerLogs(
        @Path("id", encoded = true) containerId: String,
        @Header("X-Homelab-Service") service: String = "DockMon",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("tail") tail: Int = 200
    ): ResponseBody

    @POST("api/containers/{id}/restart")
    suspend fun restartContainer(
        @Path("id", encoded = true) containerId: String,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Homelab-Service") service: String = "DockMon",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): Response<JsonElement>

    @POST("api/containers/{id}/update")
    suspend fun updateContainer(
        @Path("id", encoded = true) containerId: String,
        @Body body: Map<String, String>,
        @Header("Accept") accept: String = "application/json",
        @Header("X-Homelab-Service") service: String = "DockMon",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): Response<JsonElement>
}
