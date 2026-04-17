package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.JellystatApi
import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.dto.jellystat.JellystatCountDuration
import com.homelab.app.data.remote.dto.jellystat.JellystatLibraryTypeViews
import com.homelab.app.data.remote.dto.jellystat.JellystatSeriesPoint
import com.homelab.app.data.remote.dto.jellystat.JellystatWatchSummary
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Request

@Singleton
class JellystatRepository @Inject constructor(
    private val api: JellystatApi,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(url: String, apiKey: String, allowSelfSigned: Boolean = false) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val key = apiKey.trim()
            val request = Request.Builder()
                .url("$clean/stats/getViewsByLibraryType?days=1")
                .addHeader("X-API-Token", key)
                .addHeader("Content-Type", "application/json")
                .build()

            tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Jellystat authentication failed")
                }
            }
        }
    }

    suspend fun getWatchSummary(instanceId: String, days: Int = 30): JellystatWatchSummary {
        val safeDays = normalizeDays(days)
        val viewsByTypeRaw = api.getViewsByLibraryType(instanceId = instanceId, days = safeDays)
        val viewsOverTimeRaw = api.getViewsOverTime(instanceId = instanceId, days = safeDays)

        val viewsByType = parseViewsByType(viewsByTypeRaw)
        val points = parseViewsOverTime(viewsOverTimeRaw)

        val totalDurationSeconds = points.sumOf { it.totalDurationSeconds }
        val totalHours = totalDurationSeconds / 3600.0
        val activeDays = points.count { it.totalViews > 0 || it.totalDurationSeconds > 0.0 }

        val libraryDurationSeconds = mutableMapOf<String, Double>()
        points.forEach { point ->
            point.breakdown.forEach { (library, metric) ->
                libraryDurationSeconds[library] = (libraryDurationSeconds[library] ?: 0.0) + metric.durationSeconds
            }
        }

        val topLibrary = libraryDurationSeconds.maxByOrNull { it.value }

        return JellystatWatchSummary(
            days = safeDays,
            totalHours = totalHours,
            totalViews = viewsByType.totalViews,
            activeDays = activeDays,
            topLibraryName = topLibrary?.key,
            topLibraryHours = (topLibrary?.value ?: 0.0) / 3600.0,
            viewsByType = viewsByType,
            points = points
        )
    }

    private fun parseViewsByType(payload: JsonObject): JellystatLibraryTypeViews {
        return JellystatLibraryTypeViews(
            audio = payload["Audio"].asInt(),
            movie = payload["Movie"].asInt(),
            series = payload["Series"].asInt(),
            other = payload["Other"].asInt()
        )
    }

    private fun parseViewsOverTime(payload: JsonObject): List<JellystatSeriesPoint> {
        val rawPoints = payload["stats"].asArray()

        return rawPoints.mapNotNull { entry ->
            val obj = entry.asObject() ?: return@mapNotNull null
            val key = obj["Key"].asString() ?: obj["key"].asString() ?: return@mapNotNull null

            val breakdown = mutableMapOf<String, JellystatCountDuration>()
            var totalViews = 0
            var totalDurationSeconds = 0.0

            obj.forEach { (field, value) ->
                if (field.equals("key", ignoreCase = true)) return@forEach

                val metric = value.asObject() ?: return@forEach
                val count = (metric["count"] ?: metric["Count"]).asInt()
                val durationMinutes = (metric["duration"] ?: metric["Duration"]).asDouble()
                val durationSeconds = durationMinutes * 60.0

                breakdown[field] = JellystatCountDuration(
                    count = count,
                    durationSeconds = durationSeconds
                )

                totalViews += count
                totalDurationSeconds += durationSeconds
            }

            JellystatSeriesPoint(
                key = key,
                totalViews = totalViews,
                totalDurationSeconds = totalDurationSeconds,
                breakdown = breakdown
            )
        }.sortedWith(compareBy<JellystatSeriesPoint> { parseDate(it.key) ?: Long.MAX_VALUE }.thenBy { it.key.lowercase() })
    }

    private fun normalizeDays(raw: Int): Int {
        return raw.coerceIn(1, 3650)
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "")
    }

    private fun parseDate(raw: String): Long? {
        val formats = listOf("MMM dd, yyyy", "MMM d, yyyy", "yyyy-MM-dd", "yyyy/MM/dd")
        formats.forEach { pattern ->
            val parser = SimpleDateFormat(pattern, Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
                isLenient = true
            }
            val parsed = parser.parse(raw)
            if (parsed != null) return parsed.time
        }
        return null
    }
}

private fun JsonElement?.asObject(): JsonObject? = this as? JsonObject

private fun JsonElement?.asArray(): JsonArray = this as? JsonArray ?: JsonArray(emptyList())

private fun JsonElement?.asPrimitive(): JsonPrimitive? = this as? JsonPrimitive

private fun JsonElement?.asString(): String? {
    val primitive = asPrimitive() ?: return null
    val content = primitive.content
    return if (content.equals("null", ignoreCase = true)) null else content
}

private fun JsonElement?.asInt(): Int {
    val primitive = asPrimitive() ?: return 0
    val content = primitive.content
    return content.toIntOrNull()
        ?: content.toDoubleOrNull()?.toInt()
        ?: 0
}

private fun JsonElement?.asDouble(): Double {
    val primitive = asPrimitive() ?: return 0.0
    return primitive.content.toDoubleOrNull() ?: 0.0
}
