package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.KomodoApi
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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class KomodoResourceSummary(
    val total: Int,
    val running: Int,
    val stopped: Int,
    val healthy: Int,
    val unhealthy: Int,
    val unknown: Int
)

data class KomodoContainerSummary(
    val total: Int,
    val running: Int,
    val stopped: Int,
    val unhealthy: Int,
    val exited: Int,
    val paused: Int,
    val restarting: Int,
    val unknown: Int
)

data class KomodoDashboardData(
    val version: String?,
    val servers: KomodoResourceSummary,
    val deployments: KomodoResourceSummary,
    val stacks: KomodoResourceSummary,
    val containers: KomodoContainerSummary
)

data class KomodoSummary(
    val runningContainers: Int,
    val totalContainers: Int,
    val deployments: Int,
    val servers: Int
)

@Singleton
class KomodoRepository @Inject constructor(
    private val api: KomodoApi,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(
        url: String,
        apiKey: String,
        apiSecret: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) {
        require(apiKey.trim().isNotBlank()) { "Komodo API key is required." }
        require(apiSecret.trim().isNotBlank()) { "Komodo API secret is required." }

        val baseCandidates = listOf(cleanUrl(url), cleanOptionalUrl(fallbackUrl))
            .filterNotNull()
            .distinct()

        var lastError: Exception? = null
        for (base in baseCandidates) {
            try {
                validateCredentials(base, apiKey, apiSecret, allowSelfSigned)
                return
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Komodo authentication failed.")
    }

    suspend fun getDashboard(instanceId: String): KomodoDashboardData = coroutineScope {
        val version = async { api.getVersion(instanceId = instanceId) }
        val servers = async { api.getServersSummary(instanceId = instanceId) }
        val deployments = async { api.getDeploymentsSummary(instanceId = instanceId) }
        val stacks = async { api.getStacksSummary(instanceId = instanceId) }
        val containers = async { api.getDockerContainersSummary(instanceId = instanceId) }

        KomodoDashboardData(
            version = parseVersion(version.await()),
            servers = parseResourceSummary(servers.await()),
            deployments = parseResourceSummary(deployments.await()),
            stacks = parseResourceSummary(stacks.await()),
            containers = parseContainerSummary(containers.await())
        )
    }

    suspend fun getSummary(instanceId: String): KomodoSummary {
        val dashboard = getDashboard(instanceId)
        return KomodoSummary(
            runningContainers = dashboard.containers.running,
            totalContainers = dashboard.containers.total,
            deployments = dashboard.deployments.total,
            servers = dashboard.servers.total
        )
    }

    private suspend fun validateCredentials(
        baseUrl: String,
        apiKey: String,
        apiSecret: String,
        allowSelfSigned: Boolean
    ) = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("$baseUrl/read/GetVersion")
            .post("{}".toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .addHeader("X-Api-Key", apiKey.trim())
            .addHeader("X-Api-Secret", apiSecret.trim())
            .build()

        tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
            .newCall(request)
            .execute()
            .use { response ->
                when (response.code) {
                    in 200..399 -> Unit
                    401, 403 -> throw IllegalStateException("Invalid Komodo API credentials.")
                    else -> throw IllegalStateException("Komodo returned HTTP ${response.code}.")
                }
            }
    }

    private fun parseVersion(element: JsonElement): String? {
        val payload = unwrap(element)
        if (payload is JsonPrimitive) return payload.contentOrNull
        val obj = payload as? JsonObject ?: return null
        return obj.stringOrNull("version", "tag", "komodo_version", "komodoVersion")
            ?: firstString(obj, setOf("version", "tag"))
    }

    private fun parseResourceSummary(element: JsonElement): KomodoResourceSummary {
        val payload = unwrap(element)
        return KomodoResourceSummary(
            total = intValue(payload, "total", "count", "resources", "servers", "deployments", "stacks"),
            running = intValue(payload, "running", "online", "active", "ok", "up"),
            stopped = intValue(payload, "stopped", "offline", "disabled", "down", "not_deployed", "notDeployed"),
            healthy = intValue(payload, "healthy", "ok", "green"),
            unhealthy = intValue(payload, "unhealthy", "unreachable", "failed", "critical", "red"),
            unknown = intValue(payload, "unknown", "pending", "warning")
        )
    }

    private fun parseContainerSummary(element: JsonElement): KomodoContainerSummary {
        val payload = unwrap(element)
        return KomodoContainerSummary(
            total = intValue(payload, "total", "count", "containers"),
            running = intValue(payload, "running", "active", "up"),
            stopped = intValue(payload, "stopped", "paused", "created"),
            unhealthy = intValue(payload, "unhealthy", "dead"),
            exited = intValue(payload, "exited", "finished"),
            paused = intValue(payload, "paused"),
            restarting = intValue(payload, "restarting", "restarts"),
            unknown = intValue(payload, "unknown", "removing")
        )
    }

    private fun unwrap(element: JsonElement): JsonElement {
        if (element is JsonObject) {
            for (key in listOf("data", "response", "result", "summary", "stats")) {
                val nested = element[key]
                if (nested != null && nested !is JsonNull) return unwrap(nested)
            }
        }
        return element
    }

    private fun intValue(element: JsonElement, vararg keys: String): Int {
        val wanted = keys.map { it.lowercase() }.toSet()
        return findInts(element, wanted).sum().coerceAtLeast(0)
    }

    private fun findInts(element: JsonElement, keys: Set<String>): List<Int> {
        return when (element) {
            is JsonObject -> element.flatMap { (key, value) ->
                val direct = if (key.lowercase() in keys) listOfNotNull(value.asIntOrNull()) else emptyList()
                direct + findInts(value, keys)
            }
            is JsonArray -> element.flatMap { findInts(it, keys) }
            else -> emptyList()
        }
    }

    private fun firstString(obj: JsonObject, keys: Set<String>): String? {
        obj.forEach { (key, value) ->
            if (key.lowercase() in keys) value.asStringOrNull()?.let { return it }
            if (value is JsonObject) firstString(value, keys)?.let { return it }
        }
        return null
    }

    private fun JsonObject.stringOrNull(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key]?.asStringOrNull()
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private fun JsonElement.asStringOrNull(): String? {
        return (this as? JsonPrimitive)?.contentOrNull?.takeIf { it.isNotBlank() }
    }

    private fun JsonElement.asIntOrNull(): Int? {
        val primitive = this as? JsonPrimitive ?: return null
        return primitive.intOrNull ?: primitive.contentOrNull?.toDoubleOrNull()?.toInt()
    }

    private fun cleanUrl(url: String): String {
        return url.trim().removeSuffix("/")
    }

    private fun cleanOptionalUrl(url: String?): String? {
        return url?.trim()?.takeIf { it.isNotBlank() }?.removeSuffix("/")
    }
}
