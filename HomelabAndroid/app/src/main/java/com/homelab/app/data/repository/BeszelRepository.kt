package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.BeszelApi
import com.homelab.app.data.remote.dto.beszel.BeszelContainerRecord
import com.homelab.app.data.remote.dto.beszel.BeszelContainerStatsRecord
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetails
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeszelRepository @Inject constructor(
    private val api: BeszelApi
) {
    suspend fun authenticate(url: String, email: String, password: String, allowSelfSigned: Boolean = false): String {
        val cleanUrl = url.trimEnd('/') + "/api/collections/users/auth-with-password"
        try {
            val response = api.authenticate(
                url = cleanUrl,
                allowSelfSigned = allowSelfSigned.toString(),
                credentials = mapOf("identity" to email, "password" to password)
            )
            return response.token
        } catch (e: Exception) {
            if (e is retrofit2.HttpException && e.code() == 400) {
                // Pocketbase often throws 400 for bad auth
                throw Exception("Authentication failed. Check your credentials and URL.")
            }
            throw e
        }
    }

    suspend fun getSystems(instanceId: String): List<BeszelSystem> {
        val response = api.getSystems(instanceId = instanceId)
        return response.items.sortedBy { it.name.lowercase() }
    }

    suspend fun getSystem(instanceId: String, id: String): BeszelSystem {
        return api.getSystem(instanceId = instanceId, id = id)
    }

    suspend fun getSystemDetails(instanceId: String, systemId: String): BeszelSystemDetails? {
        val filter = "system='$systemId'"
        val response = api.getSystemDetails(instanceId = instanceId, filter = filter, limit = 1)
        return response.items.firstOrNull()
    }

    suspend fun getSystemRecords(instanceId: String, systemId: String, limit: Int = 60): List<BeszelSystemRecord> {
        val filter = "system='$systemId'"
        val response = api.getSystemRecords(instanceId = instanceId, filter = filter, limit = limit)
        return response.items
    }

    suspend fun getSmartDevices(instanceId: String, systemId: String, limit: Int = 10): List<BeszelSmartDevice> {
        val filter = "system='$systemId'"
        val response = api.getSmartDevices(instanceId = instanceId, filter = filter, limit = limit)
        return response.items
    }

    suspend fun getContainers(instanceId: String, systemId: String): List<BeszelContainerRecord> {
        val filter = "system='$systemId'"
        val response = api.getContainers(instanceId = instanceId, filter = filter)
        return response.items
    }

    suspend fun getContainerStats(instanceId: String, systemId: String, limit: Int = 240): List<BeszelContainerStatsRecord> {
        val filter = "system='$systemId'"
        val response = api.getContainerStats(instanceId = instanceId, filter = filter, limit = limit)
        return response.items
    }

    suspend fun getContainerLogs(instanceId: String, token: String, systemId: String, containerId: String): String {
        val response = api.getContainerLogs(
            instanceId = instanceId,
            authorization = "Bearer $token",
            systemId = systemId,
            containerId = containerId
        )
        val raw = response.string()
        return formatLogs(raw)
    }

    suspend fun getContainerInfo(instanceId: String, token: String, systemId: String, containerId: String): String {
        val response = api.getContainerInfo(
            instanceId = instanceId,
            authorization = "Bearer $token",
            systemId = systemId,
            containerId = containerId
        )
        val raw = response.string()
        return try {
            val json = org.json.JSONObject(raw)
            json.toString(2)
        } catch (_: Exception) {
            raw
        }
    }

    private fun formatLogs(raw: String): String {
        return try {
            val json = org.json.JSONObject(raw)
            val logs = json.optString("logs", "")
            if (logs.isNotEmpty()) {
                logs
                    .replace("\\n", "\n")
                    .replace("\\t", "\t")
                    .replace("\\\"", "\"")
                    .replace("\\/", "/")
            } else {
                json.toString(2)
            }
        } catch (_: Exception) {
            raw
        }
    }
}
