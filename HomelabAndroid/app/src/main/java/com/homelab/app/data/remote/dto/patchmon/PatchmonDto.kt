package com.homelab.app.data.remote.dto.patchmon

import kotlinx.serialization.KSerializer
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.nullable
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class PatchmonHostsResponse(
    val hosts: List<PatchmonHost> = emptyList(),
    val total: Int = 0,
    @SerialName("filtered_by_groups") val filteredByGroups: List<String> = emptyList()
)

@Serializable
data class PatchmonHost(
    val id: String,
    @SerialName("friendly_name") val friendlyName: String = "",
    val hostname: String = "",
    val ip: String = "",
    @SerialName("host_groups") val hostGroups: List<PatchmonHostGroup> = emptyList(),
    @SerialName("os_type") val osType: String = "",
    @SerialName("os_version") val osVersion: String = "",
    @SerialName("last_update") val lastUpdate: String? = null,
    val status: String = "",
    @SerialName("needs_reboot") val needsReboot: Boolean = false,
    @SerialName("updates_count") val updatesCount: Int = 0,
    @SerialName("security_updates_count") val securityUpdatesCount: Int = 0,
    @SerialName("total_packages") val totalPackages: Int = 0
)

@Serializable
data class PatchmonHostGroup(
    val id: String,
    val name: String = ""
)

@Serializable
data class PatchmonHostInfo(
    val id: String,
    @SerialName("machine_id") val machineId: String? = null,
    @SerialName("friendly_name") val friendlyName: String = "",
    val hostname: String = "",
    val ip: String = "",
    @SerialName("os_type") val osType: String = "",
    @SerialName("os_version") val osVersion: String = "",
    @SerialName("agent_version") val agentVersion: String? = null,
    @SerialName("host_groups") val hostGroups: List<PatchmonHostGroup> = emptyList()
)

@Serializable
data class PatchmonHostStats(
    @SerialName("host_id") val hostId: String = "",
    @SerialName("total_installed_packages") val totalInstalledPackages: Int = 0,
    @SerialName("outdated_packages") val outdatedPackages: Int = 0,
    @SerialName("security_updates") val securityUpdates: Int = 0,
    @SerialName("total_repos") val totalRepos: Int = 0
)

@Serializable
data class PatchmonHostSystem(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    @Serializable(with = FlexibleNullableStringSerializer::class) val architecture: String? = null,
    @SerialName("kernel_version") @Serializable(with = FlexibleNullableStringSerializer::class) val kernelVersion: String? = null,
    @SerialName("installed_kernel_version") @Serializable(with = FlexibleNullableStringSerializer::class) val installedKernelVersion: String? = null,
    @SerialName("selinux_status") @Serializable(with = FlexibleNullableStringSerializer::class) val selinuxStatus: String? = null,
    @SerialName("system_uptime") @Serializable(with = FlexibleNullableStringSerializer::class) val systemUptime: String? = null,
    @SerialName("cpu_model") @Serializable(with = FlexibleNullableStringSerializer::class) val cpuModel: String? = null,
    @SerialName("cpu_cores") @Serializable(with = FlexibleNullableIntSerializer::class) val cpuCores: Int? = null,
    @SerialName("ram_installed") @Serializable(with = FlexibleNullableStringSerializer::class) val ramInstalled: String? = null,
    @SerialName("swap_size") @Serializable(with = FlexibleNullableStringSerializer::class) val swapSize: String? = null,
    @SerialName("load_average") @Serializable(with = FlexibleLoadAverageSerializer::class) val loadAverage: PatchmonLoadAverage? = null,
    @SerialName("disk_details") @Serializable(with = FlexibleDiskDetailsSerializer::class) val diskDetails: List<PatchmonDiskDetail> = emptyList(),
    @SerialName("needs_reboot") val needsReboot: Boolean = false,
    @SerialName("reboot_reason") @Serializable(with = FlexibleNullableStringSerializer::class) val rebootReason: String? = null
)

@Serializable
data class PatchmonLoadAverage(
    @SerialName("1min") @Serializable(with = FlexibleDoubleSerializer::class) val oneMin: Double = 0.0,
    @SerialName("5min") @Serializable(with = FlexibleDoubleSerializer::class) val fiveMin: Double = 0.0,
    @SerialName("15min") @Serializable(with = FlexibleDoubleSerializer::class) val fifteenMin: Double = 0.0
)

@Serializable
data class PatchmonDiskDetail(
    @Serializable(with = FlexibleStringSerializer::class) val filesystem: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val size: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val used: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val available: String = "",
    @SerialName("use_percent") @Serializable(with = FlexibleStringSerializer::class) val usePercent: String = "",
    @SerialName("mounted_on") @Serializable(with = FlexibleStringSerializer::class) val mountedOn: String = ""
)

@Serializable
data class PatchmonHostNetwork(
    @Serializable(with = FlexibleStringSerializer::class) val id: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val ip: String = "",
    @SerialName("gateway_ip") @Serializable(with = FlexibleNullableStringSerializer::class) val gatewayIp: String? = null,
    @SerialName("dns_servers") @Serializable(with = FlexibleStringListSerializer::class) val dnsServers: List<String> = emptyList(),
    @SerialName("network_interfaces") @Serializable(with = FlexibleNetworkInterfaceListSerializer::class) val networkInterfaces: List<PatchmonNetworkInterface> = emptyList()
)

@Serializable
data class PatchmonNetworkInterface(
    @Serializable(with = FlexibleStringSerializer::class) val name: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val ip: String = "",
    @Serializable(with = FlexibleStringSerializer::class) val mac: String = ""
)

@Serializable
data class PatchmonPackagesResponse(
    val host: PatchmonPackageHost? = null,
    val packages: List<PatchmonPackage> = emptyList(),
    val total: Int = 0
)

@Serializable
data class PatchmonPackageHost(
    val id: String = "",
    val hostname: String = "",
    @SerialName("friendly_name") val friendlyName: String = ""
)

@Serializable
data class PatchmonPackage(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val category: String? = null,
    @SerialName("current_version") val currentVersion: String? = null,
    @SerialName("available_version") val availableVersion: String? = null,
    @SerialName("needs_update") val needsUpdate: Boolean = false,
    @SerialName("is_security_update") val isSecurityUpdate: Boolean = false,
    @SerialName("last_checked") val lastChecked: String? = null
)

@Serializable
data class PatchmonReportsResponse(
    @SerialName("host_id") val hostId: String = "",
    val reports: List<PatchmonPackageReport> = emptyList(),
    val total: Int = 0
)

@Serializable
data class PatchmonPackageReport(
    val id: String,
    val status: String = "",
    val date: String? = null,
    @SerialName("total_packages") val totalPackages: Int = 0,
    @SerialName("outdated_packages") val outdatedPackages: Int = 0,
    @SerialName("security_updates") val securityUpdates: Int = 0,
    @SerialName("payload_kb") val payloadKb: Double? = null,
    @SerialName("execution_time_seconds") val executionTimeSeconds: Double? = null,
    @SerialName("error_message") val errorMessage: String? = null
)

@Serializable
data class PatchmonAgentQueueResponse(
    @SerialName("host_id") val hostId: String = "",
    @SerialName("queue_status") val queueStatus: PatchmonQueueStatus = PatchmonQueueStatus(),
    @SerialName("job_history") val jobHistory: List<PatchmonAgentJob> = emptyList(),
    @SerialName("total_jobs") val totalJobs: Int = 0
)

@Serializable
data class PatchmonQueueStatus(
    val waiting: Int = 0,
    val active: Int = 0,
    val delayed: Int = 0,
    val failed: Int = 0
)

@Serializable
data class PatchmonAgentJob(
    val id: String,
    @SerialName("job_id") val jobId: String? = null,
    @SerialName("job_name") val jobName: String = "",
    val status: String = "",
    val attempt: Int = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("completed_at") val completedAt: String? = null,
    @SerialName("error_message") val errorMessage: String? = null,
    val output: String? = null
)

@Serializable
data class PatchmonNotesResponse(
    @SerialName("host_id") val hostId: String = "",
    val notes: String? = null
)

@Serializable
data class PatchmonIntegrationsResponse(
    @SerialName("host_id") val hostId: String = "",
    val integrations: Map<String, PatchmonIntegrationStatus> = emptyMap()
)

@Serializable
data class PatchmonIntegrationStatus(
    val enabled: Boolean = false,
    @SerialName("containers_count") val containersCount: Int? = null,
    @SerialName("volumes_count") val volumesCount: Int? = null,
    @SerialName("networks_count") val networksCount: Int? = null,
    val description: String? = null
)

@Serializable
data class PatchmonDeleteResponse(
    val message: String? = null,
    val deleted: PatchmonDeletedHost? = null
)

@Serializable
data class PatchmonDeletedHost(
    val id: String,
    @SerialName("friendly_name") val friendlyName: String? = null,
    val hostname: String? = null
)

private object FlexibleStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleString", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            return jsonDecoder.decodeJsonElement().asFlexibleString().orEmpty()
        }
        return runCatching { decoder.decodeString() }.getOrDefault("")
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private object FlexibleNullableStringSerializer : KSerializer<String?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleNullableString", PrimitiveKind.STRING).nullable

    override fun deserialize(decoder: Decoder): String? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            return if (element is JsonNull) null else element.asFlexibleString()
        }
        return runCatching { decoder.decodeString() }.getOrNull()
    }

    override fun serialize(encoder: Encoder, value: String?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeString(value)
        }
    }
}

private object FlexibleDoubleSerializer : KSerializer<Double> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleDouble", PrimitiveKind.DOUBLE)

    override fun deserialize(decoder: Decoder): Double {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            return jsonDecoder.decodeJsonElement().asFlexibleDouble() ?: 0.0
        }
        return runCatching { decoder.decodeDouble() }
            .recoverCatching { parseDoubleLoose(decoder.decodeString()) ?: 0.0 }
            .getOrDefault(0.0)
    }

    override fun serialize(encoder: Encoder, value: Double) {
        encoder.encodeDouble(value)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private object FlexibleNullableIntSerializer : KSerializer<Int?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("FlexibleNullableInt", PrimitiveKind.INT).nullable

    override fun deserialize(decoder: Decoder): Int? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            val element = jsonDecoder.decodeJsonElement()
            return if (element is JsonNull) null else element.asFlexibleInt()
        }
        return runCatching { decoder.decodeInt() }
            .recoverCatching { parseIntLoose(decoder.decodeString()) }
            .getOrNull()
    }

    override fun serialize(encoder: Encoder, value: Int?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeInt(value)
        }
    }
}

private object FlexibleStringListSerializer : KSerializer<List<String>> {
    private val delegate = ListSerializer(String.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<String> {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder != null) {
            return jsonDecoder.decodeJsonElement().asFlexibleStringList()
        }
        return runCatching { decoder.decodeSerializableValue(delegate) }.getOrDefault(emptyList())
    }

    override fun serialize(encoder: Encoder, value: List<String>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

@OptIn(ExperimentalSerializationApi::class)
private object FlexibleLoadAverageSerializer : KSerializer<PatchmonLoadAverage?> {
    override val descriptor: SerialDescriptor = PatchmonLoadAverage.serializer().descriptor.nullable

    override fun deserialize(decoder: Decoder): PatchmonLoadAverage? {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder == null) {
            return runCatching { decoder.decodeSerializableValue(PatchmonLoadAverage.serializer()) }.getOrNull()
        }

        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> null
            is JsonObject -> PatchmonLoadAverage(
                oneMin = element["1min"].asFlexibleDouble() ?: 0.0,
                fiveMin = element["5min"].asFlexibleDouble() ?: 0.0,
                fifteenMin = element["15min"].asFlexibleDouble() ?: 0.0
            )
            is JsonArray -> {
                val values = element.mapNotNull { it.asFlexibleDouble() }
                if (values.isEmpty()) null else PatchmonLoadAverage(
                    oneMin = values.getOrNull(0) ?: 0.0,
                    fiveMin = values.getOrNull(1) ?: 0.0,
                    fifteenMin = values.getOrNull(2) ?: 0.0
                )
            }
            is JsonPrimitive -> {
                val matches = DOUBLE_REGEX.findAll(element.content).mapNotNull { it.value.toDoubleOrNull() }.toList()
                if (matches.isEmpty()) null else PatchmonLoadAverage(
                    oneMin = matches.getOrNull(0) ?: 0.0,
                    fiveMin = matches.getOrNull(1) ?: 0.0,
                    fifteenMin = matches.getOrNull(2) ?: 0.0
                )
            }
        }
    }

    override fun serialize(encoder: Encoder, value: PatchmonLoadAverage?) {
        if (value == null) {
            encoder.encodeNull()
        } else {
            encoder.encodeSerializableValue(PatchmonLoadAverage.serializer(), value)
        }
    }
}

private object FlexibleDiskDetailsSerializer : KSerializer<List<PatchmonDiskDetail>> {
    private val delegate = ListSerializer(PatchmonDiskDetail.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<PatchmonDiskDetail> {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder == null) {
            return runCatching { decoder.decodeSerializableValue(delegate) }.getOrDefault(emptyList())
        }

        val json = jsonDecoder.json
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> emptyList()
            is JsonArray -> element.mapNotNull { item ->
                runCatching { json.decodeFromJsonElement(PatchmonDiskDetail.serializer(), item) }.getOrNull()
            }
            is JsonObject -> listOfNotNull(
                runCatching { json.decodeFromJsonElement(PatchmonDiskDetail.serializer(), element) }.getOrNull()
            )
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<PatchmonDiskDetail>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

private object FlexibleNetworkInterfaceListSerializer : KSerializer<List<PatchmonNetworkInterface>> {
    private val delegate = ListSerializer(PatchmonNetworkInterface.serializer())
    override val descriptor: SerialDescriptor = delegate.descriptor

    override fun deserialize(decoder: Decoder): List<PatchmonNetworkInterface> {
        val jsonDecoder = decoder as? JsonDecoder
        if (jsonDecoder == null) {
            return runCatching { decoder.decodeSerializableValue(delegate) }.getOrDefault(emptyList())
        }

        val json = jsonDecoder.json
        return when (val element = jsonDecoder.decodeJsonElement()) {
            is JsonNull -> emptyList()
            is JsonArray -> element.mapNotNull { item ->
                runCatching { json.decodeFromJsonElement(PatchmonNetworkInterface.serializer(), item) }.getOrNull()
            }
            is JsonObject -> listOfNotNull(
                runCatching { json.decodeFromJsonElement(PatchmonNetworkInterface.serializer(), element) }.getOrNull()
            )
            else -> emptyList()
        }
    }

    override fun serialize(encoder: Encoder, value: List<PatchmonNetworkInterface>) {
        encoder.encodeSerializableValue(delegate, value)
    }
}

private val INT_REGEX = Regex("-?\\d+")
private val DOUBLE_REGEX = Regex("-?\\d+(?:\\.\\d+)?")

private fun JsonElement?.asFlexibleString(): String? {
    val element = this ?: return null
    return when (element) {
        is JsonNull -> null
        is JsonPrimitive -> element.content
        is JsonArray -> {
            val values = element.mapNotNull { it.asFlexibleString()?.trim() }.filter { it.isNotEmpty() }
            values.joinToString(", ").ifEmpty { element.toString() }
        }
        is JsonObject -> {
            val prioritized = listOf("value", "text", "name", "label", "title", "ip", "address", "mac", "hostname")
            for (key in prioritized) {
                val value = element[key].asFlexibleString()?.trim()
                if (!value.isNullOrEmpty()) {
                    return value
                }
            }
            element.entries.firstNotNullOfOrNull { (_, value) ->
                value.asFlexibleString()?.trim()?.takeIf { it.isNotEmpty() }
            } ?: element.toString()
        }
    }
}

private fun JsonElement?.asFlexibleInt(): Int? {
    val element = this ?: return null
    return when (element) {
        is JsonNull -> null
        is JsonPrimitive -> parseIntLoose(element.content)
        is JsonArray -> element.firstNotNullOfOrNull { it.asFlexibleInt() }
        is JsonObject -> {
            val prioritized = listOf("value", "count", "total", "size", "id")
            for (key in prioritized) {
                val parsed = element[key].asFlexibleInt()
                if (parsed != null) {
                    return parsed
                }
            }
            parseIntLoose(element.toString())
        }
    }
}

private fun JsonElement?.asFlexibleDouble(): Double? {
    val element = this ?: return null
    return when (element) {
        is JsonNull -> null
        is JsonPrimitive -> parseDoubleLoose(element.content)
        is JsonArray -> element.firstNotNullOfOrNull { it.asFlexibleDouble() }
        is JsonObject -> {
            val prioritized = listOf("value", "avg", "mean", "load", "one", "five", "fifteen")
            for (key in prioritized) {
                val parsed = element[key].asFlexibleDouble()
                if (parsed != null) {
                    return parsed
                }
            }
            parseDoubleLoose(element.toString())
        }
    }
}

private fun JsonElement?.asFlexibleStringList(): List<String> {
    val element = this ?: return emptyList()
    return when (element) {
        is JsonNull -> emptyList()
        is JsonPrimitive -> listOfNotNull(element.asFlexibleString()?.takeIf { it.isNotBlank() })
        is JsonArray -> element.flatMap { item ->
            when (item) {
                is JsonArray -> item.asFlexibleStringList()
                else -> listOfNotNull(item.asFlexibleString()?.takeIf { it.isNotBlank() })
            }
        }
        is JsonObject -> {
            val prioritized = listOf("dns", "dns_servers", "servers", "items", "values")
            for (key in prioritized) {
                val parsed = element[key].asFlexibleStringList()
                if (parsed.isNotEmpty()) {
                    return parsed
                }
            }
            listOfNotNull(element.asFlexibleString()?.takeIf { it.isNotBlank() })
        }
    }
}

private fun parseIntLoose(raw: String?): Int? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    text.toIntOrNull()?.let { return it }
    text.toDoubleOrNull()?.let { return it.toInt() }
    val match = INT_REGEX.find(text)?.value ?: return null
    return match.toIntOrNull()
}

private fun parseDoubleLoose(raw: String?): Double? {
    val text = raw?.trim().orEmpty()
    if (text.isEmpty()) return null
    text.toDoubleOrNull()?.let { return it }
    val match = DOUBLE_REGEX.find(text)?.value ?: return null
    return match.toDoubleOrNull()
}
