package com.homelab.app.ui.beszel

internal enum class ExtraMetricType {
    TEMPERATURE, LOAD, NETWORK, DISK, BATTERY, SWAP
}

internal enum class ResourceMetricType {
    CPU, MEMORY
}

internal enum class GpuMetricType {
    USAGE, POWER, VRAM
}

internal enum class DockerMetricType {
    CPU, MEMORY, NETWORK
}

internal data class BandwidthPoint(
    val rxBytesPerSec: Double,
    val txBytesPerSec: Double
)

internal data class DiskFsUsage(
    val label: String,
    val usedGb: Double,
    val totalGb: Double
)

internal data class DockerMetricSummary(
    val cpuPercent: Double,
    val memoryMb: Double,
    val bandwidthUpBytesPerSec: Double?,
    val bandwidthDownBytesPerSec: Double?
)


