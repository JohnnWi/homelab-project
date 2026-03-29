package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.JellyfinApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class JellyfinDashboardData(
    val serverName: String,
    val version: String,
    val operatingSystem: String,
    val activeSessions: Int,
    val activeStreams: Int,
    val totalItems: Int,
    val sessions: List<JellyfinSession>
)

data class JellyfinSession(
    val id: String,
    val userName: String,
    val client: String,
    val deviceName: String,
    val nowPlayingTitle: String?,
    val nowPlayingType: String?,
    val isActive: Boolean
)

@Singleton
class JellyfinRepository @Inject constructor(
    private val api: JellyfinApi,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticate(url: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val key = apiKey.trim()
            val request = Request.Builder()
                .url("$clean/System/Info")
                .addHeader("X-Emby-Token", key)
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Jellyfin authentication failed")
                }
            }
        }
    }

    suspend fun getDashboard(instanceId: String): JellyfinDashboardData = coroutineScope {
        val systemInfoDef = async { api.getSystemInfo(instanceId = instanceId) }
        val sessionsDef = async { api.getSessions(instanceId = instanceId) }
        val itemsDef = async {
            try {
                api.getItems(instanceId = instanceId)
            } catch (_: Exception) {
                null
            }
        }

        val systemInfo = systemInfoDef.await()
        val sessionsArray = sessionsDef.await()
        val itemsResponse = itemsDef.await()

        val serverName = systemInfo.str("ServerName") ?: "Jellyfin"
        val version = systemInfo.str("Version") ?: "Unknown"
        val os = systemInfo.str("OperatingSystem") ?: "Unknown"

        val sessions = sessionsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val nowPlaying = obj["NowPlayingItem"] as? JsonObject
            JellyfinSession(
                id = obj.str("Id") ?: return@mapNotNull null,
                userName = obj.str("UserName") ?: "Unknown",
                client = obj.str("Client") ?: "Unknown",
                deviceName = obj.str("DeviceName") ?: "Unknown",
                nowPlayingTitle = nowPlaying?.str("Name"),
                nowPlayingType = nowPlaying?.str("Type"),
                isActive = nowPlaying != null
            )
        }

        val totalItems = itemsResponse?.get("TotalRecordCount")?.asInt() ?: 0

        JellyfinDashboardData(
            serverName = serverName,
            version = version,
            operatingSystem = os,
            activeSessions = sessions.size,
            activeStreams = sessions.count { it.isActive },
            totalItems = totalItems,
            sessions = sessions
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

private fun JsonElement?.asInt(): Int {
    val primitive = this as? JsonPrimitive ?: return 0
    return primitive.content.toIntOrNull() ?: 0
}
