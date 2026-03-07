package com.homelab.app.ui.beszel

internal enum class ExtraMetricType {
    TEMPERATURE, LOAD, NETWORK, DISK, BATTERY
}

internal enum class ResourceMetricType {
    CPU, MEMORY
}

internal enum class GpuMetricType {
    USAGE, POWER, VRAM
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


