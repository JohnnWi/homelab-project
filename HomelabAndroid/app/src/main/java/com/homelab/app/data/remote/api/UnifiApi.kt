package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Url

interface UnifiApi {

    @GET
    suspend fun getJson(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Ubiquiti Network",
        @Header("X-Homelab-Instance-Id") instanceId: String? = null,
        @Header("X-Homelab-Bypass") bypass: String? = null,
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Header("Accept") accept: String = "application/json"
    ): JsonElement

    @POST
    suspend fun postJson(
        @Url url: String,
        @Body body: JsonObject,
        @Header("X-Homelab-Service") service: String = "Ubiquiti Network",
        @Header("X-Homelab-Instance-Id") instanceId: String? = null,
        @Header("X-Homelab-Bypass") bypass: String? = null,
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String? = null,
        @Header("X-API-Key") apiKey: String? = null,
        @Header("Accept") accept: String = "application/json"
    ): JsonElement
}
