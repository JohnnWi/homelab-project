package com.homelab.app.data.remote.dto.pterodactyl

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ---------- Server List ----------

@Serializable
data class PterodactylServerListResponse(
    val data: List<PterodactylServerWrapper>
)

@Serializable
data class PterodactylServerWrapper(
    val attributes: PterodactylServer
)

@Serializable
data class PterodactylServer(
    val identifier: String,
    val uuid: String,
    val name: String,
    val description: String? = null,
    @SerialName("is_suspended") val isSuspended: Boolean = false,
    @SerialName("is_installing") val isInstalling: Boolean = false,
    val status: String? = null,
    @SerialName("server_owner") val isOwner: Boolean = false,
    val node: String? = null,
    val limits: PterodactylLimits = PterodactylLimits()
)

@Serializable
data class PterodactylLimits(
    val memory: Int = 0,
    val disk: Int = 0,
    val cpu: Int = 0,
    val swap: Int = 0
)

// ---------- Resources ----------

@Serializable
data class PterodactylResourcesResponse(
    val attributes: PterodactylResources
)

@Serializable
data class PterodactylResources(
    @SerialName("current_state") val currentState: String = "offline",
    @SerialName("is_suspended") val isSuspended: Boolean = false,
    val resources: PterodactylResourceUsage = PterodactylResourceUsage()
)

@Serializable
data class PterodactylResourceUsage(
    @SerialName("memory_bytes") val memoryBytes: Long = 0,
    @SerialName("memory_limit_bytes") val memoryLimitBytes: Long = 0,
    @SerialName("disk_bytes") val diskBytes: Long = 0,
    @SerialName("cpu_absolute") val cpuAbsolute: Double = 0.0,
    val uptime: Long = 0,
    @SerialName("network_rx_bytes") val networkRxBytes: Long = 0,
    @SerialName("network_tx_bytes") val networkTxBytes: Long = 0
)

// ---------- Power Signal ----------

@Serializable
data class PterodactylPowerRequest(
    val signal: String
)
