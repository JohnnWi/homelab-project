package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonElement
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface KomodoApi {

    @POST("read/GetVersion")
    suspend fun getVersion(
        @Body body: Map<String, String> = emptyMap(),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetServersSummary")
    suspend fun getServersSummary(
        @Body body: Map<String, String> = emptyMap(),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetDeploymentsSummary")
    suspend fun getDeploymentsSummary(
        @Body body: Map<String, String> = emptyMap(),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetStacksSummary")
    suspend fun getStacksSummary(
        @Body body: Map<String, String> = emptyMap(),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetDockerContainersSummary")
    suspend fun getDockerContainersSummary(
        @Body body: Map<String, String> = emptyMap(),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/ListStacks")
    suspend fun listStacks(
        @Body body: Map<String, @JvmSuppressWildcards Any?> = mapOf("query" to emptyMap<String, Any>()),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetStack")
    suspend fun getStack(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/ListStackServices")
    suspend fun listStackServices(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("execute/DeployStack")
    suspend fun deployStack(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("execute/StartStack")
    suspend fun startStack(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("execute/StopStack")
    suspend fun stopStack(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("execute/RestartStack")
    suspend fun restartStack(
        @Body body: Map<String, @JvmSuppressWildcards Any?>,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement
}
