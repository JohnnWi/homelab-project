package com.homelab.app.data.repository

import android.net.Uri
import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.DockmonApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.Request

data class DockmonHost(
    val id: String,
    val name: String,
    val address: String?,
    val status: String?
) {
    val isOnline: Boolean
        get() = status.isNullOrBlank() ||
            status.equals("online", ignoreCase = true) ||
            status.equals("healthy", ignoreCase = true) ||
            status.equals("active", ignoreCase = true) ||
            status.equals("ok", ignoreCase = true)
}

data class DockmonContainer(
    val id: String,
    val hostId: String?,
    val name: String,
    val image: String,
    val state: String,
    val status: String,
    val autoRestart: Boolean,
    val updateAvailable: Boolean,
    val latestImage: String?,
    val portsSummary: String?
) {
    val isRunning: Boolean
        get() = state.equals("running", ignoreCase = true) || status.contains("up", ignoreCase = true)
}

data class DockmonDashboardData(
    val hosts: List<DockmonHost>,
    val containers: List<DockmonContainer>
) {
    val runningContainers: Int get() = containers.count { it.isRunning }
    val updateCount: Int get() = containers.count { it.updateAvailable }
    val autoRestartCount: Int get() = containers.count { it.autoRestart }
}

data class DockmonActionResult(
    val success: Boolean,
    val message: String?
)

@Singleton
class DockmonRepository @Inject constructor(
    private val api: DockmonApi,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(
        url: String,
        apiKey: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) {
        require(apiKey.trim().isNotBlank()) { "DockMon API key is required." }
        val baseCandidates = listOf(cleanUrl(url), cleanOptionalUrl(fallbackUrl))
            .filterNotNull()
            .distinct()

        var lastError: Exception? = null
        for (base in baseCandidates) {
            try {
                validateKey(base, apiKey, allowSelfSigned)
                return
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("DockMon authentication failed.")
    }

    suspend fun getDashboard(instanceId: String, hostId: String? = null): DockmonDashboardData = coroutineScope {
        val hostsDeferred = async { getHosts(instanceId) }
        val containersDeferred = async { getContainers(instanceId, hostId) }
        DockmonDashboardData(
            hosts = hostsDeferred.await(),
            containers = containersDeferred.await()
        )
    }

    suspend fun getHosts(instanceId: String): List<DockmonHost> {
        return parseHosts(api.getHosts(instanceId = instanceId))
    }

    suspend fun getContainers(instanceId: String, hostId: String? = null): List<DockmonContainer> {
        return parseContainers(api.getContainers(instanceId = instanceId, hostId = hostId?.takeIf { it.isNotBlank() }))
    }

    suspend fun getSummary(instanceId: String): DockmonDashboardData {
        return getDashboard(instanceId = instanceId)
    }

    suspend fun getContainerLogs(instanceId: String, containerId: String, tail: Int = 200): String {
        return api.getContainerLogs(
            containerId = Uri.encode(containerId),
            instanceId = instanceId,
            tail = tail
        ).string()
    }

    suspend fun restartContainer(instanceId: String, containerId: String): DockmonActionResult {
        val response = api.restartContainer(
            containerId = Uri.encode(containerId),
            instanceId = instanceId
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("DockMon restart failed (${response.code()}).")
        }
        return parseAction(response.body(), fallback = "Container restart requested.")
    }

    suspend fun updateContainer(instanceId: String, containerId: String, image: String?): DockmonActionResult {
        val body = image?.trim()?.takeIf { it.isNotBlank() }?.let { mapOf("image" to it) } ?: emptyMap()
        val response = api.updateContainer(
            containerId = Uri.encode(containerId),
            body = body,
            instanceId = instanceId
        )
        if (!response.isSuccessful) {
            throw IllegalStateException("DockMon update failed (${response.code()}).")
        }
        return parseAction(response.body(), fallback = "Container update requested.")
    }

    private suspend fun validateKey(
        baseUrl: String,
        apiKey: String,
        allowSelfSigned: Boolean
    ) = withContext(Dispatchers.IO) {
        val token = apiKey.trim().let { raw ->
            if (raw.startsWith("bearer ", ignoreCase = true)) raw.substring(7).trim() else raw
        }
        val request = Request.Builder()
            .url("$baseUrl/api/hosts")
            .get()
            .addHeader("Accept", "application/json")
            .addHeader("Authorization", "Bearer $token")
            .build()

        tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
            .newCall(request)
            .execute()
            .use { response ->
                when (response.code) {
                    in 200..399 -> Unit
                    401, 403 -> throw IllegalStateException("Invalid DockMon API key.")
                    else -> throw IllegalStateException("DockMon returned HTTP ${response.code}.")
                }
            }
    }

    private fun parseHosts(element: JsonElement): List<DockmonHost> {
        return extractObjectArray(element, "hosts", "data", "items", "results").mapIndexedNotNull { index, obj ->
            val id = obj.string("id", "host_id", "hostId", "uuid", "name").ifBlank { "host-$index" }
            val name = obj.string("name", "hostname", "label", "id").ifBlank { id }
            DockmonHost(
                id = id,
                name = name,
                address = obj.stringOrNull("address", "url", "endpoint", "ip"),
                status = obj.stringOrNull("status", "state", "health")
            )
        }
    }

    private fun parseContainers(element: JsonElement): List<DockmonContainer> {
        return extractObjectArray(element, "containers", "data", "items", "results").mapIndexedNotNull { index, obj ->
            val id = obj.string("id", "container_id", "containerId", "Id", "name").ifBlank { "container-$index" }
            val name = obj.string("name", "Names", "container_name", "containerName").trimStart('/').ifBlank { id.take(12) }
            val state = obj.string("state", "State", "status", "Status")
            val status = obj.string("status", "Status", "state", "State")
            DockmonContainer(
                id = id,
                hostId = obj.stringOrNull("host_id", "hostId", "host", "node_id", "nodeId"),
                name = name,
                image = obj.string("image", "Image", "current_image", "currentImage").ifBlank { "-" },
                state = state,
                status = status,
                autoRestart = obj.boolean("auto_restart", "autoRestart", "restart", "restart_enabled", "restartEnabled"),
                updateAvailable = obj.boolean("update_available", "updateAvailable", "has_update", "hasUpdate", "outdated"),
                latestImage = obj.stringOrNull("latest_image", "latestImage", "target_image", "targetImage"),
                portsSummary = parsePorts(obj)
            )
        }
    }

    private fun parseAction(element: JsonElement?, fallback: String): DockmonActionResult {
        val obj = element?.let { unwrapPrimaryObject(it) }
        val success = obj?.boolean("success", "ok") ?: true
        val message = obj?.stringOrNull("message", "detail", "status") ?: fallback
        return DockmonActionResult(success = success, message = message)
    }

    private fun parsePorts(obj: JsonObject): String? {
        obj.stringOrNull("ports", "Ports", "ports_summary", "portsSummary")?.let { return it }
        val ports = obj["ports"] ?: obj["Ports"] ?: return null
        return when (ports) {
            is JsonArray -> ports.mapNotNull { item ->
                when (item) {
                    is JsonPrimitive -> item.contentOrNull
                    is JsonObject -> {
                        val privatePort = item.string("private", "privatePort", "containerPort")
                        val publicPort = item.string("public", "publicPort", "hostPort")
                        when {
                            publicPort.isNotBlank() && privatePort.isNotBlank() -> "$publicPort:$privatePort"
                            privatePort.isNotBlank() -> privatePort
                            else -> item.string("value", "port")
                        }
                    }
                    else -> null
                }
            }.filter { it.isNotBlank() }.joinToString(", ").ifBlank { null }
            is JsonObject -> ports.values.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.joinToString(", ").ifBlank { null }
            else -> null
        }
    }

    private fun unwrapPrimaryObject(element: JsonElement): JsonObject {
        return when (element) {
            is JsonObject -> element
            is JsonArray -> JsonObject(mapOf("items" to element))
            else -> JsonObject(emptyMap())
        }
    }

    private fun extractObjectArray(element: JsonElement, vararg keys: String): List<JsonObject> {
        when (element) {
            is JsonArray -> return element.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                for (key in keys) {
                    when (val candidate = element[key]) {
                        is JsonArray -> return candidate.mapNotNull { it as? JsonObject }
                        is JsonObject -> {
                            val nested = extractObjectArray(candidate, *keys)
                            if (nested.isNotEmpty()) return nested
                        }
                        else -> Unit
                    }
                }
                if (element.isNotEmpty() && element.values.all { it is JsonObject }) {
                    return element.values.mapNotNull { it as? JsonObject }
                }
            }
            else -> Unit
        }
        return emptyList()
    }

    private fun JsonObject.string(vararg keys: String): String {
        return stringOrNull(*keys).orEmpty()
    }

    private fun JsonObject.stringOrNull(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key] ?: continue
            when (value) {
                is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                is JsonObject -> value.stringOrNull("name", "id", "value")?.let { return it }
                is JsonArray -> value.firstOrNull()?.let { first ->
                    when (first) {
                        is JsonPrimitive -> first.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                        is JsonObject -> first.stringOrNull("name", "id", "value")?.let { return it }
                        else -> Unit
                    }
                }
                is JsonNull -> Unit
            }
        }
        return null
    }

    private fun JsonObject.boolean(vararg keys: String): Boolean {
        for (key in keys) {
            val value = this[key] ?: continue
            when (value) {
                is JsonPrimitive -> {
                    value.booleanOrNull?.let { return it }
                    value.intOrNull?.let { return it != 0 }
                    val text = value.contentOrNull?.lowercase()?.trim()
                    if (text in setOf("true", "yes", "enabled", "available", "1")) return true
                    if (text in setOf("false", "no", "disabled", "0")) return false
                }
                else -> Unit
            }
        }
        return false
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
        val value = raw?.trim().orEmpty()
        return if (value.isBlank()) null else cleanUrl(value)
    }
}
