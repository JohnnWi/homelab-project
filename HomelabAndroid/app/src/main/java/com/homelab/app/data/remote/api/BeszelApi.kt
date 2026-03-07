package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.beszel.BeszelAuthResponse
import com.homelab.app.data.remote.dto.beszel.BeszelRecordsResponse
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetailsResponse
import com.homelab.app.data.remote.dto.beszel.BeszelSystemsResponse
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevicesResponse
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import retrofit2.http.*

interface BeszelApi {

    @POST
    suspend fun authenticate(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Body credentials: Map<String, String>
    ): BeszelAuthResponse

    @GET("api/collections/systems/records")
    suspend fun getSystems(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Query("sort") sort: String = "-updated",
        @Query("perPage") perPage: Int = 50,
        // Only fetch fields needed for the dashboard: identity + CPU/memory + disk (root + extra filesystems).
        @Query("fields") fields: String = "id,name,host,status,info.cpu,info.mp,info.dp,info.efs"
    ): BeszelSystemsResponse

    @GET("api/collections/systems/records/{id}")
    suspend fun getSystem(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Path("id") id: String
    ): BeszelSystem

    @GET("api/collections/system_stats/records?sort=-created")
    suspend fun getSystemRecords(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Query("filter", encoded = true) filter: String,
        @Query("perPage") limit: Int = 30
    ): BeszelRecordsResponse

    @GET("api/collections/system_details/records")
    suspend fun getSystemDetails(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Query("filter", encoded = true) filter: String,
        @Query("perPage") limit: Int = 1
    ): BeszelSystemDetailsResponse

    @GET("api/collections/smart_devices/records")
    suspend fun getSmartDevices(
        @Header("X-Homelab-Service") service: String = "Beszel",
        @Query("filter", encoded = true) filter: String,
        @Query("perPage") limit: Int = 10
    ): BeszelSmartDevicesResponse
}
