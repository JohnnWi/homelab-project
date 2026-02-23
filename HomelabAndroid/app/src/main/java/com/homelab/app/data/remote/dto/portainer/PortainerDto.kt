package com.homelab.app.data.remote.dto.portainer

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class PortainerAuthResponse(
    val jwt: String
)

@Serializable
data class PortainerEndpoint(
    @SerialName("Id") val id: Int,
    @SerialName("Name") val name: String,
    @SerialName("Type") val type: Int,
    @SerialName("URL") val url: String,
    @SerialName("Status") val status: Int,
    @SerialName("Snapshots") val snapshots: List<EndpointSnapshot>? = null,
    @SerialName("PublicURL") val publicUrl: String? = null,
    @SerialName("GroupId") val groupId: Int? = null,
    @SerialName("TagIds") val tagIds: List<Int>? = null
) {
    val isOnline: Boolean get() = status == 1
}

@Serializable
data class EndpointSnapshot(
    @SerialName("DockerVersion") val dockerVersion: String? = null,
    @SerialName("TotalCPU") val totalCpu: Int,
    @SerialName("TotalMemory") val totalMemory: Long,
    @SerialName("RunningContainerCount") val runningContainerCount: Int,
    @SerialName("StoppedContainerCount") val stoppedContainerCount: Int,
    @SerialName("HealthyContainerCount") val healthyContainerCount: Int,
    @SerialName("UnhealthyContainerCount") val unhealthyContainerCount: Int,
    @SerialName("VolumeCount") val volumeCount: Int,
    @SerialName("ImageCount") val imageCount: Int,
    @SerialName("ServiceCount") val serviceCount: Int,
    @SerialName("StackCount") val stackCount: Int,
    @SerialName("NodeCount") val nodeCount: Int? = null,
    @SerialName("Time") val time: Long,
    @SerialName("DockerSnapshotRaw") val dockerSnapshotRaw: DockerSnapshotRaw? = null
)

@Serializable
data class DockerSnapshotRaw(
    @SerialName("Containers") val containers: Int? = null,
    @SerialName("ContainersRunning") val containersRunning: Int? = null,
    @SerialName("ContainersPaused") val containersPaused: Int? = null,
    @SerialName("ContainersStopped") val containersStopped: Int? = null,
    @SerialName("Images") val images: Int? = null,
    @SerialName("NCPU") val ncpu: Int? = null,
    @SerialName("MemTotal") val memTotal: Long? = null,
    @SerialName("OperatingSystem") val operatingSystem: String? = null,
    @SerialName("Architecture") val architecture: String? = null,
    @SerialName("KernelVersion") val kernelVersion: String? = null,
    @SerialName("ServerVersion") val serverVersion: String? = null,
    @SerialName("Name") val name: String? = null
)

@Serializable
data class PortainerContainer(
    @SerialName("Id") val id: String,
    @SerialName("Names") val names: List<String>,
    @SerialName("Image") val image: String,
    @SerialName("ImageID") val imageId: String,
    @SerialName("Command") val command: String,
    @SerialName("Created") val created: Long,
    @SerialName("State") val state: String,
    @SerialName("Status") val status: String,
    @SerialName("Ports") val ports: List<ContainerPort> = emptyList(),
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
    @SerialName("SizeRw") val sizeRw: Long? = null,
    @SerialName("SizeRootFs") val sizeRootFs: Long? = null,
    @SerialName("HostConfig") val hostConfig: ContainerHostConfig? = null,
    @SerialName("NetworkSettings") val networkSettings: ContainerNetworkSettings? = null,
    @SerialName("Mounts") val mounts: List<ContainerMount> = emptyList()
) {
    val displayName: String
        get() = if (names.isNotEmpty()) names[0].replace(Regex("^/"), "") else "Unknown"
}

@Serializable
data class ContainerPort(
    @SerialName("IP") val ip: String? = null,
    @SerialName("PrivatePort") val privatePort: Int,
    @SerialName("PublicPort") val publicPort: Int? = null,
    @SerialName("Type") val type: String
)

@Serializable
data class ContainerHostConfig(
    @SerialName("NetworkMode") val networkMode: String = "",
    @SerialName("RestartPolicy") val restartPolicy: RestartPolicy? = null
)

@Serializable
data class RestartPolicy(
    @SerialName("Name") val name: String,
    @SerialName("MaximumRetryCount") val maximumRetryCount: Int
)

@Serializable
data class ContainerNetworkSettings(
    @SerialName("Networks") val networks: Map<String, ContainerNetwork> = emptyMap()
)

@Serializable
data class ContainerNetwork(
    @SerialName("IPAddress") val ipAddress: String,
    @SerialName("Gateway") val gateway: String,
    @SerialName("MacAddress") val macAddress: String,
    @SerialName("NetworkID") val networkId: String
)

@Serializable
data class ContainerMount(
    @SerialName("Type") val type: String,
    @SerialName("Name") val name: String? = null,
    @SerialName("Source") val source: String,
    @SerialName("Destination") val destination: String,
    @SerialName("Mode") val mode: String,
    @SerialName("RW") val rw: Boolean
)

@Serializable
data class ContainerDetail(
    @SerialName("Id") val id: String,
    @SerialName("Name") val name: String,
    @SerialName("Created") val created: String,
    @SerialName("State") val state: ContainerState,
    @SerialName("Image") val image: String,
    @SerialName("Config") val config: ContainerConfig,
    @SerialName("HostConfig") val hostConfig: ContainerDetailHostConfig,
    @SerialName("NetworkSettings") val networkSettings: ContainerDetailNetworkSettings,
    @SerialName("Mounts") val mounts: List<ContainerMount> = emptyList()
) {
    val displayName: String
        get() = name.removePrefix("/")
}

@Serializable
data class ContainerState(
    @SerialName("Status") val status: String,
    @SerialName("Running") val running: Boolean,
    @SerialName("Paused") val paused: Boolean,
    @SerialName("Restarting") val restarting: Boolean,
    @SerialName("OOMKilled") val oomKilled: Boolean,
    @SerialName("Dead") val dead: Boolean,
    @SerialName("Pid") val pid: Int,
    @SerialName("ExitCode") val exitCode: Int,
    @SerialName("Error") val error: String,
    @SerialName("StartedAt") val startedAt: String,
    @SerialName("FinishedAt") val finishedAt: String
)

@Serializable
data class ContainerConfig(
    @SerialName("Hostname") val hostname: String,
    @SerialName("Env") val env: List<String> = emptyList(),
    @SerialName("Image") val image: String,
    @SerialName("Labels") val labels: Map<String, String> = emptyMap(),
    @SerialName("Cmd") val cmd: List<String>? = null,
    @SerialName("Entrypoint") val entrypoint: List<String>? = null,
    @SerialName("WorkingDir") val workingDir: String? = null
)

@Serializable
data class ContainerDetailHostConfig(
    @SerialName("NetworkMode") val networkMode: String,
    @SerialName("RestartPolicy") val restartPolicy: RestartPolicy,
    @SerialName("Memory") val memory: Long,
    @SerialName("NanoCpus") val nanoCpus: Long,
    @SerialName("CpuShares") val cpuShares: Int,
    @SerialName("Binds") val binds: List<String>? = null
)

@Serializable
data class ContainerDetailNetworkSettings(
    @SerialName("Networks") val networks: Map<String, ContainerNetwork> = emptyMap()
)

@Serializable
data class ContainerStats(
    val cpu_stats: CpuStats,
    val precpu_stats: CpuStats,
    val memory_stats: MemoryStats,
    val networks: Map<String, NetworkStats>? = null,
    val blkio_stats: BlkioStats? = null
)

@Serializable
data class CpuStats(
    val cpu_usage: CpuUsage,
    val system_cpu_usage: Long? = null,
    val online_cpus: Int? = null
)

@Serializable
data class CpuUsage(
    val total_usage: Long,
    val percpu_usage: List<Long>? = null
)

@Serializable
data class MemoryStats(
    val usage: Long,
    val limit: Long,
    val stats: MemoryCacheStats? = null
)

@Serializable
data class MemoryCacheStats(
    val cache: Long? = null
)

@Serializable
data class NetworkStats(
    val rx_bytes: Long,
    val tx_bytes: Long
)

@Serializable
data class BlkioStats(
    val io_service_bytes_recursive: List<BlkioEntry>? = null
)

@Serializable
data class BlkioEntry(
    val op: String,
    val value: Long
)

@Serializable
data class PortainerStack(
    @SerialName("Id") val id: Int,
    @SerialName("Name") val name: String,
    @SerialName("Type") val type: Int,
    @SerialName("EndpointId") val endpointId: Int,
    @SerialName("Status") val status: Int,
    @SerialName("CreationDate") val creationDate: Long? = null,
    @SerialName("UpdateDate") val updateDate: Long? = null
) {
    val isActive: Boolean get() = status == 1
}

@Serializable
data class PortainerStackFile(
    @SerialName("StackFileContent") val stackFileContent: String
)

@Serializable
data class UpdateStackRequest(
    val stackFileContent: String,
    val env: List<String> = emptyList(),
    val prune: Boolean = false
)

enum class ContainerAction(val displayName: String, val isDestructive: Boolean) {
    start("Start", false),
    stop("Stop", true),
    restart("Restart", false),
    kill("Kill", true),
    pause("Pause", false),
    unpause("Resume", false)
}
