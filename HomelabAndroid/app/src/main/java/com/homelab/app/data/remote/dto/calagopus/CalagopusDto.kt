package com.homelab.app.data.remote.dto.calagopus

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Server List ----------

@Serializable
data class CalagopusServerListResponse(
    val servers: CalagopusServerPage
)

@Serializable
data class CalagopusServerPage(
    val total: Int = 0,
    @SerialName("per_page") val perPage: Int = 0,
    val page: Int = 0,
    val data: List<CalagopusServer>
)

@Serializable
data class CalagopusServer(
    val uuid: String,
    @SerialName("uuid_short") val uuidShort: String,
    val name: String,
    val description: String? = null,
    val status: String? = null,
    @SerialName("is_suspended") val isSuspended: Boolean = false,
    @SerialName("server_owner") val isOwner: Boolean = false,
    @SerialName("node") val nodeName: String? = null,
    val limits: CalagopusLimits = CalagopusLimits()
)

@Serializable
data class CalagopusLimits(
    val memory: Int = 0,
    val disk: Int = 0,
    val cpu: Int = 0,
    val swap: Int = 0
)

// ---------- Resources ----------

@Serializable
data class CalagopusResourcesResponse(
    val resources: CalagopusResources
)

@Serializable
data class CalagopusResources(
    val state: String = "offline",
    @SerialName("memory_bytes") val memoryBytes: Long = 0,
    @SerialName("memory_limit_bytes") val memoryLimitBytes: Long = 0,
    @SerialName("disk_bytes") val diskBytes: Long = 0,
    @SerialName("cpu_absolute") val cpuAbsolute: Double = 0.0,
    val uptime: Long = 0,
    val network: CalagopusNetwork = CalagopusNetwork()
)

@Serializable
data class CalagopusNetwork(
    @SerialName("rx_bytes") val rxBytes: Long = 0,
    @SerialName("tx_bytes") val txBytes: Long = 0
)

// ---------- Power Signal ----------

@Serializable
data class CalagopusPowerRequest(
    val signal: String
)
