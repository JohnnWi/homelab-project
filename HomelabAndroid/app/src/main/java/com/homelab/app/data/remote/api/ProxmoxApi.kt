package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header

interface ProxmoxApi {

    @GET("api2/json/nodes")
    suspend fun getNodes(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api2/json/cluster/resources")
    suspend fun getClusterResources(
        @Header("X-Homelab-Service") service: String = "Proxmox",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject
}
