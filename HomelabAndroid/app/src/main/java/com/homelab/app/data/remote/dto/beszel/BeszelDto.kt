package com.homelab.app.data.remote.dto.beszel

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

private const val BESZEL_MB_TO_BYTES = 1024.0 * 1024.0

@Serializable
data class BeszelSystem(
    val id: String,
    val collectionId: String? = null,
    val collectionName: String? = null,
    val name: String,
    val host: String,
    val status: String,
    val info: BeszelSystemInfo? = null,
    val created: String? = null,
    val updated: String? = null
) {
    val isOnline: Boolean get() = status == "up"
}

@Serializable
data class BeszelSystemInfo(
    val cpu: Double? = null,
    val mp: Double? = null,
    val m: Double? = null,
    val mt: Double? = null,
    val dp: Double? = null,
    val d: Double? = null,
    // Beszel >=0.9 exposes du (used) + d (total) for disk.
    // Keep dt for backwards compatibility but prefer du/d.
    val du: Double? = null,
    val dt: Double? = null,
    val ns: Double? = null,
    val nr: Double? = null,
    val u: Double? = null,
    val cm: String? = null,
    val os: String? = null,
    val k: String? = null,
    val h: String? = null,
    val t: Double? = null,
    val c: Int? = null,
    val efs: Map<String, Double?>? = null
) {
    val cpuValue: Double get() = cpu ?: 0.0
    val mpValue: Double get() = mp ?: 0.0
    val mValue: Double get() = m ?: 0.0
    val mtValue: Double get() = mt ?: 0.0
    val dpValue: Double get() = dp ?: 0.0
    val dValue: Double get() = d ?: 0.0
    val duValue: Double get() = du ?: 0.0
    val dtValue: Double get() = dt ?: 0.0
    val nsValue: Double get() = ns ?: 0.0
    val nrValue: Double get() = nr ?: 0.0
    val uValue: Double get() = u ?: 0.0

    // Overall disk usage for the dashboard card.
    // The API only exposes per-drive percentages (root `dp` + optional `efs`),
    // without capacities, so a truly correct global percentage is impossible.
    // To avoid misleading values, we surface just the root disk percentage.
    val overallDiskPercent: Double
        get() {
            return dpValue.coerceIn(0.0, 100.0)
        }
}

@Serializable
data class BeszelSystemsResponse(
    val items: List<BeszelSystem>,
    val totalItems: Int? = null,
    val page: Int? = null,
    val perPage: Int? = null
)

@Serializable
data class BeszelSystemRecord(
    val id: String,
    val system: String? = null,
    val stats: BeszelRecordStats,
    val created: String? = null,
    val updated: String? = null
)

@Serializable
data class BeszelRecordStats(
    val cpu: Double? = null,
    val mp: Double? = null,
    val m: Double? = null,
    val mu: Double? = null,
    val mb: Double? = null,
    val mt: Double? = null,
    val s: Double? = null,
    val su: Double? = null,
    val dp: Double? = null,
    val d: Double? = null,
    val du: Double? = null,
    val dt: Double? = null,
    val dr: Double? = null,
    val dw: Double? = null,
    val b: List<Double>? = null,
    val ns: Double? = null,
    val nr: Double? = null,
    val t: JsonElement? = null,
    val efs: Map<String, BeszelFsEntry>? = null,
    val la: List<Double>? = null,
    val ni: Map<String, List<Double>>? = null,
    val dio: List<Double>? = null,
    val bat: List<Double>? = null,
    val cpub: List<Double>? = null,
    val cpus: List<Double>? = null,
    val dc: List<BeszelContainer>? = null,
    val g: Map<String, BeszelGpuEntry>? = null
) {
    val cpuValue: Double get() = cpu ?: 0.0
    val mpValue: Double get() = mp ?: 0.0
    val mValue: Double get() = m ?: 0.0
    val dpValue: Double get() = dp ?: 0.0
    val dValue: Double get() = d ?: 0.0
    val duValue: Double get() = du ?: 0.0

    val memoryUsedGb: Double?
        get() = mu ?: if (m != null && mp != null) m * mp / 100.0 else null

    val memoryTotalGb: Double?
        get() = m?.takeIf { it > 0.0 }

    val swapUsedGb: Double?
        get() = su?.takeIf { it >= 0.0 }

    val swapTotalGb: Double?
        get() = s?.takeIf { it > 0.0 }

    val maxTempCelsius: Double?
        get() = t
            ?.jsonObject
            ?.values
            ?.mapNotNull { it.jsonPrimitive.doubleOrNull }
            ?.maxOrNull()

    val temperatureSensors: Map<String, Double>
        get() = t
            ?.jsonObject
            ?.mapNotNull { (name, value) ->
                value.jsonPrimitive.doubleOrNull?.let { name to it }
            }
            ?.toMap()
            .orEmpty()

    val loadAvgValues: List<Double> get() = la ?: emptyList()

    val cpuBreakdownValues: List<Double> get() = cpub ?: emptyList()

    val cpuCoreUsageValues: List<Double> get() = cpus ?: emptyList()

    val networkInterfaces: Map<String, BeszelNetworkInterface>
        get() = ni
            ?.mapNotNull { (name, values) ->
                val uploadRate = values.getOrNull(0) ?: return@mapNotNull null
                val downloadRate = values.getOrNull(1) ?: return@mapNotNull null
                name to BeszelNetworkInterface(
                    uploadRateBytesPerSec = uploadRate,
                    downloadRateBytesPerSec = downloadRate,
                    uploadTotalBytes = values.getOrNull(2),
                    downloadTotalBytes = values.getOrNull(3)
                )
            }
            ?.toMap()
            .orEmpty()

    val bandwidthUpBytesPerSec: Double?
        get() = b?.getOrNull(0) ?: ns?.times(BESZEL_MB_TO_BYTES)

    val bandwidthDownBytesPerSec: Double?
        get() = b?.getOrNull(1) ?: nr?.times(BESZEL_MB_TO_BYTES)

    val diskReadBytesPerSec: Double?
        get() = dr ?: dio?.getOrNull(0)

    val diskWriteBytesPerSec: Double?
        get() = dw ?: dio?.getOrNull(1)

    val batteryLevel: Int?
        get() = bat?.getOrNull(0)?.toInt()

    val batteryMinutes: Int?
        get() = bat?.getOrNull(1)?.toInt()

    val primaryGpu: BeszelGpuEntry?
        get() = g?.values?.firstOrNull()

    /** All GPU entries sorted by key (e.g. "0", "1", …) */
    val gpuEntries: List<Map.Entry<String, BeszelGpuEntry>>
        get() = g?.entries?.sortedBy { it.key } ?: emptyList()

    val gpuUsagePercent: Double?
        get() = primaryGpu?.u

    val gpuPowerWatts: Double?
        get() = primaryGpu?.p

    val gpuVramPercent: Double?
        get() = primaryGpu?.memUsagePercent

    val gpuVramUsedMb: Double?
        get() = primaryGpu?.mu

    val gpuVramTotalMb: Double?
        get() = primaryGpu?.mt

    fun gpuUsagePercent(key: String): Double? = g?.get(key)?.u
    fun gpuPowerWatts(key: String): Double? = g?.get(key)?.p
    fun gpuVramPercent(key: String): Double? = g?.get(key)?.memUsagePercent
}

@Serializable
data class BeszelGpuEntry(
    val n: String,
    val u: Double? = null,
    val p: Double? = null,
    val mu: Double? = null,
    val mt: Double? = null
) {
    val memUsedMb: Double get() = mu ?: 0.0
    val memTotalMb: Double get() = mt ?: 0.0

    val memUsagePercent: Double?
        get() = if (mt != null && mt > 0.0 && mu != null) {
            (mu / mt * 100.0).coerceIn(0.0, 100.0)
        } else {
            null
        }
}

@Serializable
data class BeszelFsEntry(
    val d: Double? = null,
    val du: Double? = null,
    val r: Double? = null,
    val w: Double? = null,
    val rb: Double? = null,
    val wb: Double? = null
) {
    val readBytesPerSec: Double?
        get() = rb ?: r

    val writeBytesPerSec: Double?
        get() = wb ?: w
}

@Serializable
data class BeszelContainer(
    val n: String,
    val cpu: Double? = null,
    val m: Double? = null,
    val b: List<Double>? = null,
    val ns: Double? = null,
    val nr: Double? = null
) {
    val id: String get() = n
    val name: String get() = n
    val cpuValue: Double get() = cpu ?: 0.0
    val mValue: Double get() = m ?: 0.0
    val bandwidthUpBytesPerSec: Double?
        get() = b?.getOrNull(0) ?: ns?.times(BESZEL_MB_TO_BYTES)

    val bandwidthDownBytesPerSec: Double?
        get() = b?.getOrNull(1) ?: nr?.times(BESZEL_MB_TO_BYTES)
}

data class BeszelNetworkInterface(
    val uploadRateBytesPerSec: Double,
    val downloadRateBytesPerSec: Double,
    val uploadTotalBytes: Double?,
    val downloadTotalBytes: Double?
)

@Serializable
data class BeszelRecordsResponse(
    val items: List<BeszelSystemRecord>,
    val totalItems: Int? = null,
    val page: Int? = null,
    val perPage: Int? = null
)

@Serializable
data class BeszelAuthResponse(
    val token: String,
    val record: BeszelUserRecord? = null
)

@Serializable
data class BeszelUserRecord(
    val id: String,
    val email: String? = null,
    val username: String? = null
)

@Serializable
data class BeszelSystemDetailsResponse(
    val items: List<BeszelSystemDetails>,
    val totalItems: Int? = null,
    val page: Int? = null,
    val perPage: Int? = null
)

@Serializable
data class BeszelSystemDetails(
    val id: String,
    @SerialName("system") val systemId: String? = null,
    val hostname: String? = null,
    @SerialName("os_name") val osName: String? = null,
    val os: Int? = null,
    val kernel: String? = null,
    val arch: String? = null,
    val cores: Int? = null,
    val threads: Int? = null,
    val memory: Long? = null,
    val cpu: String? = null,
    val podman: Boolean? = null,
    val updated: String? = null
)

@Serializable
data class BeszelSmartAttribute(
    val id: Int? = null,
    @SerialName("n") val name: String,
    @SerialName("v") val value: Int? = null,
    @SerialName("w") val worst: Int? = null,
    @SerialName("t") val threshold: Int? = null,
    @SerialName("rv") val rawValue: Long? = null,
    @SerialName("rs") val rawString: String? = null
)

@Serializable
data class BeszelSmartDevice(
    val id: String,
    @SerialName("system") val systemId: String? = null,
    @SerialName("name") val device: String? = null,
    val model: String? = null,
    @SerialName("capacity") val capacityBytes: Long? = null,
    @SerialName("state") val status: String? = null,
    val type: String? = null,
    @SerialName("hours") val hours: Int? = null,
    @SerialName("cycles") val cycles: Int? = null,
    @SerialName("temp") val temperatureCelsius: Double? = null,
    val updated: String? = null,
    val attributes: List<BeszelSmartAttribute> = emptyList()
)

// MARK: - Container Records (individual container info)

@Serializable
data class BeszelContainerRecord(
    val id: String,
    val collectionId: String? = null,
    val collectionName: String? = null,
    val name: String,
    val cpu: Double? = null,
    val memory: Double? = null,
    val net: Double? = null,
    val health: Int? = null,
    val status: String? = null,
    val image: String? = null,
    val system: String? = null,
    val updated: Long? = null
) {
    val cpuValue: Double get() = cpu ?: 0.0
    val memoryValue: Double get() = memory ?: 0.0
    val netValue: Double get() = net ?: 0.0

    val healthEnum: BeszelContainerHealth
        get() = BeszelContainerHealth.entries.firstOrNull { it.value == (health ?: 0) }
            ?: BeszelContainerHealth.NONE
}

enum class BeszelContainerHealth(val value: Int) {
    NONE(0), STARTING(1), HEALTHY(2), UNHEALTHY(3)
}

@Serializable
data class BeszelContainerRecordsResponse(
    val items: List<BeszelContainerRecord>,
    val totalItems: Int? = null,
    val page: Int? = null,
    val perPage: Int? = null
)

// MARK: - Container Stats (time series)

@Serializable
data class BeszelContainerStatsRecord(
    val id: String,
    val system: String? = null,
    val stats: List<BeszelContainerStat>,
    val created: String? = null
)

@Serializable
data class BeszelContainerStat(
    val n: String,
    val c: Double,
    val m: Double,
    val ns: Double? = null,
    val nr: Double? = null
) {
    val name: String get() = n
    val cpuValue: Double get() = c
    val memoryValue: Double get() = m
    val netSent: Double get() = ns ?: 0.0
    val netReceived: Double get() = nr ?: 0.0
}

@Serializable
data class BeszelContainerStatsResponse(
    val items: List<BeszelContainerStatsRecord>,
    val totalItems: Int? = null,
    val page: Int? = null,
    val perPage: Int? = null
)

@Serializable
data class BeszelSmartDevicesResponse(
    val items: List<BeszelSmartDevice>,
    val totalItems: Int? = null,
    val page: Int? = null,
    val perPage: Int? = null
)