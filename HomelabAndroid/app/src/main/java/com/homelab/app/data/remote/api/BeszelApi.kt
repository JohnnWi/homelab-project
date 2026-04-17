package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.beszel.BeszelAuthResponse
import com.homelab.app.data.remote.dto.beszel.BeszelRecordsResponse
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetailsResponse
import com.homelab.app.data.remote.dto.beszel.BeszelSystemsResponse
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevicesResponse
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import com.homelab.app.data.remote.dto.beszel.BeszelContainerRecordsResponse
import com.homelab.app.data.remote.dto.beszel.BeszelContainerStatsResponse
import okhttp3.ResponseBody
import retrofit2.http.*

interface BeszelApi {

    @POST
    suspend fun authenticate(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false",
        @Body credentials: Map<String, String>
    ): BeszelAuthResponse

    @GET("api/collections/systems/records")
    suspend fun getSystems(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Query("sort") sort: String = "-updated",
        @Query("perPage") perPage: Int = 50,
        // Only fetch fields needed for the dashboard: identity + CPU/memory + disk (root + extra filesystems).
        @Query("fields") fields: String = "id,name,host,status,info.cpu,info.mp,info.dp,info.efs",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): BeszelSystemsResponse

    @GET("api/collections/systems/records/{id}")
    suspend fun getSystem(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String
    ): BeszelSystem

    @GET("api/collections/system_stats/records?sort=-created")
    suspend fun getSystemRecords(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("filter", encoded = true) filter: String,
        @Query("perPage") limit: Int = 30
    ): BeszelRecordsResponse

    @GET("api/collections/system_details/records")
    suspend fun getSystemDetails(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("filter", encoded = true) filter: String,
        @Query("perPage") limit: Int = 1
    ): BeszelSystemDetailsResponse

    @GET("api/collections/smart_devices/records")
    suspend fun getSmartDevices(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("filter", encoded = true) filter: String,
        @Query("perPage") limit: Int = 10
    ): BeszelSmartDevicesResponse

    @GET("api/collections/containers/records")
    suspend fun getContainers(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("filter", encoded = true) filter: String,
        @Query("perPage") limit: Int = 200
    ): BeszelContainerRecordsResponse

    @GET("api/collections/container_stats/records?sort=-created")
    suspend fun getContainerStats(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("filter", encoded = true) filter: String,
        @Query("perPage") limit: Int = 240
    ): BeszelContainerStatsResponse

    @GET("api/beszel/containers/logs")
    suspend fun getContainerLogs(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("Authorization") authorization: String,
        @Query("system") systemId: String,
        @Query("container") containerId: String
    ): ResponseBody

    @GET("api/beszel/containers/info")
    suspend fun getContainerInfo(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Header("Authorization") authorization: String,
        @Query("system") systemId: String,
        @Query("container") containerId: String
    ): ResponseBody
}
