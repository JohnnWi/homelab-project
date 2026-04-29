package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.calagopus.CalagopusPowerRequest
import com.homelab.app.data.remote.dto.calagopus.CalagopusResourcesResponse
import com.homelab.app.data.remote.dto.calagopus.CalagopusServerListResponse
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path

interface CalagopusApi {

    @GET("api/client/servers")
    suspend fun getServers(
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): CalagopusServerListResponse

    @GET("api/client/servers/{uuidShort}/resources")
    suspend fun getServerResources(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("uuidShort") uuidShort: String
    ): CalagopusResourcesResponse

    @POST("api/client/servers/{uuidShort}/power")
    suspend fun sendPowerSignal(
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("uuidShort") uuidShort: String,
        @Body body: CalagopusPowerRequest
    )
}
