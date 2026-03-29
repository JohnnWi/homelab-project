package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.ProxmoxApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class ProxmoxDashboardData(
    val nodes: List<ProxmoxNode>,
    val resources: List<ProxmoxResource>,
    val totalVMs: Int,
    val runningVMs: Int,
    val totalContainers: Int,
    val runningContainers: Int
)

data class ProxmoxNode(
    val node: String,
    val status: String,
    val cpuUsage: Double,
    val memoryUsed: Long,
    val memoryTotal: Long,
    val uptime: Long
)

data class ProxmoxResource(
    val id: String,
    val name: String,
    val type: String,
    val status: String,
    val node: String,
    val cpuUsage: Double,
    val memoryUsed: Long,
    val memoryTotal: Long
)

@Singleton
class ProxmoxRepository @Inject constructor(
    private val api: ProxmoxApi,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticate(url: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val key = apiKey.trim()
            val request = Request.Builder()
                .url("$clean/api2/json/nodes")
                .addHeader("Authorization", "PVEAPIToken=$key")
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Proxmox authentication failed")
                }
            }
        }
    }

    suspend fun getDashboard(instanceId: String): ProxmoxDashboardData = coroutineScope {
        val nodesDef = async { api.getNodes(instanceId = instanceId) }
        val resourcesDef = async { api.getClusterResources(instanceId = instanceId) }

        val nodesResponse = nodesDef.await()
        val resourcesResponse = resourcesDef.await()

        val nodesArray = (nodesResponse["data"] as? JsonArray) ?: JsonArray(emptyList())
        val resourcesArray = (resourcesResponse["data"] as? JsonArray) ?: JsonArray(emptyList())

        val nodes = nodesArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            ProxmoxNode(
                node = obj.str("node") ?: return@mapNotNull null,
                status = obj.str("status") ?: "unknown",
                cpuUsage = obj.double("cpu"),
                memoryUsed = obj.long("mem"),
                memoryTotal = obj.long("maxmem"),
                uptime = obj.long("uptime")
            )
        }

        val resources = resourcesArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val type = obj.str("type") ?: return@mapNotNull null
            if (type != "qemu" && type != "lxc") return@mapNotNull null
            ProxmoxResource(
                id = obj.str("id") ?: return@mapNotNull null,
                name = obj.str("name") ?: "Unknown",
                type = type,
                status = obj.str("status") ?: "unknown",
                node = obj.str("node") ?: "unknown",
                cpuUsage = obj.double("cpu"),
                memoryUsed = obj.long("mem"),
                memoryTotal = obj.long("maxmem")
            )
        }

        val vms = resources.filter { it.type == "qemu" }
        val containers = resources.filter { it.type == "lxc" }

        ProxmoxDashboardData(
            nodes = nodes,
            resources = resources,
            totalVMs = vms.size,
            runningVMs = vms.count { it.status == "running" },
            totalContainers = containers.size,
            runningContainers = containers.count { it.status == "running" }
        )
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "")
    }
}

private fun JsonObject.str(key: String): String? {
    val element = this[key] ?: return null
    val primitive = element as? JsonPrimitive ?: return null
    val content = primitive.content
    return if (content.equals("null", ignoreCase = true)) null else content
}

private fun JsonObject.long(key: String): Long {
    val element = this[key] ?: return 0L
    val primitive = element as? JsonPrimitive ?: return 0L
    return primitive.content.toLongOrNull() ?: primitive.content.toDoubleOrNull()?.toLong() ?: 0L
}

private fun JsonObject.double(key: String): Double {
    val element = this[key] ?: return 0.0
    val primitive = element as? JsonPrimitive ?: return 0.0
    return primitive.content.toDoubleOrNull() ?: 0.0
}
