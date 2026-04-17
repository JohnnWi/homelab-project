package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.PlexApi
import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.dto.plex.PlexDashboardData
import com.homelab.app.data.remote.dto.plex.PlexHistoryItem
import com.homelab.app.data.remote.dto.plex.PlexLibrary
import com.homelab.app.data.remote.dto.plex.PlexRecentItem
import com.homelab.app.data.remote.dto.plex.PlexServerInfo
import com.homelab.app.data.remote.dto.plex.PlexSession
import com.homelab.app.data.remote.dto.plex.PlexStats
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
import okhttp3.Request

@Singleton
class PlexRepository @Inject constructor(
    private val api: PlexApi,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(url: String, apiKey: String, allowSelfSigned: Boolean = false) {
        withContext(Dispatchers.IO) {
            val clean = cleanUrl(url)
            val key = apiKey.trim()
            val request = Request.Builder()
                .url("$clean/identity")
                .addHeader("X-Plex-Token", key)
                .addHeader("Accept", "application/json")
                .build()

            tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Plex authentication failed")
                }
            }
        }
    }

    suspend fun getDashboard(instanceId: String): PlexDashboardData = coroutineScope {
        val serverInfoDef = async { getServerInfo(instanceId) }
        val librariesDef = async { getLibrariesWithSizes(instanceId) }
        val activeSessionsDef = async { getActiveSessions(instanceId) }
        val recentlyAddedDef = async { getRecentlyAdded(instanceId) }
        val watchHistoryDef = async { getWatchHistory(instanceId) }

        val libs = librariesDef.await()
        var totalItems = 0
        var totalMovies = 0
        var totalShows = 0
        var totalEpisodes = 0
        var totalMusic = 0
        var totalPhotos = 0

        libs.forEach { lib ->
            totalItems += lib.itemCount
            when (lib.type) {
                "movie" -> totalMovies += lib.itemCount
                "show" -> {
                    totalShows += lib.itemCount
                    totalEpisodes += lib.episodeCount
                }
                "artist" -> totalMusic += lib.itemCount
                "photo" -> totalPhotos += lib.itemCount
            }
        }

        val stats = PlexStats(
            totalItems = totalItems,
            totalMovies = totalMovies,
            totalShows = totalShows,
            totalEpisodes = totalEpisodes,
            totalMusic = totalMusic,
            totalPhotos = totalPhotos
        )

        PlexDashboardData(
            serverInfo = serverInfoDef.await(),
            libraries = libs,
            stats = stats,
            activeSessions = activeSessionsDef.await(),
            recentlyAdded = recentlyAddedDef.await(),
            watchHistory = watchHistoryDef.await()
        )
    }

    private suspend fun getServerInfo(instanceId: String): PlexServerInfo {
        val payload = api.getServerInfo(instanceId = instanceId)
        val mc = payload["MediaContainer"]?.asObject() ?: return PlexServerInfo("Unknown", "Unknown", "Unknown")
        return PlexServerInfo(
            name = mc["friendlyName"]?.asString() ?: "Plex Server",
            platform = mc["platform"]?.asString() ?: "Unknown",
            version = mc["version"]?.asString() ?: "Unknown"
        )
    }

    private suspend fun getLibrariesWithSizes(instanceId: String): List<PlexLibrary> {
        val payload = api.getLibraries(instanceId = instanceId)
        val mc = payload["MediaContainer"]?.asObject() ?: return emptyList()
        val dirs = mc["Directory"]?.asArray() ?: return emptyList()

        val libs = dirs.mapNotNull { d ->
            val obj = d.asObject() ?: return@mapNotNull null
            val type = obj["type"]?.asString() ?: return@mapNotNull null
            PlexLibrary(
                id = obj["uuid"]?.asString() ?: "",
                key = obj["key"]?.asString() ?: "",
                title = obj["title"]?.asString() ?: "Unknown",
                type = type,
                scanner = obj["scanner"]?.asString() ?: "",
                agent = obj["agent"]?.asString() ?: "",
                createdAt = obj["createdAt"]?.asLong() ?: 0L,
                updatedAt = obj["updatedAt"]?.asLong(),
                sfSymbol = symbolForLib(type)
            )
        }.sortedBy { it.title.lowercase() }

        return coroutineScope {
            val defs = libs.map { lib ->
                async {
                    var finalCount = 0
                    var finalEpisodes = 0
                    var finalSize = 0L

                    try {
                        val sizePayload = api.getLibrarySize(key = lib.key, instanceId = instanceId)
                        val smc = sizePayload["MediaContainer"]?.asObject()

                        if (smc != null) {
                            finalCount = smc["totalSize"]?.asInt() ?: smc["size"]?.asInt() ?: 0
                            if (lib.type == "show") {
                                val epPayload = api.getLibrarySize(key = lib.key, type = 4, instanceId = instanceId)
                                val epMc = epPayload["MediaContainer"]?.asObject()
                                if (epMc != null) {
                                    finalEpisodes = epMc["totalSize"]?.asInt() ?: epMc["size"]?.asInt() ?: 0
                                }
                            }
                        }
                    } catch (e: Exception) {
                        // ignore size error
                    }

                    lib.copy(
                        itemCount = finalCount,
                        episodeCount = finalEpisodes,
                        totalSize = finalSize
                    )
                }
            }
            defs.map { it.await() }
        }
    }

    private suspend fun getActiveSessions(instanceId: String): List<PlexSession> {
        val payload = api.getActiveSessions(instanceId = instanceId)
        val mc = payload["MediaContainer"]?.asObject() ?: return emptyList()
        val metas = (mc["Metadata"] ?: mc["Video"] ?: mc["Track"])?.asArray() ?: return emptyList()

        return metas.mapNotNull { m ->
            val obj = m.asObject() ?: return@mapNotNull null
            val type = obj["type"]?.asString() ?: "unknown"
            
            val user = obj["User"]?.asObject()
            val player = obj["Player"]?.asObject()
            val sessionParams = obj["Session"]?.asObject()
            val mediaArr = obj["Media"]?.asArray()
            val mediaObj = mediaArr?.firstOrNull()?.asObject()
            val partArr = mediaObj?.get("Part")?.asArray()
            val partObj = partArr?.firstOrNull()?.asObject()
            val streamArr = partObj?.get("Stream")?.asArray()
            
            val isTranscoding = sessionParams?.get("location")?.asString() == "lan" &&
                (mediaObj?.get("videoProfile")?.asString() != null)
                // Actually in Plex it's TranscodeSession that indicates this reliably:
            val transcodeSession = obj["TranscodeSession"]?.asObject()
            val actualTranscoding = transcodeSession != null && transcodeSession["videoDecision"]?.asString() == "transcode"

            var bandwidth = 0L
            if (sessionParams != null) {
                bandwidth = sessionParams["bandwidth"]?.asLong() ?: 0L
            }

            PlexSession(
                id = sessionParams?.get("id")?.asString() ?: obj["sessionKey"]?.asString() ?: "",
                title = obj["title"]?.asString() ?: "Unknown",
                parentTitle = obj["parentTitle"]?.asString() ?: "",
                grandparentTitle = obj["grandparentTitle"]?.asString() ?: "",
                type = type,
                username = user?.get("title")?.asString() ?: "Unknown",
                playerPlatform = player?.get("title")?.asString() ?: player?.get("platform")?.asString() ?: "Unknown",
                playerState = player?.get("state")?.asString() ?: "stopped",
                isLocal = player?.get("local")?.asBoolean() ?: true,
                resolution = mediaObj?.get("videoResolution")?.asString() ?: "",
                isTranscoding = actualTranscoding,
                progressRatio = 0.0, // To do math if needed using viewOffset / duration
                bandwidth = bandwidth,
                posterUrl = obj["thumb"]?.asString() ?: "",
                duration = obj["duration"]?.asLong() ?: 0L,
                viewOffset = obj["viewOffset"]?.asLong() ?: 0L
            ).let { s ->
                val ratio = if (s.duration > 0) s.viewOffset.toDouble() / s.duration.toDouble() else 0.0
                s.copy(progressRatio = ratio.coerceIn(0.0, 1.0))
            }
        }
    }

    private suspend fun getRecentlyAdded(instanceId: String): List<PlexRecentItem> {
        val payload = api.getRecentlyAdded(instanceId = instanceId)
        val mc = payload["MediaContainer"]?.asObject() ?: return emptyList()
        val metas = (mc["Metadata"] ?: mc["Video"] ?: mc["Directory"])?.asArray() ?: return emptyList()

        return metas.mapNotNull { m ->
            val obj = m.asObject() ?: return@mapNotNull null
            val type = obj["type"]?.asString() ?: "unknown"
            PlexRecentItem(
                id = obj["ratingKey"]?.asString() ?: "",
                title = obj["title"]?.asString() ?: "Unknown",
                parentTitle = obj["parentTitle"]?.asString() ?: "",
                grandparentTitle = obj["grandparentTitle"]?.asString() ?: "",
                type = type,
                year = obj["year"]?.asInt(),
                addedAt = obj["addedAt"]?.asLong()?.times(1000) ?: 0L, // ms
                sfSymbol = symbolForLib(type)
            )
        }
    }

    private suspend fun getWatchHistory(instanceId: String): List<PlexHistoryItem> {
        val payload = api.getWatchHistory(instanceId = instanceId)
        val mc = payload["MediaContainer"]?.asObject() ?: return emptyList()
        val metas = (mc["Metadata"] ?: mc["Video"] ?: mc["Track"])?.asArray() ?: return emptyList()

        return metas.mapNotNull { m ->
            val obj = m.asObject() ?: return@mapNotNull null
            val type = obj["type"]?.asString() ?: "unknown"
            PlexHistoryItem(
                id = obj["ratingKey"]?.asString() ?: "",
                title = obj["title"]?.asString() ?: "Unknown",
                parentTitle = obj["parentTitle"]?.asString() ?: "",
                grandparentTitle = obj["grandparentTitle"]?.asString() ?: "",
                type = type,
                viewedAt = obj["viewedAt"]?.asLong()?.times(1000) ?: 0L,
                username = obj["accountID"]?.asString() ?: "User", // Actually it's AccountID in history
                playerPlatform = obj["clientPlatform"]?.asString() ?: "Unknown Player"
            )
        }
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "") // remove trailing slash
    }

    private fun symbolForLib(type: String): String {
        return when (type) {
            "movie" -> "film.fill"
            "show", "episode" -> "tv.fill"
            "artist", "album", "track" -> "music.note"
            "photo" -> "photo.on.rectangle"
            else -> "folder.fill"
        }
    }
}

// JSON helpers
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
    return content.toIntOrNull() ?: content.toDoubleOrNull()?.toInt() ?: 0
}
private fun JsonElement?.asLong(): Long {
    val primitive = asPrimitive() ?: return 0L
    val content = primitive.content
    return content.toLongOrNull() ?: content.toDoubleOrNull()?.toLong() ?: 0L
}
private fun JsonElement?.asDouble(): Double {
    val primitive = asPrimitive() ?: return 0.0
    val content = primitive.content
    return content.toDoubleOrNull() ?: 0.0
}
private fun JsonElement?.asBoolean(): Boolean {
    val primitive = asPrimitive() ?: return false
    val content = primitive.content
    return content.toBooleanStrictOrNull() ?: (content == "1")
}
