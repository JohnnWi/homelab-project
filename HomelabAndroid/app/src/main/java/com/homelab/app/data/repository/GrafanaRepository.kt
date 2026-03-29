package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.GrafanaApi
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

data class GrafanaDashboardData(
    val version: String,
    val database: String,
    val commit: String,
    val dashboardCount: Int,
    val alertCount: Int,
    val firingAlerts: Int,
    val dashboards: List<GrafanaDashboardItem>,
    val alerts: List<GrafanaAlert>
)

data class GrafanaDashboardItem(
    val id: Int,
    val uid: String,
    val title: String,
    val type: String,
    val uri: String?,
    val tags: List<String>
)

data class GrafanaAlert(
    val name: String,
    val state: String,
    val severity: String?
)

@Singleton
class GrafanaRepository @Inject constructor(
    private val api: GrafanaApi,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticate(url: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val key = apiKey.trim()
            val bearerToken = if (key.startsWith("bearer ", ignoreCase = true)) key else "Bearer $key"
            val request = Request.Builder()
                .url("$clean/api/health")
                .addHeader("Authorization", bearerToken)
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Grafana authentication failed")
                }
            }
        }
    }

    suspend fun getDashboard(instanceId: String): GrafanaDashboardData = coroutineScope {
        val healthDef = async { api.getHealth(instanceId = instanceId) }
        val dashboardsDef = async { api.searchDashboards(instanceId = instanceId) }
        val alertsDef = async {
            try {
                api.getAlerts(instanceId = instanceId)
            } catch (_: Exception) {
                JsonArray(emptyList())
            }
        }

        val health = healthDef.await()
        val dashboardsArray = dashboardsDef.await()
        val alertsArray = alertsDef.await()

        val version = health.str("version") ?: "Unknown"
        val database = health.str("database") ?: "Unknown"
        val commit = health.str("commit") ?: "Unknown"

        val dashboards = dashboardsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            GrafanaDashboardItem(
                id = obj.int("id"),
                uid = obj.str("uid") ?: "",
                title = obj.str("title") ?: "Untitled",
                type = obj.str("type") ?: "dash-db",
                uri = obj.str("uri"),
                tags = (obj["tags"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.content } ?: emptyList()
            )
        }

        val alerts = alertsArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val labels = obj["labels"] as? JsonObject
            GrafanaAlert(
                name = labels?.str("alertname") ?: obj.str("name") ?: "Unknown",
                state = obj.str("state") ?: (obj["status"] as? JsonObject)?.str("state") ?: "unknown",
                severity = labels?.str("severity")
            )
        }

        val firingAlerts = alerts.count { it.state.equals("active", ignoreCase = true) || it.state.equals("firing", ignoreCase = true) }

        GrafanaDashboardData(
            version = version,
            database = database,
            commit = commit,
            dashboardCount = dashboards.size,
            alertCount = alerts.size,
            firingAlerts = firingAlerts,
            dashboards = dashboards,
            alerts = alerts
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
