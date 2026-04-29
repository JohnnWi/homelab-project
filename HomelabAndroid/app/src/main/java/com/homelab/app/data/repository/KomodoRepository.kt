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
import kotlinx.serialization.json.buildJsonObject
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

data class KomodoStackItem(
    val id: String,
    val name: String,
    val status: String,
    val server: String?,
    val project: String?,
    val updateAvailable: Boolean
)

data class KomodoStackService(
    val name: String,
    val image: String?,
    val containerName: String?,
    val status: String,
    val updateAvailable: Boolean
)

data class KomodoStackDetail(
    val stack: KomodoStackItem,
    val services: List<KomodoStackService>
)

enum class KomodoStackAction {
    DEPLOY,
    START,
    STOP,
    RESTART
}

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

    suspend fun getStacks(instanceId: String): List<KomodoStackItem> {
        val response = api.listStacks(body = listStacksBody(), instanceId = instanceId)
        return arrayPayload(response).mapNotNull { parseStackItem(it) }
    }

    suspend fun getStackDetail(instanceId: String, stackId: String): KomodoStackDetail {
        val body = stackBody(stackId)
        val stackResponse = api.getStack(body = body, instanceId = instanceId)
        val servicesResponse = api.listStackServices(body = body, instanceId = instanceId)
        val stack = parseStackItem(unwrap(stackResponse))
            ?: KomodoStackItem(
                id = stackId,
                name = stackId,
                status = "Unknown",
                server = null,
                project = null,
                updateAvailable = false
            )
        return KomodoStackDetail(
            stack = stack,
            services = arrayPayload(servicesResponse).mapNotNull { parseStackService(it) }
        )
    }

    suspend fun executeStackAction(instanceId: String, stackId: String, action: KomodoStackAction) {
        val body = when (action) {
            KomodoStackAction.DEPLOY -> stackActionBody(stackId, includeStopTime = true)
            KomodoStackAction.START,
            KomodoStackAction.STOP,
            KomodoStackAction.RESTART -> stackActionBody(stackId, includeStopTime = false)
        }
        when (action) {
            KomodoStackAction.DEPLOY -> api.deployStack(body = body, instanceId = instanceId)
            KomodoStackAction.START -> api.startStack(body = body, instanceId = instanceId)
            KomodoStackAction.STOP -> api.stopStack(body = body, instanceId = instanceId)
            KomodoStackAction.RESTART -> api.restartStack(body = body, instanceId = instanceId)
        }
    }

    private fun listStacksBody(): JsonObject = buildJsonObject {
        put("query", JsonObject(emptyMap()))
    }

    private fun stackBody(stackId: String): JsonObject = buildJsonObject {
        put("stack", JsonPrimitive(stackId))
    }

    private fun stackActionBody(stackId: String, includeStopTime: Boolean): JsonObject = buildJsonObject {
        put("stack", JsonPrimitive(stackId))
        put("services", JsonArray(emptyList()))
        if (includeStopTime) {
            put("stop_time", JsonNull)
        }
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

    private fun parseStackItem(element: JsonElement): KomodoStackItem? {
        val obj = unwrap(element) as? JsonObject ?: return null
        val info = obj["info"] as? JsonObject
        val config = obj["config"] as? JsonObject
        val id = obj.stringOrNull("id", "_id")
            ?: (obj["_id"] as? JsonObject)?.stringOrNull("\$oid")
            ?: obj.stringOrNull("name")
            ?: return null
        val name = obj.stringOrNull("name")
            ?: config?.stringOrNull("name", "project_name", "projectName")
            ?: id
        val status = info?.stringOrNull("state", "status")
            ?: obj.stringOrNull("state", "status")
            ?: config?.stringOrNull("state", "status")
            ?: "Unknown"
        return KomodoStackItem(
            id = id,
            name = name,
            status = status,
            server = info?.stringOrNull("server", "server_name", "serverName")
                ?: config?.stringOrNull("server", "server_id", "serverId"),
            project = info?.stringOrNull("project", "project_name", "projectName")
                ?: config?.stringOrNull("project_name", "projectName"),
            updateAvailable = info?.boolOrFalse("update_available", "updateAvailable", "updates_available", "updatesAvailable")
                ?: obj.boolOrFalse("update_available", "updateAvailable", "updates_available", "updatesAvailable")
                ?: false
        )
    }

    private fun parseStackService(element: JsonElement): KomodoStackService? {
        val obj = unwrap(element) as? JsonObject ?: return null
        val container = obj["container"] as? JsonObject
        val name = obj.stringOrNull("service", "name", "service_name", "serviceName")
            ?: container?.stringOrNull("name", "container_name", "containerName")
            ?: return null
        val status = container?.stringOrNull("state", "status")
            ?: obj.stringOrNull("state", "status")
            ?: "Unknown"
        return KomodoStackService(
            name = name,
            image = obj.stringOrNull("image") ?: container?.stringOrNull("image"),
            containerName = container?.stringOrNull("name", "container_name", "containerName"),
            status = status,
            updateAvailable = obj.boolOrFalse("update_available", "updateAvailable", "updates_available", "updatesAvailable") ?: false
        )
    }

    private fun arrayPayload(element: JsonElement): List<JsonElement> {
        return when (val payload = unwrap(element)) {
            is JsonArray -> payload.toList()
            is JsonObject -> {
                for (key in listOf("items", "resources", "stacks", "services", "containers", "data", "response", "result")) {
                    val nested = payload[key]
                    if (nested is JsonArray) return nested.toList()
                }
                emptyList()
            }
            else -> emptyList()
        }
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

    private fun JsonObject.boolOrFalse(vararg keys: String): Boolean? {
        for (key in keys) {
            val value = this[key]
            if (value is JsonPrimitive) {
                value.contentOrNull?.let { content ->
                    if (content.equals("true", ignoreCase = true)) return true
                    if (content.equals("false", ignoreCase = true)) return false
                }
            }
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
