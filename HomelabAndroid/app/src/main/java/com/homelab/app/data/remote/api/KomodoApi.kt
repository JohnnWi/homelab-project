package com.homelab.app.data.remote.api

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface KomodoApi {

    @POST("read/GetVersion")
    suspend fun getVersion(
        @Body body: JsonObject = JsonObject(emptyMap()),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetServersSummary")
    suspend fun getServersSummary(
        @Body body: JsonObject = JsonObject(emptyMap()),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetDeploymentsSummary")
    suspend fun getDeploymentsSummary(
        @Body body: JsonObject = JsonObject(emptyMap()),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetStacksSummary")
    suspend fun getStacksSummary(
        @Body body: JsonObject = JsonObject(emptyMap()),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetDockerContainersSummary")
    suspend fun getDockerContainersSummary(
        @Body body: JsonObject = JsonObject(emptyMap()),
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/ListStacks")
    suspend fun listStacks(
        @Body body: JsonObject,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/GetStack")
    suspend fun getStack(
        @Body body: JsonObject,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("read/ListStackServices")
    suspend fun listStackServices(
        @Body body: JsonObject,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("execute/DeployStack")
    suspend fun deployStack(
        @Body body: JsonObject,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("execute/StartStack")
    suspend fun startStack(
        @Body body: JsonObject,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("execute/StopStack")
    suspend fun stopStack(
        @Body body: JsonObject,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement

    @POST("execute/RestartStack")
    suspend fun restartStack(
        @Body body: JsonObject,
        @Header("X-Homelab-Service") service: String = "Komodo",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): JsonElement
}
