package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.PBSApi
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

data class PBSDashboardData(
    val datastores: List<PBSDatastore>,
    val datastoreUsage: List<PBSDatastoreUsage>
)

data class PBSDatastore(
    val name: String,
    val path: String?,
    val comment: String?
)

data class PBSDatastoreUsage(
    val store: String,
    val total: Long,
    val used: Long,
    val available: Long
)

@Singleton
class PBSRepository @Inject constructor(
    private val api: PBSApi,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticate(url: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val key = apiKey.trim()
            val request = Request.Builder()
                .url("$clean/api2/json/admin/datastore")
                .addHeader("Authorization", "PBSAPIToken=$key")
                .addHeader("Accept", "application/json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("PBS authentication failed")
                }
            }
        }
    }

    suspend fun getDashboard(instanceId: String): PBSDashboardData = coroutineScope {
        val datastoresDef = async { api.getDatastores(instanceId = instanceId) }
        val usageDef = async {
            try {
                api.getDatastoreUsage(instanceId = instanceId)
            } catch (_: Exception) {
                null
            }
        }

        val datastoresResponse = datastoresDef.await()
        val usageResponse = usageDef.await()

        val datastoresArray = (datastoresResponse["data"] as? JsonArray) ?: JsonArray(emptyList())
        val usageArray = (usageResponse?.get("data") as? JsonArray) ?: JsonArray(emptyList())

        val datastores = datastoresArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            PBSDatastore(
                name = obj.str("name") ?: return@mapNotNull null,
                path = obj.str("path"),
                comment = obj.str("comment")
            )
        }

        val usageItems = usageArray.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            PBSDatastoreUsage(
                store = obj.str("store") ?: return@mapNotNull null,
                total = obj.long("total"),
                used = obj.long("used"),
                available = obj.long("avail")
            )
        }

        PBSDashboardData(
            datastores = datastores,
            datastoreUsage = usageItems
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
