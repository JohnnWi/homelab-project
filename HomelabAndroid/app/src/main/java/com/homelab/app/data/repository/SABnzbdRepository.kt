package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.SABnzbdApi
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

data class SABnzbdDashboardData(
    val version: String,
    val paused: Boolean,
    val speedBps: String,
    val sizeLeft: String,
    val timeLeft: String,
    val diskSpaceFree: String,
    val queueCount: Int,
    val historyCount: Int,
    val queueItems: List<SABnzbdQueueItem>,
    val historyItems: List<SABnzbdHistoryItem>
)

data class SABnzbdQueueItem(
    val id: String,
    val filename: String,
    val status: String,
    val percentage: Int,
    val sizeLeft: String,
    val timeLeft: String
)

data class SABnzbdHistoryItem(
    val id: String,
    val name: String,
    val status: String,
    val size: String,
    val completedAt: Long
)

@Singleton
class SABnzbdRepository @Inject constructor(
    private val api: SABnzbdApi,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticate(url: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val key = apiKey.trim()
            val request = Request.Builder()
                .url("$clean/api?mode=queue&output=json&apikey=$key")
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("SABnzbd authentication failed")
                }
            }
        }
    }

    suspend fun getDashboard(instanceId: String): SABnzbdDashboardData = coroutineScope {
        val queueDef = async { api.getQueue(instanceId = instanceId) }
        val historyDef = async { api.getHistory(instanceId = instanceId) }

        val queueResponse = queueDef.await()
        val historyResponse = historyDef.await()

        val queue = queueResponse["queue"] as? JsonObject ?: JsonObject(emptyMap())
        val history = historyResponse["history"] as? JsonObject ?: JsonObject(emptyMap())

        val version = queue.str("version") ?: "Unknown"
        val paused = queue.str("paused")?.toBooleanStrictOrNull() ?: false
        val speed = queue.str("speed") ?: "0 B/s"
        val sizeLeft = queue.str("sizeleft") ?: "0 B"
        val timeLeft = queue.str("timeleft") ?: "0:00:00"
        val diskFree = queue.str("diskspacef1") ?: "Unknown"

        val slots = (queue["slots"] as? JsonArray) ?: JsonArray(emptyList())
        val queueItems = slots.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            SABnzbdQueueItem(
                id = obj.str("nzo_id") ?: return@mapNotNull null,
                filename = obj.str("filename") ?: "Unknown",
                status = obj.str("status") ?: "Unknown",
                percentage = obj.str("percentage")?.toIntOrNull() ?: 0,
                sizeLeft = obj.str("sizeleft") ?: "0 B",
                timeLeft = obj.str("timeleft") ?: ""
            )
        }

        val historySlots = (history["slots"] as? JsonArray) ?: JsonArray(emptyList())
        val historyItems = historySlots.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            SABnzbdHistoryItem(
                id = obj.str("nzo_id") ?: return@mapNotNull null,
                name = obj.str("name") ?: "Unknown",
                status = obj.str("status") ?: "Unknown",
                size = obj.str("size") ?: "0 B",
                completedAt = obj.long("completed") * 1000L
            )
        }

        val historyCount = history.str("noofslots")?.toIntOrNull() ?: historyItems.size

        SABnzbdDashboardData(
            version = version,
            paused = paused,
            speedBps = speed,
            sizeLeft = sizeLeft,
            timeLeft = timeLeft,
            diskSpaceFree = diskFree,
            queueCount = queueItems.size,
            historyCount = historyCount,
            queueItems = queueItems,
            historyItems = historyItems
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
    return primitive.content.toLongOrNull() ?: 0L
}
