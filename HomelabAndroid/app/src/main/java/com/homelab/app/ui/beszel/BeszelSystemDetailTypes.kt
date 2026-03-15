package com.homelab.app.ui.beszel

import com.homelab.app.data.remote.dto.beszel.BeszelContainer
import com.homelab.app.data.remote.dto.beszel.BeszelRecordStats
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetails

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
    val memoryUsedMb: Double,
    val uploadRateBytesPerSec: Double?,
    val downloadRateBytesPerSec: Double?
)

internal data class BeszelSystemDetailUiModel(
    val system: BeszelSystem,
    val systemDetails: BeszelSystemDetails?,
    val statsHistory: List<BeszelRecordStats>,
    val latestStats: BeszelRecordStats?,
    val smartDevices: List<BeszelSmartDevice>,
    val cpuHistoryPercent: List<Double>,
    val memoryHistoryPercent: List<Double>,
    val memoryUsedHistoryGb: List<Double>,
    val diskUsedGb: Double?,
    val diskTotalGb: Double?,
    val memoryUsedGb: Double?,
    val memoryTotalGb: Double?,
    val externalFileSystems: List<DiskFsUsage>,
    val dockerSummary: DockerMetricSummary?,
    val dockerCpuHistoryPercent: List<Double>,
    val dockerMemoryUsedHistoryMb: List<Double>,
    val dockerUploadRateHistoryBytesPerSec: List<Double>,
    val dockerDownloadRateHistoryBytesPerSec: List<Double>,
    val hasDockerNetwork: Boolean,
    val containers: List<BeszelContainer>,
    val perCoreCpuPercent: List<Double>
)


