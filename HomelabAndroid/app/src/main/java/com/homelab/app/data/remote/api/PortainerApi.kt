package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.portainer.*
import okhttp3.ResponseBody
import retrofit2.http.*

interface PortainerApi {

    @POST
    suspend fun authenticate(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false",
        @Body credentials: Map<String, String>
    ): PortainerAuthResponse

    @GET
    suspend fun testApiKey(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false",
        @Header("X-API-Key") apiKey: String
    ): List<PortainerEndpoint>

    @GET("api/endpoints")
    suspend fun getEndpoints(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<PortainerEndpoint>

    @GET("api/endpoints/{id}/docker/containers/json")
    suspend fun getContainers(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") endpointId: Int,
        @Query("all") all: Boolean = true
    ): List<PortainerContainer>

    @GET("api/endpoints/{id}/docker/containers/{containerId}/json")
    suspend fun getContainerDetail(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") endpointId: Int,
        @Path("containerId") containerId: String
    ): ContainerDetail

    @GET("api/endpoints/{id}/docker/containers/{containerId}/stats")
    suspend fun getContainerStats(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") endpointId: Int,
        @Path("containerId") containerId: String,
        @Query("stream") stream: Boolean = false
    ): ContainerStats

    @GET("api/endpoints/{id}/docker/containers/{containerId}/logs")
    suspend fun getContainerLogs(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") endpointId: Int,
        @Path("containerId") containerId: String,
        @Query("stdout") stdout: Boolean = true,
        @Query("stderr") stderr: Boolean = true,
        @Query("tail") tail: Int = 100,
        @Query("timestamps") timestamps: Boolean = true
    ): ResponseBody

    @POST("api/endpoints/{id}/docker/containers/{containerId}/{action}")
    suspend fun containerAction(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") endpointId: Int,
        @Path("containerId") containerId: String,
        @Path("action") action: String
    )

    @DELETE("api/endpoints/{id}/docker/containers/{containerId}")
    suspend fun removeContainer(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") endpointId: Int,
        @Path("containerId") containerId: String,
        @Query("force") force: Boolean = false
    )

    @POST("api/endpoints/{id}/docker/containers/{containerId}/rename")
    suspend fun renameContainer(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") endpointId: Int,
        @Path("containerId") containerId: String,
        @Query("name") name: String
    )

    @GET("api/stacks")
    suspend fun getStacks(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("filters") filters: String
    ): List<PortainerStack>

    @GET("api/stacks/{id}/file")
    suspend fun getStackFile(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") stackId: Int
    ): PortainerStackFile

    @PUT("api/stacks/{id}")
    suspend fun updateStackFile(
        @Header("X-Homelab-Service") service: String = "Portainer",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") stackId: Int,
        @Query("endpointId") endpointId: Int,
        @Body request: UpdateStackRequest
    )
}
