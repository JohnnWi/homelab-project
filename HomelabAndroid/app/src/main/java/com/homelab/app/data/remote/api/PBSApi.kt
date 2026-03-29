package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header

interface PBSApi {

    @GET("api2/json/admin/datastore")
    suspend fun getDatastores(
        @Header("X-Homelab-Service") service: String = "PBS",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api2/json/status/datastore-usage")
    suspend fun getDatastoreUsage(
        @Header("X-Homelab-Service") service: String = "PBS",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject
}
