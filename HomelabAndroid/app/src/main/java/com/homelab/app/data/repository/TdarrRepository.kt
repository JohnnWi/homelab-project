package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.TdarrApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

data class TdarrDashboardData(
    val totalFiles: Int,
    val totalTranscodeCount: Int,
    val totalHealthCheckCount: Int,
    val sizeReduction: String,
    val nodes: List<TdarrNode>,
    val tdarrScore: String
)

data class TdarrNode(
    val id: String,
    val name: String,
    val workers: Int,
    val online: Boolean
)

@Singleton
class TdarrRepository @Inject constructor(
    private val api: TdarrApi,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticate(url: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val requestBuilder = Request.Builder()
                .url("$clean/api/v2/get-stats")
                .post("{}".toRequestBody("application/json".toMediaType()))
                .addHeader("Accept", "application/json")
                .addHeader("Content-Type", "application/json")

            if (apiKey.isNotBlank()) {
                requestBuilder.addHeader("x-api-key", apiKey.trim())
            }

            okHttpClient.newCall(requestBuilder.build()).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Tdarr authentication failed")
                }
            }
        }
    }

    suspend fun getDashboard(instanceId: String): TdarrDashboardData = coroutineScope {
        val statsDef = async { api.getStats(instanceId = instanceId) }
        val nodesDef = async {
            try {
                api.getNodes(instanceId = instanceId)
            } catch (_: Exception) {
                null
            }
        }

        val stats = statsDef.await()
        val nodesObj = nodesDef.await()

        val totalFiles = stats.int("totalFileCount")
        val totalTranscodeCount = stats.int("totalTranscodeCount")
        val totalHealthCheckCount = stats.int("totalHealthCheckCount")
        val sizeReduction = stats.str("sizeDiff") ?: "0 GB"
        val tdarrScore = stats.str("tdarrScore") ?: "N/A"

        val nodes = nodesObj?.entries?.mapNotNull { (key, value) ->
            val nodeObj = value as? JsonObject ?: return@mapNotNull null
            TdarrNode(
                id = key,
                name = nodeObj.str("nodeName") ?: key,
                workers = nodeObj.int("workers"),
                online = nodeObj.str("online")?.toBooleanStrictOrNull() ?: true
            )
        } ?: emptyList()

        TdarrDashboardData(
            totalFiles = totalFiles,
            totalTranscodeCount = totalTranscodeCount,
            totalHealthCheckCount = totalHealthCheckCount,
            sizeReduction = sizeReduction,
            nodes = nodes,
            tdarrScore = tdarrScore
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
    return primitive.content.toIntOrNull() ?: primitive.content.toDoubleOrNull()?.toInt() ?: 0
}
