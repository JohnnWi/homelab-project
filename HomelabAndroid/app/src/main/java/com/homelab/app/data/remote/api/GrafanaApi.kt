package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header

interface GrafanaApi {

    @GET("api/health")
    suspend fun getHealth(
        @Header("X-Homelab-Service") service: String = "Grafana",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/search")
    suspend fun searchDashboards(
        @Header("X-Homelab-Service") service: String = "Grafana",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonArray

    @GET("api/alertmanager/grafana/api/v2/alerts")
    suspend fun getAlerts(
        @Header("X-Homelab-Service") service: String = "Grafana",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonArray
}
