package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.pterodactyl.PterodactylPowerRequest
import com.homelab.app.data.remote.dto.pterodactyl.PterodactylResourcesResponse
import com.homelab.app.data.remote.dto.pterodactyl.PterodactylServerListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface PterodactylApi {

    @GET("api/client")
    suspend fun getServers(
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): PterodactylServerListResponse

    @GET("api/client/servers/{identifier}/resources")
    suspend fun getServerResources(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("identifier") identifier: String
    ): PterodactylResourcesResponse

    @POST("api/client/servers/{identifier}/power")
    suspend fun sendPowerSignal(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("identifier") identifier: String,
        @Body body: PterodactylPowerRequest
    )
}
