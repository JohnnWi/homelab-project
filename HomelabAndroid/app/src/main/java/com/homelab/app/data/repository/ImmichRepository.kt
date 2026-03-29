package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.ImmichApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

data class ImmichDashboardData(
    val version: String,
    val totalPhotos: Int,
    val totalVideos: Int,
    val totalUsage: Long,
    val totalUsers: Int
)

@Singleton
class ImmichRepository @Inject constructor(
    private val api: ImmichApi,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticate(url: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val key = apiKey.trim()
            val request = Request.Builder()
                .url("$clean/api/server/info")
                .addHeader("x-api-key", key)
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Immich authentication failed")
                }
            }
        }
    }

    suspend fun getDashboard(instanceId: String): ImmichDashboardData = coroutineScope {
        val serverInfoDef = async { api.getServerInfo(instanceId = instanceId) }
        val statsDef = async {
            try {
                api.getServerStatistics(instanceId = instanceId)
            } catch (_: Exception) {
                null
            }
        }

        val serverInfo = serverInfoDef.await()
        val stats = statsDef.await()

        val version = serverInfo.str("version") ?: "Unknown"

        val totalPhotos = stats?.int("photos") ?: 0
        val totalVideos = stats?.int("videos") ?: 0
        val totalUsage = stats?.long("usage") ?: 0L
        val totalUsers = stats?.int("usageByUser")?.let { 0 }
            ?: (stats?.get("usageByUser") as? kotlinx.serialization.json.JsonArray)?.size
            ?: 0

        ImmichDashboardData(
            version = version,
            totalPhotos = totalPhotos,
            totalVideos = totalVideos,
            totalUsage = totalUsage,
            totalUsers = totalUsers
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

private fun JsonObject.int(key: String): Int {
    val element = this[key] ?: return 0
    val primitive = element as? JsonPrimitive ?: return 0
    return primitive.content.toIntOrNull() ?: 0
}

private fun JsonObject.long(key: String): Long {
    val element = this[key] ?: return 0L
    val primitive = element as? JsonPrimitive ?: return 0L
    return primitive.content.toLongOrNull() ?: 0L
}
