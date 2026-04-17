package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonObject
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query
import retrofit2.http.QueryMap

interface TechnitiumApi {

    @GET("api/user/login")
    suspend fun login(
        @Query("user") user: String,
        @Query("pass") password: String,
        @Query("totp") totp: String? = null,
        @Query("includeInfo") includeInfo: Boolean = true,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false"
    ): JsonObject

    @GET("api/user/session/get")
    suspend fun getSession(
        @Query("token") token: String,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/dashboard/stats/get")
    suspend fun getDashboardStats(
        @Query("token") token: String,
        @Query("type") type: String,
        @Query("utc") utc: Boolean = true,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/dashboard/stats/getTop")
    suspend fun getTopStats(
        @Query("token") token: String,
        @Query("type") type: String,
        @Query("statsType") statsType: String,
        @Query("limit") limit: Int = 20,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/settings/get")
    suspend fun getSettings(
        @Query("token") token: String,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/settings/set")
    suspend fun setSettings(
        @QueryMap params: Map<String, String>,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/settings/forceUpdateBlockLists")
    suspend fun forceUpdateBlockLists(
        @Query("token") token: String,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/settings/temporaryDisableBlocking")
    suspend fun temporaryDisableBlocking(
        @Query("token") token: String,
        @Query("minutes") minutes: Int,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/zones/list")
    suspend fun listZones(
        @Query("token") token: String,
        @Query("pageNumber") pageNumber: Int = 1,
        @Query("zonesPerPage") zonesPerPage: Int = 1,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/cache/list")
    suspend fun listCache(
        @Query("token") token: String,
        @Query("domain") domain: String = "",
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/blocked/list")
    suspend fun listBlockedZones(
        @Query("token") token: String,
        @Query("domain") domain: String = "",
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/blocked/add")
    suspend fun addBlockedZone(
        @Query("token") token: String,
        @Query("domain") domain: String,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/blocked/delete")
    suspend fun deleteBlockedZone(
        @Query("token") token: String,
        @Query("domain") domain: String,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject

    @GET("api/logs/list")
    suspend fun listLogs(
        @Query("token") token: String,
        @Header("X-Homelab-Service") service: String = "Technitium",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonObject
}
