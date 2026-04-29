package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.UptimeKumaApi
import java.util.Base64
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

enum class UptimeKumaMonitorStatus(val rawValue: Int) {
    DOWN(0),
    UP(1),
    PENDING(2),
    MAINTENANCE(3),
    UNKNOWN(-1);

    companion object {
        fun from(value: Double?): UptimeKumaMonitorStatus {
            if (value == null || !value.isFinite()) return UNKNOWN
            return when (value.toInt()) {
                0 -> DOWN
                1 -> UP
                2 -> PENDING
                3 -> MAINTENANCE
                else -> UNKNOWN
            }
        }
    }
}

data class UptimeKumaMonitor(
    val id: String,
    val name: String,
    val type: String?,
    val url: String?,
    val status: UptimeKumaMonitorStatus,
    val responseTimeMs: Double?,
    val certDaysRemaining: Double?
)

data class UptimeKumaDashboardData(
    val monitors: List<UptimeKumaMonitor>
) {
    val upCount: Int get() = monitors.count { it.status == UptimeKumaMonitorStatus.UP }
    val downCount: Int get() = monitors.count { it.status == UptimeKumaMonitorStatus.DOWN }
    val pendingCount: Int get() = monitors.count { it.status == UptimeKumaMonitorStatus.PENDING }
    val maintenanceCount: Int get() = monitors.count { it.status == UptimeKumaMonitorStatus.MAINTENANCE }
    val unknownCount: Int get() = monitors.count { it.status == UptimeKumaMonitorStatus.UNKNOWN }
    val averageLatencyMs: Double?
        get() = monitors.mapNotNull { it.responseTimeMs }.filter { it.isFinite() && it >= 0.0 }.takeIf { it.isNotEmpty() }?.average()
    val expiringCertificates: Int
        get() = monitors.count { days -> days.certDaysRemaining?.let { it in 0.0..30.0 } == true }
}

data class UptimeKumaSummary(
    val upCount: Int,
    val totalCount: Int
)

@Singleton
class UptimeKumaRepository @Inject constructor(
    private val api: UptimeKumaApi,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(
        url: String,
        username: String? = null,
        passwordOrApiKey: String? = null,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) {
        val baseCandidates = listOf(cleanUrl(url), cleanOptionalUrl(fallbackUrl))
            .filterNotNull()
            .distinct()
        var lastError: Exception? = null

        for (base in baseCandidates) {
            try {
                val metrics = fetchMetricsText(
                    baseUrl = base,
                    username = username,
                    passwordOrApiKey = passwordOrApiKey,
                    allowSelfSigned = allowSelfSigned
                )
                validateMetrics(metrics)
                return
            } catch (error: Exception) {
                lastError = error
            }
        }

        throw lastError ?: IllegalStateException("Uptime Kuma validation failed.")
    }

    suspend fun getDashboard(instanceId: String): UptimeKumaDashboardData {
        val metrics = api.getMetrics(instanceId = instanceId).string()
        return parseMetrics(metrics)
    }

    suspend fun getSummary(instanceId: String): UptimeKumaSummary {
        val data = getDashboard(instanceId)
        return UptimeKumaSummary(upCount = data.upCount, totalCount = data.monitors.size)
    }

    private suspend fun fetchMetricsText(
        baseUrl: String,
        username: String?,
        passwordOrApiKey: String?,
        allowSelfSigned: Boolean
    ): String = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/metrics")
            .get()
            .addHeader("Accept", "text/plain")

        val secret = passwordOrApiKey.orEmpty()
        if (secret.isNotBlank()) {
            val credentials = "${username.orEmpty()}:$secret"
            val encoded = Base64.getEncoder().encodeToString(credentials.toByteArray(Charsets.UTF_8))
            requestBuilder.addHeader("Authorization", "Basic $encoded")
        }

        tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
            .newCall(requestBuilder.build())
            .execute()
            .use { response ->
                when (response.code) {
                    in 200..399 -> response.body?.string().orEmpty()
                    401, 403 -> throw IllegalStateException("Uptime Kuma authentication failed.")
                    else -> throw IllegalStateException("Uptime Kuma returned HTTP ${response.code}.")
                }
            }
    }

    fun parseMetrics(metrics: String): UptimeKumaDashboardData {
        val records = linkedMapOf<String, MutableMonitorRecord>()
        metrics.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .forEach { line ->
                val sample = UptimeKumaMetricSample.parse(line) ?: return@forEach
                val metric = sample.name.lowercase()
                if (!metric.contains("monitor")) return@forEach

                val identity = sample.labels["monitor_name"]
                    ?: sample.labels["name"]
                    ?: sample.labels["monitor"]
                    ?: sample.labels["id"]
                    ?: return@forEach
                val key = sample.labels["monitor_id"] ?: sample.labels["id"] ?: identity
                val record = records.getOrPut(key) {
                    MutableMonitorRecord(id = key, name = identity)
                }

                record.name = identity
                record.type = sample.labels["monitor_type"] ?: sample.labels["type"] ?: record.type
                record.url = sample.labels["monitor_url"]
                    ?: sample.labels["url"]
                    ?: sample.labels["monitor_hostname"]
                    ?: sample.labels["hostname"]
                    ?: record.url

                when {
                    metric.endsWith("monitor_status") || metric == "monitor_status" -> record.statusValue = sample.value
                    metric.endsWith("monitor_response_time") || metric == "monitor_response_time" -> record.responseTimeMs = sample.value.takeIf { it.isFinite() }
                    metric.endsWith("monitor_cert_days_remaining") || metric == "monitor_cert_days_remaining" -> record.certDaysRemaining = sample.value.takeIf { it.isFinite() }
                }
            }

        return UptimeKumaDashboardData(
            monitors = records.values
                .map { it.toMonitor() }
                .sortedWith(compareBy<UptimeKumaMonitor> { statusRank(it.status) }.thenBy { it.name.lowercase() })
        )
    }

    private fun validateMetrics(metrics: String) {
        val looksLikeUptimeKuma = metrics.contains("uptime_kuma", ignoreCase = true) ||
            metrics.contains("monitor_status", ignoreCase = true) ||
            metrics.contains("monitor_response_time", ignoreCase = true)
        if (!looksLikeUptimeKuma) {
            throw IllegalStateException("Uptime Kuma metrics were not found.")
        }
    }

    private data class MutableMonitorRecord(
        val id: String,
        var name: String,
        var type: String? = null,
        var url: String? = null,
        var statusValue: Double? = null,
        var responseTimeMs: Double? = null,
        var certDaysRemaining: Double? = null
    ) {
        fun toMonitor(): UptimeKumaMonitor {
            return UptimeKumaMonitor(
                id = id,
                name = name,
                type = type,
                url = url,
                status = UptimeKumaMonitorStatus.from(statusValue),
                responseTimeMs = responseTimeMs,
                certDaysRemaining = certDaysRemaining
            )
        }
    }

    private fun statusRank(status: UptimeKumaMonitorStatus): Int {
        return when (status) {
            UptimeKumaMonitorStatus.DOWN -> 0
            UptimeKumaMonitorStatus.PENDING -> 1
            UptimeKumaMonitorStatus.MAINTENANCE -> 2
            UptimeKumaMonitorStatus.UNKNOWN -> 3
            UptimeKumaMonitorStatus.UP -> 4
        }
    }

    private data class UptimeKumaMetricSample(
        val name: String,
        val labels: Map<String, String>,
        val value: Double
    ) {
        companion object {
            fun parse(line: String): UptimeKumaMetricSample? {
                val splitIndex = line.indexOfLast { it.isWhitespace() }
                if (splitIndex <= 0 || splitIndex >= line.lastIndex) return null

                val head = line.substring(0, splitIndex).trim()
                val value = line.substring(splitIndex + 1).trim().toDoubleOrNull() ?: return null
                val labelsStart = head.indexOf('{')
                if (labelsStart < 0) return UptimeKumaMetricSample(head, emptyMap(), value)

                val name = head.substring(0, labelsStart)
                val labelsEnd = head.lastIndexOf('}')
                if (labelsEnd <= labelsStart) return null
                val labels = parseLabels(head.substring(labelsStart + 1, labelsEnd))
                return UptimeKumaMetricSample(name = name, labels = labels, value = value)
            }

            private fun parseLabels(raw: String): Map<String, String> {
                val labels = linkedMapOf<String, String>()
                var index = 0
                while (index < raw.length) {
                    while (index < raw.length && (raw[index] == ',' || raw[index].isWhitespace())) index++
                    val keyStart = index
                    while (index < raw.length && raw[index] != '=') index++
                    if (index >= raw.length) break
                    val key = raw.substring(keyStart, index).trim()
                    index++
                    if (index >= raw.length || raw[index] != '"') break
                    index++
                    val value = StringBuilder()
                    while (index < raw.length) {
                        val char = raw[index]
                        if (char == '\\' && index + 1 < raw.length) {
                            value.append(raw[index + 1])
                            index += 2
                        } else if (char == '"') {
                            index++
                            break
                        } else {
                            value.append(char)
                            index++
                        }
                    }
                    if (key.isNotBlank()) labels[key] = value.toString()
                    while (index < raw.length && raw[index] != ',') index++
                }
                return labels
            }
        }
    }
}

private fun cleanUrl(raw: String): String {
    var clean = raw.trim()
    clean = clean.trimEnd { it == ')' || it == ']' || it == '}' || it == ',' || it == ';' }
    if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
        clean = "https://$clean"
    }
    return clean.replace(Regex("/+$"), "")
}

private fun cleanOptionalUrl(raw: String?): String? {
    if (raw.isNullOrBlank()) return null
    return cleanUrl(raw)
}
