package com.homelab.app.data.repository

import android.net.Uri
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ServiceType
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

enum class MediaArrAction {
    QBITTORRENT_PAUSE_ALL,
    QBITTORRENT_RESUME_ALL,
    QBITTORRENT_TOGGLE_ALT_SPEED,
    QBITTORRENT_FORCE_RECHECK,
    QBITTORRENT_REANNOUNCE,
    QBITTORRENT_PAUSE_TORRENT,
    QBITTORRENT_RESUME_TORRENT,
    QBITTORRENT_RECHECK_TORRENT,
    QBITTORRENT_REANNOUNCE_TORRENT,
    QBITTORRENT_DELETE_TORRENT,
    QBITTORRENT_DELETE_TORRENT_WITH_DATA,
    RADARR_SEARCH_MISSING,
    RADARR_RSS_SYNC,
    RADARR_REFRESH_INDEX,
    RADARR_RESCAN,
    RADARR_DOWNLOADED_SCAN,
    RADARR_HEALTH_CHECK,
    SONARR_SEARCH_MISSING,
    SONARR_RSS_SYNC,
    SONARR_REFRESH_INDEX,
    SONARR_RESCAN,
    SONARR_DOWNLOADED_SCAN,
    SONARR_HEALTH_CHECK,
    LIDARR_SEARCH_MISSING,
    LIDARR_RSS_SYNC,
    LIDARR_REFRESH_INDEX,
    LIDARR_RESCAN,
    LIDARR_DOWNLOADED_SCAN,
    LIDARR_HEALTH_CHECK,
    JELLYSEERR_APPROVE_PENDING,
    JELLYSEERR_DECLINE_PENDING,
    JELLYSEERR_APPROVE_REQUEST,
    JELLYSEERR_DECLINE_REQUEST,
    JELLYSEERR_RUN_RECENT_SCAN,
    JELLYSEERR_RUN_FULL_SCAN,
    PROWLARR_TEST_INDEXERS,
    PROWLARR_SYNC_APPS,
    PROWLARR_HEALTH_CHECK,
    GLUETUN_RESTART_VPN,
    FLARESOLVERR_CREATE_SESSION,
    FLARESOLVERR_DESTROY_SESSION,
    RADARR_ADD_CONTENT,
    SONARR_ADD_CONTENT,
    LIDARR_ADD_CONTENT,
    JELLYSEERR_REQUEST_CONTENT
}

data class MediaArrActionResult(
    val action: MediaArrAction,
    val detail: String? = null
)

data class MediaArrMetric(
    val label: String,
    val value: String,
    val supporting: String? = null
)

data class QbittorrentTorrentItem(
    val hash: String,
    val name: String,
    val state: String,
    val progress: Double,
    val downloadedBytes: Long,
    val totalSizeBytes: Long,
    val downloadSpeedBytes: Long,
    val uploadSpeedBytes: Long,
    val etaSeconds: Long,
    val ratio: Double?,
    val seeds: Int?,
    val leechers: Int?,
    val category: String?,
    val tags: String?
)

data class JellyseerrRequestItem(
    val id: Int,
    val title: String,
    val status: String,
    val requestedBy: String?,
    val requestedAt: String?,
    val isPending: Boolean
)

data class MediaArrDownloadItem(
    val id: String,
    val title: String,
    val progress: Double?,
    val progressLabel: String?,
    val trailingLabel: String?,
    val supporting: String? = null
)

data class MediaArrHistoryItem(
    val id: String,
    val title: String,
    val subtitle: String? = null,
    val supporting: String? = null
)

data class MediaArrSnapshot(
    val serviceType: ServiceType,
    val serviceLabel: String,
    val version: String?,
    val status: String?,
    val details: List<MediaArrMetric> = emptyList(),
    val metrics: List<MediaArrMetric>,
    val downloadItems: List<MediaArrDownloadItem> = emptyList(),
    val libraryTitle: String? = null,
    val libraryItems: List<MediaArrSearchResultItem> = emptyList(),
    val recentHistoryItems: List<MediaArrHistoryItem> = emptyList(),
    val highlights: List<String>,
    val warnings: List<String>,
    val actions: List<MediaArrAction>,
    val qbittorrentItems: List<QbittorrentTorrentItem> = emptyList(),
    val jellyseerrRequests: List<JellyseerrRequestItem> = emptyList(),
    val flaresolverrSessions: List<String> = emptyList()
)

data class MediaArrCardPreviewMetric(
    val label: String,
    val value: String
)

data class MediaArrCardPreview(
    val serviceType: ServiceType,
    val headline: String?,
    val metrics: List<MediaArrCardPreviewMetric>
)

enum class MediaArrSearchRequestTarget {
    RADARR_MOVIE,
    SONARR_SERIES,
    LIDARR_ARTIST,
    JELLYSEERR_MEDIA
}

data class MediaArrSearchResultItem(
    val id: String,
    val title: String,
    val subtitle: String?,
    val supporting: String?,
    val status: String?,
    val posterUrl: String? = null,
    val posterFallbackUrls: List<String> = emptyList(),
    val detailsUrl: String? = null,
    val details: List<MediaArrMetric> = emptyList(),
    val requestTarget: MediaArrSearchRequestTarget? = null,
    val requestId: String? = null,
    val requestMediaType: String? = null
)

data class MediaArrRequestOption(
    val key: String,
    val label: String,
    val id: Int? = null,
    val path: String? = null
)

data class MediaArrRequestConfiguration(
    val title: String,
    val qualityProfiles: List<MediaArrRequestOption> = emptyList(),
    val rootFolders: List<MediaArrRequestOption> = emptyList(),
    val languageProfiles: List<MediaArrRequestOption> = emptyList(),
    val metadataProfiles: List<MediaArrRequestOption> = emptyList()
) {
    val requiresExplicitSelection: Boolean
        get() = qualityProfiles.size > 1 ||
            rootFolders.size > 1 ||
            languageProfiles.size > 1 ||
            metadataProfiles.size > 1
}

data class MediaArrRequestSelection(
    val qualityProfile: MediaArrRequestOption? = null,
    val rootFolder: MediaArrRequestOption? = null,
    val languageProfile: MediaArrRequestOption? = null,
    val metadataProfile: MediaArrRequestOption? = null
)

class MediaArrRequestConfigurationRequiredException(
    val configuration: MediaArrRequestConfiguration
) : IllegalStateException("Additional request configuration required")

@Singleton
class MediaArrRepository @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticateWithApiKey(
        url: String,
        serviceType: ServiceType,
        apiKey: String,
        fallbackUrl: String? = null
    ) = withContext(Dispatchers.IO) {
        val path = when (serviceType) {
            ServiceType.RADARR, ServiceType.SONARR -> "/api/v3/system/status"
            ServiceType.LIDARR -> "/api/v1/system/status"
            ServiceType.JELLYSEERR -> "/api/v1/status"
            ServiceType.PROWLARR -> "/api/v1/system/status"
            ServiceType.BAZARR -> "/api/system/status"
            ServiceType.GLUETUN -> "/v1/openvpn/status"
            ServiceType.FLARESOLVERR -> "/health"
            else -> throw IllegalArgumentException("Unsupported API key service: $serviceType")
        }

        val headers = buildMap<String, String> {
            put("Accept", "application/json")
            if (apiKey.isBlank()) {
                return@buildMap
            }
            when (serviceType) {
                ServiceType.GLUETUN, ServiceType.FLARESOLVERR -> {
                    put("X-Api-Key", apiKey)
                    put("Authorization", "Bearer $apiKey")
                }
                else -> {
                    put("X-Api-Key", apiKey)
                }
            }
        }

        val baseCandidates = listOf(url, fallbackUrl)
            .mapNotNull { candidate -> candidate?.takeIf { it.isNotBlank() }?.let(::cleanUrl) }
            .distinct()

        var lastError: Throwable? = null
        for (baseUrl in baseCandidates) {
            try {
                requestRaw(
                    baseUrl = baseUrl,
                    path = path,
                    method = "GET",
                    headers = headers,
                    bypass = true
                )
                return@withContext
            } catch (error: Throwable) {
                lastError = error
            }
        }

        throw lastError ?: IllegalStateException("Authentication failed")
    }

    suspend fun authenticateQbittorrent(
        url: String,
        username: String,
        password: String,
        fallbackUrl: String? = null
    ): String {
        return withContext(Dispatchers.IO) {
            val baseCandidates = listOf(url, fallbackUrl)
                .mapNotNull { candidate -> candidate?.takeIf { it.isNotBlank() }?.let(::cleanUrl) }
                .distinct()
            val formBody = FormBody.Builder()
                .add("username", username)
                .add("password", password)
                .build()

            var lastError: Throwable? = null
            for (baseUrl in baseCandidates) {
                val request = Request.Builder()
                    .url("$baseUrl/api/v2/auth/login")
                    .post(formBody)
                    .addHeader("X-Homelab-Bypass", "true")
                    .build()

                try {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) {
                            throw IllegalStateException("qBittorrent authentication failed")
                        }
                        val setCookie = response.headers("Set-Cookie").joinToString(";")
                        val sid = parseSidFromSetCookie(setCookie)
                        if (sid.isNullOrBlank()) {
                            throw IllegalStateException("Missing SID cookie from qBittorrent")
                        }
                        return@withContext sid
                    }
                } catch (error: Throwable) {
                    lastError = error
                }
            }

            throw lastError ?: IllegalStateException("qBittorrent authentication failed")
        }
    }

    suspend fun loadSnapshot(instanceId: String): MediaArrSnapshot = withContext(Dispatchers.IO) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")
        when (instance.type) {
            ServiceType.QBITTORRENT -> qbittorrentSnapshot(instance)
            ServiceType.RADARR -> arrV3Snapshot(instance, isMovie = true)
            ServiceType.SONARR -> arrV3Snapshot(instance, isMovie = false)
            ServiceType.LIDARR -> lidarrSnapshot(instance)
            ServiceType.JELLYSEERR -> jellyseerrSnapshot(instance)
            ServiceType.PROWLARR -> prowlarrSnapshot(instance)
            ServiceType.BAZARR -> bazarrSnapshot(instance)
            ServiceType.GLUETUN -> gluetunSnapshot(instance)
            ServiceType.FLARESOLVERR -> flaresolverrSnapshot(instance)
            else -> throw IllegalStateException("Unsupported media service: ${instance.type.displayName}")
        }
    }

    suspend fun loadCardPreview(instanceId: String): MediaArrCardPreview = withContext(Dispatchers.IO) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")
        when (instance.type) {
            ServiceType.QBITTORRENT -> qbittorrentCardPreview(instance)
            ServiceType.RADARR -> arrV3CardPreview(instance)
            ServiceType.SONARR -> arrV3CardPreview(instance)
            ServiceType.LIDARR -> lidarrCardPreview(instance)
            ServiceType.JELLYSEERR -> jellyseerrCardPreview(instance)
            ServiceType.PROWLARR -> prowlarrCardPreview(instance)
            ServiceType.BAZARR -> bazarrCardPreview(instance)
            ServiceType.GLUETUN -> gluetunCardPreview(instance)
            ServiceType.FLARESOLVERR -> flaresolverrCardPreview(instance)
            else -> throw IllegalStateException("Unsupported media service: ${instance.type.displayName}")
        }
    }

    suspend fun searchContent(instanceId: String, query: String): List<MediaArrSearchResultItem> = withContext(Dispatchers.IO) {
        val term = query.trim()
        if (term.length < 2) return@withContext emptyList()

        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")

        when (instance.type) {
            ServiceType.RADARR -> searchRadarr(instance, term)
            ServiceType.SONARR -> searchSonarr(instance, term)
            ServiceType.LIDARR -> searchLidarr(instance, term)
            ServiceType.JELLYSEERR -> searchJellyseerr(instance, term)
            ServiceType.PROWLARR -> searchProwlarr(instance, term)
            else -> emptyList()
        }
    }

    suspend fun runAction(instanceId: String, action: MediaArrAction): MediaArrActionResult = withContext(Dispatchers.IO) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")

        when (action) {
            MediaArrAction.QBITTORRENT_PAUSE_ALL -> {
                qbittorrentRequestWithSessionRecovery(instance) {
                    requestInstance(it, "/api/v2/torrents/pause", method = "POST", body = "hashes=all", extraHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded"))
                }
                MediaArrActionResult(action)
            }
            MediaArrAction.QBITTORRENT_RESUME_ALL -> {
                qbittorrentRequestWithSessionRecovery(instance) {
                    requestInstance(it, "/api/v2/torrents/resume", method = "POST", body = "hashes=all", extraHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded"))
                }
                MediaArrActionResult(action)
            }
            MediaArrAction.QBITTORRENT_TOGGLE_ALT_SPEED -> {
                qbittorrentRequestWithSessionRecovery(instance) {
                    requestInstance(it, "/api/v2/transfer/toggleSpeedLimitsMode", method = "POST")
                }
                MediaArrActionResult(action)
            }
            MediaArrAction.QBITTORRENT_FORCE_RECHECK -> {
                qbittorrentRequestWithSessionRecovery(instance) {
                    requestInstance(it, "/api/v2/torrents/recheck", method = "POST", body = "hashes=all", extraHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded"))
                }
                MediaArrActionResult(action)
            }
            MediaArrAction.QBITTORRENT_REANNOUNCE -> {
                qbittorrentRequestWithSessionRecovery(instance) {
                    requestInstance(it, "/api/v2/torrents/reannounce", method = "POST", body = "hashes=all", extraHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded"))
                }
                MediaArrActionResult(action)
            }
            MediaArrAction.QBITTORRENT_PAUSE_TORRENT,
            MediaArrAction.QBITTORRENT_RESUME_TORRENT,
            MediaArrAction.QBITTORRENT_RECHECK_TORRENT,
            MediaArrAction.QBITTORRENT_REANNOUNCE_TORRENT,
            MediaArrAction.QBITTORRENT_DELETE_TORRENT,
            MediaArrAction.QBITTORRENT_DELETE_TORRENT_WITH_DATA -> {
                throw IllegalArgumentException("Torrent-level action requires torrent hash")
            }
            MediaArrAction.RADARR_SEARCH_MISSING -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("MissingMoviesSearch", "MoviesSearch", "MovieSearch"))
                MediaArrActionResult(action)
            }
            MediaArrAction.RADARR_RSS_SYNC -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("RssSync", "RSSSync"))
                MediaArrActionResult(action)
            }
            MediaArrAction.RADARR_REFRESH_INDEX -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("RefreshMovie", "RefreshMovies", "RefreshMonitoredDownloads"))
                MediaArrActionResult(action)
            }
            MediaArrAction.RADARR_RESCAN -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("RescanFolders", "RescanMovie", "RescanMovieFiles"))
                MediaArrActionResult(action)
            }
            MediaArrAction.RADARR_DOWNLOADED_SCAN -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("DownloadedMoviesScan", "CheckForFinishedDownload"))
                MediaArrActionResult(action)
            }
            MediaArrAction.RADARR_HEALTH_CHECK -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("HealthCheck", "CheckHealth"))
                MediaArrActionResult(action)
            }
            MediaArrAction.SONARR_SEARCH_MISSING -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("MissingEpisodeSearch", "SeriesSearch", "EpisodeSearch"))
                MediaArrActionResult(action)
            }
            MediaArrAction.SONARR_RSS_SYNC -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("RssSync", "RSSSync"))
                MediaArrActionResult(action)
            }
            MediaArrAction.SONARR_REFRESH_INDEX -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("RefreshSeries", "RefreshMonitoredDownloads"))
                MediaArrActionResult(action)
            }
            MediaArrAction.SONARR_RESCAN -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("RescanSeries", "RescanSeriesPaths", "RescanFolders"))
                MediaArrActionResult(action)
            }
            MediaArrAction.SONARR_DOWNLOADED_SCAN -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("DownloadedEpisodesScan", "CheckForFinishedDownload"))
                MediaArrActionResult(action)
            }
            MediaArrAction.SONARR_HEALTH_CHECK -> {
                runArrCommand(instance, apiVersion = 3, candidates = listOf("HealthCheck", "CheckHealth"))
                MediaArrActionResult(action)
            }
            MediaArrAction.LIDARR_SEARCH_MISSING -> {
                runArrCommand(instance, apiVersion = 1, candidates = listOf("MissingAlbumSearch", "AlbumSearch"))
                MediaArrActionResult(action)
            }
            MediaArrAction.LIDARR_RSS_SYNC -> {
                runArrCommand(instance, apiVersion = 1, candidates = listOf("RssSync", "RSSSync"))
                MediaArrActionResult(action)
            }
            MediaArrAction.LIDARR_REFRESH_INDEX -> {
                runArrCommand(instance, apiVersion = 1, candidates = listOf("RefreshArtist", "RefreshMonitoredDownloads"))
                MediaArrActionResult(action)
            }
            MediaArrAction.LIDARR_RESCAN -> {
                runArrCommand(instance, apiVersion = 1, candidates = listOf("RescanFolders", "RescanArtist", "RescanArtists"))
                MediaArrActionResult(action)
            }
            MediaArrAction.LIDARR_DOWNLOADED_SCAN -> {
                runArrCommand(instance, apiVersion = 1, candidates = listOf("DownloadedAlbumsScan", "CheckForFinishedDownload"))
                MediaArrActionResult(action)
            }
            MediaArrAction.LIDARR_HEALTH_CHECK -> {
                runArrCommand(instance, apiVersion = 1, candidates = listOf("HealthCheck", "CheckHealth"))
                MediaArrActionResult(action)
            }
            MediaArrAction.JELLYSEERR_APPROVE_PENDING -> {
                val pending = oldestPendingJellyseerrRequest(instance)
                requestInstance(instance, "/api/v1/request/${pending.first}/approve", method = "POST")
                MediaArrActionResult(action, detail = pending.second)
            }
            MediaArrAction.JELLYSEERR_DECLINE_PENDING -> {
                val pending = oldestPendingJellyseerrRequest(instance)
                requestInstance(instance, "/api/v1/request/${pending.first}/decline", method = "POST")
                MediaArrActionResult(action, detail = pending.second)
            }
            MediaArrAction.JELLYSEERR_APPROVE_REQUEST,
            MediaArrAction.JELLYSEERR_DECLINE_REQUEST -> {
                throw IllegalArgumentException("Request-level Jellyseerr action requires request id")
            }
            MediaArrAction.JELLYSEERR_RUN_RECENT_SCAN -> {
                val job = runJellyseerrJob(
                    instance = instance,
                    keywordCandidates = listOf("recently", "added", "new")
                )
                MediaArrActionResult(action, detail = job)
            }
            MediaArrAction.JELLYSEERR_RUN_FULL_SCAN -> {
                val job = runJellyseerrJob(
                    instance = instance,
                    keywordCandidates = listOf("scan", "sync", "plex", "jellyfin", "emby", "radarr", "sonarr")
                )
                MediaArrActionResult(action, detail = job)
            }
            MediaArrAction.PROWLARR_TEST_INDEXERS -> {
                runProwlarrIndexerTest(instance)
                MediaArrActionResult(action)
            }
            MediaArrAction.PROWLARR_SYNC_APPS -> {
                runProwlarrCommand(instance, candidates = listOf("ApplicationIndexerSync", "ApplicationSync", "IndexerSync"), path = "/api/v1/command")
                MediaArrActionResult(action)
            }
            MediaArrAction.PROWLARR_HEALTH_CHECK -> {
                runProwlarrCommand(instance, candidates = listOf("HealthCheck", "CheckHealth"), path = "/api/v1/command")
                MediaArrActionResult(action)
            }
            MediaArrAction.GLUETUN_RESTART_VPN -> {
                runGluetunRestart(instance)
                MediaArrActionResult(action)
            }
            MediaArrAction.FLARESOLVERR_CREATE_SESSION -> {
                val generated = "homelab-${java.util.UUID.randomUUID().toString().take(8)}"
                val payload = JSONObject()
                    .put("cmd", "sessions.create")
                    .put("session", generated)
                    .toString()
                val response = requestInstance(
                    instance,
                    "/v1",
                    method = "POST",
                    body = payload,
                    extraHeaders = mapOf("Content-Type" to "application/json")
                )
                val created = response.asJsonObject?.optString("session")?.takeIf { it.isNotBlank() } ?: generated
                MediaArrActionResult(action, detail = created)
            }
            MediaArrAction.FLARESOLVERR_DESTROY_SESSION -> {
                val listPayload = JSONObject().put("cmd", "sessions.list").toString()
                val sessionsResponse = requestInstance(
                    instance,
                    "/v1",
                    method = "POST",
                    body = listPayload,
                    extraHeaders = mapOf("Content-Type" to "application/json")
                )
                val sessions = sessionsResponse.asJsonObject
                    ?.optJSONArray("sessions")
                    ?.let { arr -> List(arr.length()) { index -> arr.optString(index) } }
                    .orEmpty()
                    .filter { it.isNotBlank() }
                val target = sessions.firstOrNull() ?: throw IllegalStateException("No sessions to destroy")
                val payload = JSONObject()
                    .put("cmd", "sessions.destroy")
                    .put("session", target)
                    .toString()
                requestInstance(
                    instance,
                    "/v1",
                    method = "POST",
                    body = payload,
                    extraHeaders = mapOf("Content-Type" to "application/json")
                )
                MediaArrActionResult(action, detail = target)
            }
            MediaArrAction.RADARR_ADD_CONTENT,
            MediaArrAction.SONARR_ADD_CONTENT,
            MediaArrAction.LIDARR_ADD_CONTENT,
            MediaArrAction.JELLYSEERR_REQUEST_CONTENT -> {
                throw IllegalArgumentException("Content request action requires a search result payload")
            }
        }
    }

    suspend fun runJellyseerrRequestAction(
        instanceId: String,
        requestId: Int,
        title: String?,
        approve: Boolean
    ): MediaArrActionResult = withContext(Dispatchers.IO) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")
        if (instance.type != ServiceType.JELLYSEERR) {
            throw IllegalStateException("Request-level actions are only supported for Jellyseerr")
        }
        if (requestId <= 0) throw IllegalArgumentException("Invalid Jellyseerr request id")

        val path = if (approve) "/api/v1/request/$requestId/approve" else "/api/v1/request/$requestId/decline"
        requestInstance(instance, path, method = "POST")
        MediaArrActionResult(
            action = if (approve) MediaArrAction.JELLYSEERR_APPROVE_REQUEST else MediaArrAction.JELLYSEERR_DECLINE_REQUEST,
            detail = title
        )
    }

    suspend fun runQbittorrentTorrentAction(
        instanceId: String,
        torrentHash: String,
        torrentName: String?,
        action: MediaArrAction
    ): MediaArrActionResult = withContext(Dispatchers.IO) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")
        if (instance.type != ServiceType.QBITTORRENT) {
            throw IllegalStateException("Torrent-level actions are only supported for qBittorrent")
        }

        val safeHash = torrentHash.trim()
        if (safeHash.isBlank()) throw IllegalArgumentException("Missing torrent hash")

        val formHeaders = mapOf("Content-Type" to "application/x-www-form-urlencoded")
        var activeInstance = instance
        when (action) {
            MediaArrAction.QBITTORRENT_PAUSE_TORRENT -> {
                qbittorrentRequestWithSessionRecovery(activeInstance) {
                    requestInstance(it, "/api/v2/torrents/pause", method = "POST", body = "hashes=$safeHash", extraHeaders = formHeaders)
                }.also { activeInstance = it.second }
                MediaArrActionResult(action, detail = torrentName)
            }
            MediaArrAction.QBITTORRENT_RESUME_TORRENT -> {
                qbittorrentRequestWithSessionRecovery(activeInstance) {
                    requestInstance(it, "/api/v2/torrents/resume", method = "POST", body = "hashes=$safeHash", extraHeaders = formHeaders)
                }.also { activeInstance = it.second }
                MediaArrActionResult(action, detail = torrentName)
            }
            MediaArrAction.QBITTORRENT_RECHECK_TORRENT -> {
                qbittorrentRequestWithSessionRecovery(activeInstance) {
                    requestInstance(it, "/api/v2/torrents/recheck", method = "POST", body = "hashes=$safeHash", extraHeaders = formHeaders)
                }.also { activeInstance = it.second }
                MediaArrActionResult(action, detail = torrentName)
            }
            MediaArrAction.QBITTORRENT_REANNOUNCE_TORRENT -> {
                qbittorrentRequestWithSessionRecovery(activeInstance) {
                    requestInstance(it, "/api/v2/torrents/reannounce", method = "POST", body = "hashes=$safeHash", extraHeaders = formHeaders)
                }.also { activeInstance = it.second }
                MediaArrActionResult(action, detail = torrentName)
            }
            MediaArrAction.QBITTORRENT_DELETE_TORRENT -> {
                qbittorrentRequestWithSessionRecovery(activeInstance) {
                    requestInstance(it, "/api/v2/torrents/delete", method = "POST", body = "hashes=$safeHash&deleteFiles=false", extraHeaders = formHeaders)
                }.also { activeInstance = it.second }
                MediaArrActionResult(action, detail = torrentName)
            }
            MediaArrAction.QBITTORRENT_DELETE_TORRENT_WITH_DATA -> {
                qbittorrentRequestWithSessionRecovery(activeInstance) {
                    requestInstance(it, "/api/v2/torrents/delete", method = "POST", body = "hashes=$safeHash&deleteFiles=true", extraHeaders = formHeaders)
                }.also { activeInstance = it.second }
                MediaArrActionResult(action, detail = torrentName)
            }
            else -> throw IllegalArgumentException("Unsupported torrent-level action: $action")
        }
    }

    suspend fun destroyFlaresolverrSession(
        instanceId: String,
        sessionId: String
    ): MediaArrActionResult = withContext(Dispatchers.IO) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")
        if (instance.type != ServiceType.FLARESOLVERR) {
            throw IllegalStateException("Session-level actions are only supported for FlareSolverr")
        }
        val target = sessionId.trim()
        if (target.isBlank()) throw IllegalArgumentException("Missing FlareSolverr session id")

        val payload = JSONObject()
            .put("cmd", "sessions.destroy")
            .put("session", target)
            .toString()
        requestInstance(
            instance = instance,
            path = "/v1",
            method = "POST",
            body = payload,
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
        MediaArrActionResult(MediaArrAction.FLARESOLVERR_DESTROY_SESSION, detail = target)
    }

    suspend fun requestSearchResult(
        instanceId: String,
        item: MediaArrSearchResultItem,
        selection: MediaArrRequestSelection? = null
    ): MediaArrActionResult = withContext(Dispatchers.IO) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Service instance not found")

        when (item.requestTarget) {
            MediaArrSearchRequestTarget.RADARR_MOVIE -> {
                addRadarrMovie(instance, item, selection)
                MediaArrActionResult(MediaArrAction.RADARR_ADD_CONTENT, detail = item.title)
            }
            MediaArrSearchRequestTarget.SONARR_SERIES -> {
                addSonarrSeries(instance, item, selection)
                MediaArrActionResult(MediaArrAction.SONARR_ADD_CONTENT, detail = item.title)
            }
            MediaArrSearchRequestTarget.LIDARR_ARTIST -> {
                addLidarrArtist(instance, item, selection)
                MediaArrActionResult(MediaArrAction.LIDARR_ADD_CONTENT, detail = item.title)
            }
            MediaArrSearchRequestTarget.JELLYSEERR_MEDIA -> {
                val mediaId = item.requestId?.toIntOrNull()
                    ?: throw IllegalStateException("Missing media id for request")
                val mediaType = item.requestMediaType
                    ?.lowercase(Locale.ROOT)
                    ?.takeIf { it == "movie" || it == "tv" }
                    ?: throw IllegalStateException("Missing media type for request")
                val payload = JSONObject()
                    .put("mediaType", mediaType)
                    .put("mediaId", mediaId)
                requestInstance(
                    instance = instance,
                    path = "/api/v1/request",
                    method = "POST",
                    body = payload.toString(),
                    extraHeaders = mapOf("Content-Type" to "application/json")
                )
                MediaArrActionResult(MediaArrAction.JELLYSEERR_REQUEST_CONTENT, detail = item.title)
            }
            null -> throw IllegalStateException("This content cannot be requested from the selected service")
        }
    }

    private suspend fun qbittorrentCardPreview(instance: ServiceInstance): MediaArrCardPreview {
        var activeInstance = instance
        val transferInfo = qbittorrentRequestWithSessionRecovery(activeInstance) {
            requestInstance(it, "/api/v2/transfer/info").asJsonObject ?: JSONObject()
        }.also { activeInstance = it.second }.first
        val connectionStatus = transferInfo.optString("connection_status").ifBlank { null }
        val dhtNodes = transferInfo.optInt("dht_nodes", -1).takeIf { it >= 0 }?.toString() ?: "0"
        return MediaArrCardPreview(
            serviceType = instance.type,
            headline = connectionStatus,
            metrics = listOf(
                MediaArrCardPreviewMetric("Download", speedLabel(transferInfo.optLong("dl_info_speed"))),
                MediaArrCardPreviewMetric("Upload", speedLabel(transferInfo.optLong("up_info_speed"))),
                MediaArrCardPreviewMetric("DHT Nodes", dhtNodes)
            )
        )
    }

    private fun arrV3CardPreview(instance: ServiceInstance): MediaArrCardPreview {
        val status = requestInstance(instance, "/api/v3/system/status").asJsonObject ?: JSONObject()
        val queue = requestInstance(instance, "/api/v3/queue?page=1&pageSize=1&sortDirection=descending").asJsonObject ?: JSONObject()
        val healthRows = requestInstance(instance, "/api/v3/health").asJsonArray ?: JSONArray()

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = Date()
        val end = Date(now.time + 14L * 24L * 60L * 60L * 1000L)
        val calendarPath = "/api/v3/calendar?start=${dateFormatter.format(now)}&end=${dateFormatter.format(end)}"
        val upcoming = requestInstance(instance, calendarPath).asJsonArray ?: JSONArray()

        val queueTotal = queue.optInt("totalRecords", queue.optInt("recordsTotal", queue.optInt("total", 0)))
        val headline = listOfNotNull(
            status.optString("version").takeIf { it.isNotBlank() }?.let { "v$it" },
            status.optString("branch").takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        ).joinToString(" • ").ifBlank { null }

        return MediaArrCardPreview(
            serviceType = instance.type,
            headline = headline,
            metrics = listOf(
                MediaArrCardPreviewMetric("Download", queueTotal.toString()),
                MediaArrCardPreviewMetric("Health", healthRows.length().toString()),
                MediaArrCardPreviewMetric("Upcoming", upcoming.length().toString())
            )
        )
    }

    private fun lidarrCardPreview(instance: ServiceInstance): MediaArrCardPreview {
        val status = requestInstance(instance, "/api/v1/system/status").asJsonObject ?: JSONObject()
        val queue = requestInstance(instance, "/api/v1/queue?page=1&pageSize=1&sortDirection=descending").asJsonObject ?: JSONObject()
        val healthRows = requestInstance(instance, "/api/v1/health").asJsonArray ?: JSONArray()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = Date()
        val end = Date(now.time + 14L * 24L * 60L * 60L * 1000L)
        val calendarPath = "/api/v1/calendar?start=${dateFormatter.format(now)}&end=${dateFormatter.format(end)}"
        val upcoming = requestInstance(instance, calendarPath).asJsonArray ?: JSONArray()

        val queueTotal = queue.optInt("totalRecords", queue.optInt("recordsTotal", queue.optInt("total", 0)))
        val headline = listOfNotNull(
            status.optString("version").takeIf { it.isNotBlank() }?.let { "v$it" },
            status.optString("branch").takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        ).joinToString(" • ").ifBlank { null }

        return MediaArrCardPreview(
            serviceType = instance.type,
            headline = headline,
            metrics = listOf(
                MediaArrCardPreviewMetric("Download", queueTotal.toString()),
                MediaArrCardPreviewMetric("Health", healthRows.length().toString()),
                MediaArrCardPreviewMetric("Upcoming", upcoming.length().toString())
            )
        )
    }

    private fun jellyseerrCardPreview(instance: ServiceInstance): MediaArrCardPreview {
        val status = requestInstance(instance, "/api/v1/status").asJsonObject ?: JSONObject()
        val requestsObj = requestInstance(instance, "/api/v1/request?take=20&skip=0&sort=added&filter=all").asJsonObject ?: JSONObject()
        val results = requestsObj.optJSONArray("results") ?: JSONArray()

        var pending = 0
        var available = 0
        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            val statusValue = row.opt("status")
            val normalized = when (statusValue) {
                is Number -> statusValue.toInt().toString()
                else -> statusValue?.toString().orEmpty().lowercase(Locale.ROOT)
            }
            when (normalized) {
                "1", "pending" -> pending += 1
                "5", "available" -> available += 1
            }
        }
        val total = requestsObj.optInt("totalResults", requestsObj.optInt("total", results.length()))
        val headline = firstNonBlank(
            status.optString("version").takeIf { it.isNotBlank() }?.let { "v$it" },
            status.optString("commitTag")
        )

        return MediaArrCardPreview(
            serviceType = instance.type,
            headline = headline,
            metrics = listOf(
                MediaArrCardPreviewMetric("Requests", total.toString()),
                MediaArrCardPreviewMetric("Pending", pending.toString()),
                MediaArrCardPreviewMetric("Available", available.toString())
            )
        )
    }

    private fun prowlarrCardPreview(instance: ServiceInstance): MediaArrCardPreview {
        val status = requestInstance(instance, "/api/v1/system/status").asJsonObject ?: JSONObject()
        val indexers = requestInstance(instance, "/api/v1/indexer").asJsonArray ?: JSONArray()
        val health = requestInstance(instance, "/api/v1/health").asJsonArray ?: JSONArray()
        val apps = requestInstance(instance, "/api/v1/applications").asJsonArray ?: JSONArray()

        val unhealthy = countWhere(indexers) {
            val state = it.optString("status").lowercase(Locale.ROOT)
            state.contains("error") || state.contains("down") || state.contains("unhealthy")
        } + health.length()

        val headline = listOfNotNull(
            status.optString("version").takeIf { it.isNotBlank() }?.let { "v$it" },
            status.optString("branch").takeIf { it.isNotBlank() }?.replaceFirstChar { it.uppercase() }
        ).joinToString(" • ").ifBlank { null }

        return MediaArrCardPreview(
            serviceType = instance.type,
            headline = headline,
            metrics = listOf(
                MediaArrCardPreviewMetric("Indexers", indexers.length().toString()),
                MediaArrCardPreviewMetric("Apps", apps.length().toString()),
                MediaArrCardPreviewMetric("Issues", unhealthy.toString())
            )
        )
    }

    private fun bazarrCardPreview(instance: ServiceInstance): MediaArrCardPreview {
        val status = requestInstance(instance, "/api/system/status").asJsonObject ?: JSONObject()
        val badges = requestInstance(instance, "/api/badges").asJsonObject ?: JSONObject()
        val health = requestInstance(instance, "/api/system/health").asJsonArray ?: JSONArray()

        val badgeMetrics = buildList {
            listOf("wanted", "missing", "providers").forEach { key ->
                if (badges.has(key)) {
                    add(
                        MediaArrCardPreviewMetric(
                            key.replaceFirstChar { it.uppercaseChar() },
                            badges.opt(key)?.toString().orEmpty()
                        )
                    )
                }
            }
        }

        return MediaArrCardPreview(
            serviceType = instance.type,
            headline = firstNonBlank(
                status.optString("version").takeIf { it.isNotBlank() }?.let { "v$it" },
                status.optJSONObject("data")?.optString("version")?.takeIf { it.isNotBlank() }?.let { "v$it" }
            ),
            metrics = if (badgeMetrics.isNotEmpty()) {
                badgeMetrics
            } else {
                listOf(MediaArrCardPreviewMetric("Health", health.length().toString()))
            }
        )
    }

    private fun gluetunCardPreview(instance: ServiceInstance): MediaArrCardPreview {
        val vpn = requestInstance(instance, "/v1/openvpn/status", expectJson = true).asJsonObject ?: JSONObject()
        val publicIpObj = requestInstance(instance, "/v1/publicip/ip", expectJson = true).asJsonObject ?: JSONObject()
        val publicIpRaw = requestInstance(instance, "/v1/publicip/ip", expectJson = false).body.trim()
        val forwardedObj = requestInstance(instance, "/v1/openvpn/portforwarded", expectJson = true).asJsonObject ?: JSONObject()
        val forwardedRaw = requestInstance(instance, "/v1/openvpn/portforwarded", expectJson = false).body.trim()

        val status = firstNonBlank(
            vpn.optString("status"),
            vpn.optJSONObject("openvpn")?.optString("status")
        ) ?: "Unknown"
        val provider = firstNonBlank(
            vpn.optString("provider"),
            vpn.optJSONObject("vpn")?.optString("provider"),
            vpn.optJSONObject("openvpn")?.optString("provider")
        )
        val server = firstNonBlank(
            vpn.optString("server_name"),
            vpn.optJSONObject("openvpn")?.optString("server_name")
        )
        val publicIp = firstNonBlank(
            publicIpObj.optString("public_ip"),
            publicIpObj.optString("ip"),
            publicIpRaw
        ) ?: "N/A"
        val forwardedPort = firstNonBlank(
            forwardedObj.optString("port"),
            forwardedObj.optString("port_forwarded"),
            forwardedRaw
        ) ?: "N/A"

        return MediaArrCardPreview(
            serviceType = instance.type,
            headline = firstNonBlank(provider, server, status),
            metrics = listOf(
                MediaArrCardPreviewMetric("Status", status),
                MediaArrCardPreviewMetric("Public IP", publicIp),
                MediaArrCardPreviewMetric("Forwarded Port", forwardedPort)
            )
        )
    }

    private fun flaresolverrCardPreview(instance: ServiceInstance): MediaArrCardPreview {
        val healthObj = requestInstance(instance, "/health").asJsonObject ?: JSONObject()
        val sessionsObj = requestInstance(
            instance,
            "/v1",
            method = "POST",
            body = JSONObject().put("cmd", "sessions.list").toString(),
            extraHeaders = mapOf("Content-Type" to "application/json")
        ).asJsonObject ?: JSONObject()
        val sessionsArray = sessionsObj.optJSONArray("sessions") ?: JSONArray()

        return MediaArrCardPreview(
            serviceType = instance.type,
            headline = firstNonBlank(
                healthObj.optString("version").takeIf { it.isNotBlank() }?.let { "v$it" },
                healthObj.optString("message"),
                healthObj.optString("msg")
            ),
            metrics = listOf(
                MediaArrCardPreviewMetric("Sessions", sessionsArray.length().toString()),
                MediaArrCardPreviewMetric("Status", healthObj.optString("status").ifBlank { "unknown" })
            )
        )
    }

    private fun searchRadarr(instance: ServiceInstance, term: String): List<MediaArrSearchResultItem> {
        val encoded = urlEncode(term)
        val rows = requestInstance(instance, "/api/v3/movie/lookup?term=$encoded").asJsonArray ?: JSONArray()
        return buildList {
            val limit = minOf(rows.length(), 25)
            for (i in 0 until limit) {
                val row = rows.optJSONObject(i) ?: continue
                val artworkCandidates = extractPosterCandidates(instance, row)
                val title = firstNonBlank(row.optString("title"), row.optString("titleSlug")) ?: continue
                val tmdbId = row.optInt("tmdbId", -1).takeIf { it > 0 }
                val year = row.optInt("year", -1).takeIf { it > 0 }
                val subtitle = listOfNotNull(
                    year?.toString(),
                    tmdbId?.let { "TMDb $it" }
                ).joinToString(" • ").takeIf { it.isNotBlank() }
                val monitored = row.optBoolean("monitored", false)
                val hasFile = row.optBoolean("hasFile", false)
                val status = when {
                    hasFile -> "In Library"
                    monitored -> "Monitored"
                    else -> "Unmonitored"
                }
                val supporting = row.optString("status").ifBlank { null }
                val imdbId = row.optString("imdbId").takeIf { it.isNotBlank() }
                val id = firstNonBlank(
                    tmdbId?.toString(),
                    imdbId,
                    row.optString("titleSlug"),
                    title
                ) ?: title
                val detailsUrl = when {
                    tmdbId != null -> "https://www.themoviedb.org/movie/$tmdbId"
                    !imdbId.isNullOrBlank() -> "https://www.imdb.com/title/$imdbId/"
                    else -> null
                }
                val studio = row.optString("studio").ifBlank { null }
                val originalTitle = row.optString("originalTitle").ifBlank { null }
                val runtime = row.optInt("runtime", -1).takeIf { it > 0 }?.let { "$it min" }
                val availability = row.optString("minimumAvailability").ifBlank { null }
                val genres = jsonStringArray(row.optJSONArray("genres"))
                    .takeIf { it.isNotEmpty() }
                    ?.take(3)
                    ?.joinToString(", ")
                val overview = row.optString("overview").ifBlank { null }
                val details = buildSearchDetails(
                    "Year" to year?.toString(),
                    "TMDb" to tmdbId?.toString(),
                    "IMDb" to imdbId,
                    "Studio" to studio,
                    "Original" to originalTitle,
                    "Runtime" to runtime,
                    "Genres" to genres,
                    "Availability" to availability,
                    "Overview" to overview
                )
                add(
                    MediaArrSearchResultItem(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        supporting = supporting,
                        status = status,
                        posterUrl = artworkCandidates.firstOrNull(),
                        posterFallbackUrls = artworkCandidates.drop(1),
                        detailsUrl = detailsUrl,
                        details = details,
                        requestTarget = tmdbId?.let { MediaArrSearchRequestTarget.RADARR_MOVIE },
                        requestId = tmdbId?.toString()
                    )
                )
            }
        }
    }

    private fun searchSonarr(instance: ServiceInstance, term: String): List<MediaArrSearchResultItem> {
        val encoded = urlEncode(term)
        val rows = requestInstance(instance, "/api/v3/series/lookup?term=$encoded").asJsonArray ?: JSONArray()
        return buildList {
            val limit = minOf(rows.length(), 25)
            for (i in 0 until limit) {
                val row = rows.optJSONObject(i) ?: continue
                val artworkCandidates = extractPosterCandidates(instance, row)
                val title = firstNonBlank(row.optString("title"), row.optString("titleSlug")) ?: continue
                val tvdbId = row.optInt("tvdbId", -1).takeIf { it > 0 }
                val year = row.optInt("year", -1).takeIf { it > 0 }
                val network = row.optString("network").takeIf { it.isNotBlank() }
                val subtitle = listOfNotNull(
                    year?.toString(),
                    network,
                    tvdbId?.let { "TVDB $it" }
                ).joinToString(" • ").takeIf { it.isNotBlank() }
                val monitored = row.optBoolean("monitored", false)
                val ended = row.optBoolean("ended", false)
                val status = when {
                    ended -> "Ended"
                    monitored -> "Monitored"
                    else -> "Unmonitored"
                }
                val supporting = row.optString("status").ifBlank { null }
                val id = firstNonBlank(
                    tvdbId?.toString(),
                    row.opt("tvMazeId")?.toString(),
                    row.optString("titleSlug"),
                    title
                ) ?: title
                val detailsUrl = tvdbId?.let { "https://thetvdb.com/dereferrer/series/$it" }
                val tvMazeId = row.optInt("tvMazeId", -1).takeIf { it > 0 }?.toString()
                val seasonCount = row.optInt("seasonCount", -1).takeIf { it > 0 }?.toString()
                val runtime = row.optInt("runtime", -1).takeIf { it > 0 }?.let { "$it min" }
                val genres = jsonStringArray(row.optJSONArray("genres"))
                    .takeIf { it.isNotEmpty() }
                    ?.take(3)
                    ?.joinToString(", ")
                val overview = row.optString("overview").ifBlank { null }
                val details = buildSearchDetails(
                    "Year" to year?.toString(),
                    "TVDB" to tvdbId?.toString(),
                    "TVMaze" to tvMazeId,
                    "Network" to network,
                    "Seasons" to seasonCount,
                    "Runtime" to runtime,
                    "Genres" to genres,
                    "Overview" to overview
                )
                add(
                    MediaArrSearchResultItem(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        supporting = supporting,
                        status = status,
                        posterUrl = artworkCandidates.firstOrNull(),
                        posterFallbackUrls = artworkCandidates.drop(1),
                        detailsUrl = detailsUrl,
                        details = details,
                        requestTarget = tvdbId?.let { MediaArrSearchRequestTarget.SONARR_SERIES },
                        requestId = tvdbId?.toString()
                    )
                )
            }
        }
    }

    private fun searchLidarr(instance: ServiceInstance, term: String): List<MediaArrSearchResultItem> {
        val encoded = urlEncode(term)
        val rows = requestInstance(instance, "/api/v1/artist/lookup?term=$encoded").asJsonArray ?: JSONArray()
        return buildList {
            val limit = minOf(rows.length(), 25)
            for (i in 0 until limit) {
                val row = rows.optJSONObject(i) ?: continue
                val artworkCandidates = extractPosterCandidates(instance, row)
                val title = firstNonBlank(row.optString("artistName"), row.optString("sortName")) ?: continue
                val disambiguation = row.optString("disambiguation").takeIf { it.isNotBlank() }
                val artistType = row.optString("artistType").takeIf { it.isNotBlank() }
                val subtitle = listOfNotNull(disambiguation, artistType).joinToString(" • ").takeIf { it.isNotBlank() }
                val monitored = row.optBoolean("monitored", false)
                val ended = row.optBoolean("ended", false)
                val status = when {
                    ended -> "Ended"
                    monitored -> "Monitored"
                    else -> "Unmonitored"
                }
                val supporting = row.optString("status").ifBlank { null }
                val foreignArtistId = row.optString("foreignArtistId").takeIf { it.isNotBlank() }
                val id = firstNonBlank(
                    foreignArtistId,
                    row.optString("artistName"),
                    title
                ) ?: title
                val detailsUrl = foreignArtistId?.let { "https://musicbrainz.org/artist/$it" }
                val country = row.optString("country").ifBlank { null }
                val sortName = row.optString("sortName").ifBlank { null }
                val genres = jsonStringArray(row.optJSONArray("genres"))
                    .takeIf { it.isNotEmpty() }
                    ?.take(3)
                    ?.joinToString(", ")
                val overview = row.optString("overview").ifBlank { null }
                val details = buildSearchDetails(
                    "MusicBrainz" to foreignArtistId,
                    "Type" to artistType,
                    "Country" to country,
                    "Sort Name" to sortName,
                    "Genres" to genres,
                    "Overview" to overview
                )
                add(
                    MediaArrSearchResultItem(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        supporting = supporting,
                        status = status,
                        posterUrl = artworkCandidates.firstOrNull(),
                        posterFallbackUrls = artworkCandidates.drop(1),
                        detailsUrl = detailsUrl,
                        details = details,
                        requestTarget = foreignArtistId?.let { MediaArrSearchRequestTarget.LIDARR_ARTIST },
                        requestId = foreignArtistId
                    )
                )
            }
        }
    }

    private fun searchJellyseerr(instance: ServiceInstance, term: String): List<MediaArrSearchResultItem> {
        val encoded = urlEncode(term)
        val payload = requestInstance(instance, "/api/v1/search?query=$encoded&page=1").asJsonObject ?: JSONObject()
        val rows = payload.optJSONArray("results") ?: JSONArray()
        return buildList {
            val limit = minOf(rows.length(), 25)
            for (i in 0 until limit) {
                val row = rows.optJSONObject(i) ?: continue
                val title = firstNonBlank(
                    row.optString("title"),
                    row.optString("name"),
                    row.optString("originalTitle"),
                    row.optString("originalName")
                ) ?: continue
                val mediaTypeRaw = firstNonBlank(row.optString("mediaType"), row.optString("media_type"))
                val mediaType = mediaTypeRaw?.uppercase(Locale.ROOT)
                val mediaTypePath = when (mediaTypeRaw?.lowercase(Locale.ROOT)) {
                    "movie" -> "movie"
                    "tv", "show", "series" -> "tv"
                    else -> null
                }
                val release = firstNonBlank(row.optString("releaseDate"), row.optString("firstAirDate"))
                    ?.take(4)
                val subtitle = listOfNotNull(mediaType, release).joinToString(" • ").takeIf { it.isNotBlank() }
                val mediaInfo = row.optJSONObject("mediaInfo")
                val status = mediaInfo?.let { jellyseerrStatusLabel(it.opt("status")) }
                val supporting = firstNonBlank(
                    mediaInfo?.optString("status4k"),
                    mediaInfo?.optString("mediaAddedAt")
                )
                val id = firstNonBlank(
                    row.opt("id")?.toString(),
                    title
                ) ?: title
                val posterPath = row.optString("posterPath")
                    .ifBlank { row.optString("poster_path") }
                    .takeIf { !it.isNullOrBlank() }
                val posterUrl = posterPath?.let { path ->
                    val normalized = if (path.startsWith("/")) path else "/$path"
                    "https://image.tmdb.org/t/p/w500$normalized"
                }
                val posterFallbacks = posterPath?.let { path ->
                    val normalized = if (path.startsWith("/")) path else "/$path"
                    listOf("https://image.tmdb.org/t/p/original$normalized")
                }.orEmpty()
                val detailsUrl = if (mediaTypePath != null && id.toIntOrNull() != null) {
                    "https://www.themoviedb.org/$mediaTypePath/$id"
                } else {
                    null
                }
                val language = firstNonBlank(
                    row.optString("originalLanguage"),
                    row.optString("original_language")
                )?.uppercase(Locale.ROOT)
                val voteAverage = row.optDouble("voteAverage", Double.NaN)
                    .takeIf { !it.isNaN() && !it.isInfinite() }
                    ?.let { String.format(Locale.US, "%.1f", it) }
                val voteCount = row.optInt("voteCount", -1).takeIf { it > 0 }?.toString()
                    ?: row.optInt("vote_count", -1).takeIf { it > 0 }?.toString()
                val overview = row.optString("overview").ifBlank { null }
                val details = buildSearchDetails(
                    "Media" to mediaType,
                    "Year" to release,
                    "TMDB" to id.toIntOrNull()?.toString(),
                    "Language" to language,
                    "Rating" to voteAverage,
                    "Votes" to voteCount,
                    "Overview" to overview
                )
                add(
                    MediaArrSearchResultItem(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        supporting = supporting,
                        status = status,
                        posterUrl = posterUrl,
                        posterFallbackUrls = posterFallbacks,
                        detailsUrl = detailsUrl,
                        details = details,
                        requestTarget = if (mediaTypePath != null && id.toIntOrNull() != null) {
                            MediaArrSearchRequestTarget.JELLYSEERR_MEDIA
                        } else {
                            null
                        },
                        requestId = id.toIntOrNull()?.toString(),
                        requestMediaType = mediaTypePath
                    )
                )
            }
        }
    }

    private fun searchProwlarr(instance: ServiceInstance, term: String): List<MediaArrSearchResultItem> {
        val encoded = urlEncode(term)
        val rows = requestInstance(
            instance,
            "/api/v1/search?query=$encoded&type=search&limit=25&offset=0"
        ).asJsonArray ?: JSONArray()
        return buildList {
            val limit = minOf(rows.length(), 25)
            for (i in 0 until limit) {
                val row = rows.optJSONObject(i) ?: continue
                val title = row.optString("title").ifBlank { continue }
                val indexer = row.optString("indexer").ifBlank { null }
                val protocol = row.optString("protocol").ifBlank { null }?.uppercase(Locale.ROOT)
                val subtitle = listOfNotNull(indexer, protocol).joinToString(" • ").takeIf { it.isNotBlank() }
                val size = row.optLong("size", -1L).takeIf { it > 0 }?.let { bytesLabel(it.toDouble()) }
                val seeds = row.optInt("seeders", -1).takeIf { it >= 0 }
                val leechers = row.optInt("leechers", -1).takeIf { it >= 0 }
                val supporting = listOfNotNull(
                    size,
                    if (seeds != null || leechers != null) {
                        "S:${seeds ?: "-"} L:${leechers ?: "-"}"
                    } else {
                        null
                    }
                ).joinToString(" • ").takeIf { it.isNotBlank() }
                val ageHours = row.optInt("ageHours", -1).takeIf { it >= 0 }
                val status = ageHours?.let { "${it}h" } ?: row.optInt("age", -1).takeIf { it >= 0 }?.let { "${it}d" }
                val detailsUrl = firstNonBlank(
                    row.optString("guid"),
                    row.optString("infoUrl"),
                    row.optString("comments")
                )?.let { toAbsoluteUrl(instance.url, it) }
                val grabs = row.optInt("grabs", -1).takeIf { it >= 0 }?.toString()
                val category = firstNonBlank(
                    row.optString("categoryDesc"),
                    row.optString("category")
                )
                val posterCandidates = extractProwlarrPosterCandidates(instance, row)
                val details = buildSearchDetails(
                    "Indexer" to indexer,
                    "Protocol" to protocol,
                    "Size" to size,
                    "Seeders" to seeds?.toString(),
                    "Leechers" to leechers?.toString(),
                    "Age" to status,
                    "Grabs" to grabs,
                    "Category" to category,
                    "Info URL" to detailsUrl
                )
                val id = firstNonBlank(
                    row.optString("guid"),
                    row.opt("id")?.toString(),
                    title
                ) ?: title
                add(
                    MediaArrSearchResultItem(
                        id = id,
                        title = title,
                        subtitle = subtitle,
                        supporting = supporting,
                        status = status,
                        posterUrl = posterCandidates.firstOrNull(),
                        posterFallbackUrls = posterCandidates.drop(1),
                        detailsUrl = detailsUrl,
                        details = details
                    )
                )
            }
        }
    }

    private suspend fun qbittorrentSnapshot(instance: ServiceInstance): MediaArrSnapshot {
        var activeInstance = instance
        val versionText = qbittorrentRequestWithSessionRecovery(activeInstance) {
            requestInstance(it, "/api/v2/app/version", expectJson = false).body.trim()
        }.also { activeInstance = it.second }.first
        val transferInfo = qbittorrentRequestWithSessionRecovery(activeInstance) {
            requestInstance(it, "/api/v2/transfer/info").asJsonObject ?: JSONObject()
        }.also { activeInstance = it.second }.first
        val torrents = qbittorrentRequestWithSessionRecovery(activeInstance) {
            requestInstance(it, "/api/v2/torrents/info?filter=all").asJsonArray ?: JSONArray()
        }.also { activeInstance = it.second }.first
        val torrentItems = parseQbittorrentItems(torrents)

        var downloading = 0
        var seeding = 0
        var stalled = 0
        val altSpeedEnabled = transferInfo.optBoolean("use_alt_speed_limits", false)
        val freeSpaceBytes = transferInfo.optLong("free_space_on_disk", -1)

        for (index in 0 until torrents.length()) {
            val row = torrents.optJSONObject(index) ?: continue
            when (row.optString("state").lowercase(Locale.ROOT)) {
                "downloading", "forceddl", "metadl" -> downloading += 1
                "uploading", "forcedup", "stalledup" -> seeding += 1
                "stalleddl", "stalledup", "queueddl", "queuedup" -> stalled += 1
            }
        }
        val dhtNodes = transferInfo.optInt("dht_nodes", -1)
        val allTimeDownload = transferInfo.optLong("alltime_dl", -1)

        val metrics = buildList {
            add(MediaArrMetric("Torrents", torrents.length().toString()))
            add(MediaArrMetric("Downloading", downloading.toString()))
            add(MediaArrMetric("Seeding", seeding.toString()))
            add(MediaArrMetric("Queued/Stalled", stalled.toString()))
            add(MediaArrMetric("Download", speedLabel(transferInfo.optLong("dl_info_speed"))))
            add(MediaArrMetric("Upload", speedLabel(transferInfo.optLong("up_info_speed"))))
            add(MediaArrMetric("Alt Speed", if (altSpeedEnabled) "ON" else "OFF"))
            add(MediaArrMetric("Free Space", if (freeSpaceBytes >= 0) bytesLabel(freeSpaceBytes.toDouble()) else "N/A"))
            if (dhtNodes >= 0) add(MediaArrMetric("DHT Nodes", dhtNodes.toString()))
            if (allTimeDownload >= 0) add(MediaArrMetric("All-time Download", bytesLabel(allTimeDownload.toDouble())))
        }

        val highlights = buildList {
            add("Connection: ${transferInfo.optString("connection_status", "unknown")}")
        }

        return MediaArrSnapshot(
            serviceType = instance.type,
            serviceLabel = instance.label,
            version = versionText.ifBlank { null },
            status = transferInfo.optString("connection_status").ifBlank { null },
            metrics = metrics,
            highlights = highlights,
            warnings = emptyList(),
            actions = listOf(
                MediaArrAction.QBITTORRENT_PAUSE_ALL,
                MediaArrAction.QBITTORRENT_RESUME_ALL,
                MediaArrAction.QBITTORRENT_TOGGLE_ALT_SPEED,
                MediaArrAction.QBITTORRENT_FORCE_RECHECK,
                MediaArrAction.QBITTORRENT_REANNOUNCE
            ),
            qbittorrentItems = torrentItems
        )
    }

    private suspend fun <T> qbittorrentRequestWithSessionRecovery(
        instance: ServiceInstance,
        block: suspend (ServiceInstance) -> T
    ): Pair<T, ServiceInstance> {
        return try {
            block(instance) to instance
        } catch (error: Throwable) {
            if (!isQbittorrentAuthFailure(error) || !canRefreshQbittorrentSession(instance)) {
                throw error
            }

            val refreshed = refreshQbittorrentSession(instance)
            block(refreshed) to refreshed
        }
    }

    private fun isQbittorrentAuthFailure(error: Throwable): Boolean {
        val message = error.message.orEmpty()
        return message.startsWith("401:") || message.startsWith("403:")
    }

    private suspend fun refreshQbittorrentSession(instance: ServiceInstance): ServiceInstance {
        val username = instance.username.orEmpty()
        val password = instance.password.orEmpty()
        if (username.isBlank() || password.isBlank()) {
            throw IllegalStateException("qBittorrent session expired and credentials are unavailable")
        }

        val sid = authenticateQbittorrent(
            url = instance.url,
            username = username,
            password = password,
            fallbackUrl = instance.fallbackUrl
        )
        val refreshed = instance.copy(token = sid)
        serviceInstancesRepository.saveInstance(refreshed)
        return refreshed
    }

    private fun canRefreshQbittorrentSession(instance: ServiceInstance): Boolean {
        return instance.type == ServiceType.QBITTORRENT &&
            !instance.username.isNullOrBlank() &&
            !instance.password.isNullOrBlank()
    }

    private fun arrV3Snapshot(instance: ServiceInstance, isMovie: Boolean): MediaArrSnapshot {
        val status = requestInstance(instance, "/api/v3/system/status").asJsonObject ?: JSONObject()
        val libraryPath = if (isMovie) "/api/v3/movie" else "/api/v3/series"
        val library = requestInstance(instance, libraryPath).asJsonArray ?: JSONArray()
        val queue = requestInstance(instance, "/api/v3/queue?page=1&pageSize=20&sortDirection=descending").asJsonObject ?: JSONObject()
        val history = requestInstance(instance, "/api/v3/history?page=1&pageSize=20&sortDirection=descending").asJsonObject ?: JSONObject()
        val healthRows = requestInstance(instance, "/api/v3/health").asJsonArray ?: JSONArray()

        val monitoredCount = countWhere(library) { it.optBoolean("monitored", false) }
        val queueTotal = queue.optInt("totalRecords", queue.optInt("recordsTotal", 0))

        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = Date()
        val end = Date(now.time + 14L * 24L * 60L * 60L * 1000L)
        val calendarPath = "/api/v3/calendar?start=${dateFormatter.format(now)}&end=${dateFormatter.format(end)}"
        val upcoming = requestInstance(instance, calendarPath).asJsonArray ?: JSONArray()

        val highlights = buildList {
            val count = minOf(upcoming.length(), 6)
            for (i in 0 until count) {
                val row = upcoming.optJSONObject(i) ?: continue
                val title = row.optString("title")
                    .ifBlank { row.optJSONObject(if (isMovie) "movie" else "series")?.optString("title").orEmpty() }
                    .ifBlank { null }
                if (title != null) add(title)
            }
        }

        val warnings = buildList {
            for (i in 0 until minOf(healthRows.length(), 6)) {
                val row = healthRows.optJSONObject(i) ?: continue
                val message = row.optString("message")
                    .ifBlank { row.optString("type") }
                    .ifBlank { null }
                if (message != null) add(message)
            }
        }

        val metrics = listOf(
            MediaArrMetric(if (isMovie) "Movies" else "Series", library.length().toString()),
            MediaArrMetric("Monitored", monitoredCount.toString()),
            MediaArrMetric("Download", queueTotal.toString()),
            MediaArrMetric("Health Issues", healthRows.length().toString()),
            MediaArrMetric("Upcoming", upcoming.length().toString())
        )
        val libraryItems = buildArrLibraryItems(instance, library, isMovie)
        val downloadItems = buildArrDownloadItems(queue)
        val recentHistoryItems = buildArrHistoryItems(history)

        val actions = if (isMovie) {
            listOf(
                MediaArrAction.RADARR_SEARCH_MISSING,
                MediaArrAction.RADARR_RSS_SYNC,
                MediaArrAction.RADARR_REFRESH_INDEX,
                MediaArrAction.RADARR_RESCAN,
                MediaArrAction.RADARR_DOWNLOADED_SCAN,
                MediaArrAction.RADARR_HEALTH_CHECK
            )
        } else {
            listOf(
                MediaArrAction.SONARR_SEARCH_MISSING,
                MediaArrAction.SONARR_RSS_SYNC,
                MediaArrAction.SONARR_REFRESH_INDEX,
                MediaArrAction.SONARR_RESCAN,
                MediaArrAction.SONARR_DOWNLOADED_SCAN,
                MediaArrAction.SONARR_HEALTH_CHECK
            )
        }

        return MediaArrSnapshot(
            serviceType = instance.type,
            serviceLabel = instance.label,
            version = status.optString("version").ifBlank { null },
            status = status.optString("branch").ifBlank { null },
            metrics = metrics,
            downloadItems = downloadItems,
            libraryTitle = "Latest Additions",
            libraryItems = libraryItems,
            recentHistoryItems = recentHistoryItems,
            highlights = highlights,
            warnings = warnings,
            actions = actions
        )
    }

    private fun lidarrSnapshot(instance: ServiceInstance): MediaArrSnapshot {
        val status = requestInstance(instance, "/api/v1/system/status").asJsonObject ?: JSONObject()
        val albums = requestInstance(instance, "/api/v1/album").asJsonArray ?: JSONArray()
        val queue = requestInstance(instance, "/api/v1/queue?page=1&pageSize=20&sortDirection=descending").asJsonObject ?: JSONObject()
        val history = requestInstance(instance, "/api/v1/history?page=1&pageSize=20&sortDirection=descending").asJsonObject ?: JSONObject()
        val healthRows = requestInstance(instance, "/api/v1/health").asJsonArray ?: JSONArray()
        val dateFormatter = SimpleDateFormat("yyyy-MM-dd", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        val now = Date()
        val end = Date(now.time + 14L * 24L * 60L * 60L * 1000L)
        val upcoming = requestInstance(
            instance,
            "/api/v1/calendar?start=${dateFormatter.format(now)}&end=${dateFormatter.format(end)}"
        ).asJsonArray ?: JSONArray()

        val monitoredCount = countWhere(albums) { it.optBoolean("monitored", false) }
        val queueTotal = queue.optInt("totalRecords", queue.optInt("recordsTotal", 0))

        val metrics = listOf(
            MediaArrMetric("Albums", albums.length().toString()),
            MediaArrMetric("Monitored", monitoredCount.toString()),
            MediaArrMetric("Download", queueTotal.toString()),
            MediaArrMetric("Health Issues", healthRows.length().toString()),
            MediaArrMetric("Upcoming", upcoming.length().toString())
        )
        val libraryItems = buildLidarrLibraryItems(instance, albums)
        val downloadItems = buildArrDownloadItems(queue)
        val recentHistoryItems = buildArrHistoryItems(history)

        val highlights = buildList {
            val count = minOf(albums.length(), 6)
            for (i in 0 until count) {
                val row = albums.optJSONObject(i) ?: continue
                val title = row.optString("title").ifBlank { null } ?: continue
                add(title)
            }
        }

        val warnings = buildList {
            for (i in 0 until minOf(healthRows.length(), 6)) {
                val row = healthRows.optJSONObject(i) ?: continue
                val message = row.optString("message").ifBlank { row.optString("type") }
                if (message.isNotBlank()) add(message)
            }
        }

        return MediaArrSnapshot(
            serviceType = instance.type,
            serviceLabel = instance.label,
            version = status.optString("version").ifBlank { null },
            status = status.optString("branch").ifBlank { null },
            metrics = metrics,
            downloadItems = downloadItems,
            libraryTitle = "Latest Albums",
            libraryItems = libraryItems,
            recentHistoryItems = recentHistoryItems,
            highlights = highlights,
            warnings = warnings,
            actions = listOf(
                MediaArrAction.LIDARR_SEARCH_MISSING,
                MediaArrAction.LIDARR_RSS_SYNC,
                MediaArrAction.LIDARR_REFRESH_INDEX,
                MediaArrAction.LIDARR_RESCAN,
                MediaArrAction.LIDARR_DOWNLOADED_SCAN,
                MediaArrAction.LIDARR_HEALTH_CHECK
            )
        )
    }

    private fun buildArrLibraryItems(
        instance: ServiceInstance,
        library: JSONArray,
        isMovie: Boolean
    ): List<MediaArrSearchResultItem> {
        return jsonObjectList(library)
            .sortedWith(
                compareByDescending<JSONObject> { latestSortKey(it) }
                    .thenByDescending { it.optInt("id", 0) }
            )
            .take(12)
            .mapNotNull { row ->
                if (isMovie) buildRadarrLibraryItem(instance, row) else buildSonarrLibraryItem(instance, row)
            }
    }

    private fun buildRadarrLibraryItem(instance: ServiceInstance, row: JSONObject): MediaArrSearchResultItem? {
        val title = firstNonBlank(row.optString("title"), row.optString("titleSlug")) ?: return null
        val year = row.optInt("year", -1).takeIf { it > 0 }
        val subtitle = year?.toString()
        val tmdbId = row.optInt("tmdbId", -1).takeIf { it > 0 }
        val imdbId = row.optString("imdbId").takeIf { it.isNotBlank() }
        val detailsUrl = when {
            tmdbId != null -> "https://www.themoviedb.org/movie/$tmdbId"
            !imdbId.isNullOrBlank() -> "https://www.imdb.com/title/$imdbId/"
            else -> null
        }
        val hasFile = row.optBoolean("hasFile", false)
        val status = if (hasFile) "In Library" else null
        val studio = row.optString("studio").ifBlank { null }
        val runtime = row.optInt("runtime", -1).takeIf { it > 0 }?.let { "$it min" }
        val overview = row.optString("overview").ifBlank { null }
        val added = firstNonBlank(row.optString("added"), row.optString("dateAdded"), row.optString("addedAt"))
        val sizeOnDisk = row.optLong("sizeOnDisk", -1).takeIf { it > 0 }
        val artworkCandidates = extractPosterCandidates(instance, row)
        return MediaArrSearchResultItem(
            id = "movie-${row.optInt("id", 0)}",
            title = title,
            subtitle = subtitle,
            supporting = studio ?: row.optString("status").ifBlank { null },
            status = status,
            posterUrl = artworkCandidates.firstOrNull(),
            posterFallbackUrls = artworkCandidates.drop(1),
            detailsUrl = detailsUrl,
            details = buildSearchDetails(
                "Year" to year?.toString(),
                "Status" to row.optString("status").ifBlank { null },
                "Studio" to studio,
                "Runtime" to runtime,
                "Size" to sizeOnDisk?.let { bytesLabel(it.toDouble()) },
                "Added" to added,
                "Overview" to overview
            )
        )
    }

    private fun buildSonarrLibraryItem(instance: ServiceInstance, row: JSONObject): MediaArrSearchResultItem? {
        val title = firstNonBlank(row.optString("title"), row.optString("sortTitle")) ?: return null
        val year = row.optInt("year", -1).takeIf { it > 0 }
        val network = row.optString("network").ifBlank { null }
        val subtitle = listOfNotNull(year?.toString(), network).joinToString(" • ").takeIf { it.isNotBlank() }
        val tvdbId = row.optInt("tvdbId", -1).takeIf { it > 0 }
        val detailsUrl = tvdbId?.let { "https://thetvdb.com/dereferrer/series/$it" }
        val statistics = row.optJSONObject("statistics")
        val episodeCount = sequenceOf(
            row.optInt("episodeCount", -1).takeIf { it > 0 },
            row.optInt("totalEpisodeCount", -1).takeIf { it > 0 },
            statistics?.optInt("episodeCount", -1)?.takeIf { it > 0 },
            statistics?.optInt("totalEpisodeCount", -1)?.takeIf { it > 0 }
        ).firstOrNull()
        val episodeFileCount = sequenceOf(
            row.optInt("episodeFileCount", -1).takeIf { it >= 0 },
            statistics?.optInt("episodeFileCount", -1)?.takeIf { it >= 0 }
        ).firstOrNull()
        val monitored = row.optBoolean("monitored", false)
        val fullyDownloaded = episodeCount != null && episodeCount > 0 && episodeFileCount != null && episodeFileCount >= episodeCount
        val status = when {
            fullyDownloaded -> "In Library"
            monitored -> "Monitored"
            else -> null
        }
        val runtime = row.optInt("runtime", -1).takeIf { it > 0 }?.let { "$it min" }
        val seasons = row.optInt("seasonCount", -1).takeIf { it > 0 }?.toString()
        val genres = jsonStringArray(row.optJSONArray("genres")).takeIf { it.isNotEmpty() }?.take(3)?.joinToString(", ")
        val overview = row.optString("overview").ifBlank { null }
        val artworkCandidates = extractPosterCandidates(instance, row)
        val supporting = if (episodeCount != null && episodeFileCount != null) {
            "$episodeFileCount/$episodeCount"
        } else {
            row.optString("status").ifBlank { null }
        }
        return MediaArrSearchResultItem(
            id = "series-${row.optInt("id", 0)}",
            title = title,
            subtitle = subtitle,
            supporting = supporting,
            status = status,
            posterUrl = artworkCandidates.firstOrNull(),
            posterFallbackUrls = artworkCandidates.drop(1),
            detailsUrl = detailsUrl,
            details = buildSearchDetails(
                "Year" to year?.toString(),
                "Network" to network,
                "Episodes" to if (episodeCount != null && episodeFileCount != null) "$episodeFileCount/$episodeCount" else null,
                "Seasons" to seasons,
                "Runtime" to runtime,
                "Genres" to genres,
                "Overview" to overview
            )
        )
    }

    private fun buildLidarrLibraryItems(
        instance: ServiceInstance,
        albums: JSONArray
    ): List<MediaArrSearchResultItem> {
        return jsonObjectList(albums)
            .sortedWith(
                compareByDescending<JSONObject> { latestSortKey(it) }
                    .thenByDescending { it.optInt("id", 0) }
            )
            .take(12)
            .mapNotNull { row ->
                val title = firstNonBlank(row.optString("title"), row.optString("albumTitle")) ?: return@mapNotNull null
                val artist = firstNonBlank(row.optString("artistName"), row.optJSONObject("artist")?.optString("artistName"))
                val releaseDate = row.optString("releaseDate").ifBlank { null }
                val formattedReleaseDate = formatMediaReleaseDate(releaseDate)
                val monitored = row.optBoolean("monitored", false)
                val albumType = row.optString("albumType").ifBlank { null }
                val overview = row.optString("overview").ifBlank { null }
                val artworkCandidates = extractPosterCandidates(instance, row)
                MediaArrSearchResultItem(
                    id = "album-${row.optInt("id", 0)}",
                    title = title,
                    subtitle = formattedReleaseDate,
                    supporting = artist,
                    status = if (monitored) "Monitored" else null,
                    posterUrl = artworkCandidates.firstOrNull(),
                    posterFallbackUrls = artworkCandidates.drop(1),
                    detailsUrl = null,
                    details = buildSearchDetails(
                        "Artist" to artist,
                        "Release Date" to formattedReleaseDate,
                        "Type" to albumType,
                        "Overview" to overview
                    )
                )
            }
    }

    private fun buildArrDownloadItems(queue: JSONObject): List<MediaArrDownloadItem> {
        return jsonObjectList(extractRecordsArray(queue))
            .take(12)
            .mapNotNull { row ->
                val title = firstNonBlank(
                    row.optString("title"),
                    row.optString("sourceTitle"),
                    row.optJSONObject("movie")?.optString("title"),
                    row.optJSONObject("series")?.optString("title"),
                    row.optJSONObject("album")?.optString("title"),
                    row.optString("artistName")
                ) ?: return@mapNotNull null
                val totalSize = sequenceOf(
                    row.optLong("size", -1).takeIf { it > 0 },
                    row.optLong("totalSize", -1).takeIf { it > 0 }
                ).firstOrNull()
                val remainingSize = sequenceOf(
                    row.optLong("sizeleft", -1).takeIf { it >= 0 },
                    row.optLong("sizeLeft", -1).takeIf { it >= 0 },
                    row.optLong("remainingSize", -1).takeIf { it >= 0 }
                ).firstOrNull()
                val downloadedSize = if (totalSize != null && remainingSize != null) {
                    (totalSize - remainingSize).coerceAtLeast(0L)
                } else {
                    null
                }
                val progress = when {
                    totalSize != null && remainingSize != null && totalSize > 0L -> {
                        ((totalSize - remainingSize).toDouble() / totalSize.toDouble()).coerceIn(0.0, 1.0)
                    }
                    else -> row.optDouble("progress", Double.NaN)
                        .takeUnless { it.isNaN() }
                        ?.coerceIn(0.0, 1.0)
                }
                val progressLabel = when {
                    downloadedSize != null && totalSize != null -> {
                        "${bytesLabel(downloadedSize.toDouble())} / ${bytesLabel(totalSize.toDouble())}"
                    }
                    totalSize != null -> bytesLabel(totalSize.toDouble())
                    else -> null
                }
                val eta = firstNonBlank(row.optString("timeleft"), row.optString("timeLeft"))
                    ?.takeIf { it.isNotBlank() && !it.equals("00:00:00", true) }
                val status = firstNonBlank(
                    row.optString("status"),
                    row.optString("trackedDownloadStatus"),
                    row.optString("protocol")
                )
                MediaArrDownloadItem(
                    id = row.opt("id")?.toString() ?: title,
                    title = title,
                    progress = progress,
                    progressLabel = progressLabel,
                    trailingLabel = eta,
                    supporting = status
                )
            }
    }

    private fun buildArrHistoryItems(history: JSONObject): List<MediaArrHistoryItem> {
        return jsonObjectList(extractRecordsArray(history))
            .take(20)
            .mapNotNull { row ->
                val title = firstNonBlank(
                    row.optString("sourceTitle"),
                    row.optString("title"),
                    row.optJSONObject("movie")?.optString("title"),
                    row.optJSONObject("series")?.optString("title"),
                    row.optJSONObject("album")?.optString("title"),
                    row.optString("artistName")
                ) ?: return@mapNotNull null
                val subtitle = firstNonBlank(
                    row.optString("eventType"),
                    row.optString("action"),
                    row.optString("data")
                )
                val supporting = firstNonBlank(
                    row.optString("date"),
                    row.optString("added"),
                    row.optString("importedDate")
                )?.let(::compactIsoDate)
                MediaArrHistoryItem(
                    id = row.opt("id")?.toString() ?: title,
                    title = title,
                    subtitle = subtitle,
                    supporting = supporting
                )
            }
    }

    private fun extractRecordsArray(container: JSONObject): JSONArray {
        return container.optJSONArray("records")
            ?: container.optJSONArray("results")
            ?: container.optJSONArray("queueRecords")
            ?: container.optJSONArray("items")
            ?: JSONArray()
    }

    private fun compactIsoDate(value: String): String {
        return runCatching {
            val parser = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            formatter.format(parser.parse(value) ?: return value)
        }.getOrDefault(value)
    }

    private fun jellyseerrSnapshot(instance: ServiceInstance): MediaArrSnapshot {
        val status = requestInstance(instance, "/api/v1/status").asJsonObject ?: JSONObject()
        val requestsObj = requestInstance(instance, "/api/v1/request?take=25&skip=0&sort=added&filter=all").asJsonObject ?: JSONObject()
        val results = requestsObj.optJSONArray("results") ?: JSONArray()

        var pending = 0
        var approved = 0
        var available = 0
        val recent = mutableListOf<String>()
        val recentRequests = mutableListOf<JellyseerrRequestItem>()

        for (i in 0 until results.length()) {
            val row = results.optJSONObject(i) ?: continue
            val id = row.optInt("id", -1)
            val statusValue = row.opt("status")
            val normalized = when (statusValue) {
                is Number -> statusValue.toInt().toString()
                else -> statusValue?.toString().orEmpty().lowercase(Locale.ROOT)
            }
            val statusLabel = jellyseerrStatusLabel(normalized)
            val isPending = normalized == "1" || normalized.contains("pending")
            when (normalized) {
                "1", "pending" -> pending += 1
                "2", "approved", "processing", "4" -> approved += 1
                "5", "available" -> available += 1
            }

            if (recent.size < 8) {
                val media = row.optJSONObject("media")
                val title = media?.optString("title")
                    ?.ifBlank { media.optString("name") }
                    ?.ifBlank { row.optString("subject") }
                    ?.ifBlank { null }
                if (title != null) recent += title
            }

            if (recentRequests.size < 8 && id > 0) {
                val media = row.optJSONObject("media")
                val title = firstNonBlank(
                    media?.optString("title"),
                    media?.optString("name"),
                    row.optString("subject")
                ) ?: "Request #$id"

                val requestedBy = row.optJSONObject("requestedBy")?.let { requestedByObj ->
                    firstNonBlank(
                        requestedByObj.optString("displayName"),
                        requestedByObj.optString("username"),
                        requestedByObj.optString("email")
                    )
                }
                val requestedAt = firstNonBlank(
                    row.optString("createdAt"),
                    row.optString("requestedAt"),
                    row.optString("updatedAt")
                )

                recentRequests += JellyseerrRequestItem(
                    id = id,
                    title = title,
                    status = statusLabel,
                    requestedBy = requestedBy,
                    requestedAt = requestedAt,
                    isPending = isPending
                )
            }
        }

        val total = requestsObj.optInt("totalResults", requestsObj.optInt("total", results.length()))
        val metrics = listOf(
            MediaArrMetric("Requests", total.toString()),
            MediaArrMetric("Pending", pending.toString()),
            MediaArrMetric("Approved", approved.toString()),
            MediaArrMetric("Available", available.toString())
        )

        val actions = buildList {
            if (pending > 0) {
                add(MediaArrAction.JELLYSEERR_APPROVE_PENDING)
                add(MediaArrAction.JELLYSEERR_DECLINE_PENDING)
            }
            add(MediaArrAction.JELLYSEERR_RUN_RECENT_SCAN)
            add(MediaArrAction.JELLYSEERR_RUN_FULL_SCAN)
        }

        return MediaArrSnapshot(
            serviceType = instance.type,
            serviceLabel = instance.label,
            version = status.optString("version").ifBlank { null },
            status = status.optString("commitTag").ifBlank { null },
            details = buildList {
                status.optString("version").ifBlank { null }?.let { add(MediaArrMetric("Version", it)) }
                status.optString("commitTag").ifBlank { null }?.let { add(MediaArrMetric("Commit", it)) }
                firstNonBlank(
                    status.optString("dbType"),
                    status.optJSONObject("appData")?.optString("dbType")
                )?.let { add(MediaArrMetric("DB", it)) }
            },
            metrics = metrics,
            highlights = recent,
            warnings = emptyList(),
            actions = actions,
            jellyseerrRequests = recentRequests
        )
    }

    private fun prowlarrSnapshot(instance: ServiceInstance): MediaArrSnapshot {
        val status = requestInstance(instance, "/api/v1/system/status").asJsonObject ?: JSONObject()
        val indexers = requestInstance(instance, "/api/v1/indexer").asJsonArray ?: JSONArray()
        val health = requestInstance(instance, "/api/v1/health").asJsonArray ?: JSONArray()
        val apps = requestInstance(instance, "/api/v1/applications").asJsonArray ?: JSONArray()
        val historyObj = requestInstance(instance, "/api/v1/history?page=1&pageSize=10&sortDirection=descending").asJsonObject ?: JSONObject()
        val records = historyObj.optJSONArray("records") ?: JSONArray()

        val enabledCount = countWhere(indexers) { it.optBoolean("enable", it.optBoolean("enabled", true)) }
        val unhealthy = countWhere(indexers) {
            val state = it.optString("status").lowercase(Locale.ROOT)
            state.contains("error") || state.contains("down") || state.contains("unhealthy")
        }

        val metrics = listOf(
            MediaArrMetric("Indexers", indexers.length().toString(), "$enabledCount enabled"),
            MediaArrMetric("Applications", apps.length().toString()),
            MediaArrMetric("Health", health.length().toString()),
            MediaArrMetric("Unhealthy", unhealthy.toString())
        )

        val highlights = buildList {
            val count = minOf(records.length(), 8)
            for (i in 0 until count) {
                val row = records.optJSONObject(i) ?: continue
                val eventType = row.optString("eventType").ifBlank { "event" }
                val title = row.optString("sourceTitle").ifBlank { row.optString("title") }
                if (title.isNotBlank()) add("$eventType: $title")
            }
        }

        val warnings = buildList {
            for (i in 0 until minOf(health.length(), 6)) {
                val row = health.optJSONObject(i) ?: continue
                val message = row.optString("message").ifBlank { row.optString("description") }
                if (message.isNotBlank()) add(message)
            }
        }

        return MediaArrSnapshot(
            serviceType = instance.type,
            serviceLabel = instance.label,
            version = status.optString("version").ifBlank { null },
            status = status.optString("branch").ifBlank { null },
            details = buildList {
                status.optString("version").ifBlank { null }?.let { add(MediaArrMetric("Version", it)) }
                status.optString("branch").ifBlank { null }?.let { add(MediaArrMetric("Branch", it)) }
                status.optString("packageVersion").ifBlank { null }?.let { add(MediaArrMetric("Package", it)) }
            },
            metrics = metrics,
            highlights = highlights,
            warnings = warnings,
            actions = listOf(
                MediaArrAction.PROWLARR_TEST_INDEXERS,
                MediaArrAction.PROWLARR_SYNC_APPS,
                MediaArrAction.PROWLARR_HEALTH_CHECK
            )
        )
    }

    private fun bazarrSnapshot(instance: ServiceInstance): MediaArrSnapshot {
        val status = requestInstance(instance, "/api/system/status").asJsonObject ?: JSONObject()
        val badges = requestInstance(instance, "/api/badges").asJsonObject ?: JSONObject()
        val health = requestInstance(instance, "/api/system/health").asJsonArray ?: JSONArray()
        val tasksObj = requestInstance(instance, "/api/system/tasks").asJsonObject ?: JSONObject()
        val tasks = tasksObj.optJSONArray("data") ?: JSONArray()

        val metrics = mutableListOf<MediaArrMetric>()
        listOf("wanted", "missing", "movies", "series", "providers").forEach { key ->
            if (badges.has(key)) {
                val metricValue = badges.opt(key)?.toString().orEmpty()
                metrics += MediaArrMetric(label = key.replaceFirstChar { it.uppercaseChar() }, value = metricValue)
            }
        }
        if (metrics.isEmpty()) {
            metrics += MediaArrMetric("Tasks", tasks.length().toString())
            metrics += MediaArrMetric("Health", health.length().toString())
        }

        val highlights = buildList {
            for (i in 0 until minOf(tasks.length(), 8)) {
                val row = tasks.optJSONObject(i) ?: continue
                val name = row.optString("name").ifBlank { row.optString("task") }
                if (name.isNotBlank()) add(name)
            }
        }

        val warnings = buildList {
            for (i in 0 until minOf(health.length(), 8)) {
                val row = health.optJSONObject(i) ?: continue
                val message = row.optString("message").ifBlank { row.optString("description") }
                if (message.isNotBlank()) add(message)
            }
        }

        return MediaArrSnapshot(
            serviceType = instance.type,
            serviceLabel = instance.label,
            version = status.optString("version").ifBlank { status.optJSONObject("data")?.optString("version") },
            status = null,
            details = buildList {
                firstNonBlank(
                    status.optString("version"),
                    status.optJSONObject("data")?.optString("version")
                )?.let { add(MediaArrMetric("Version", it)) }
                firstNonBlank(
                    status.optString("packageVersion"),
                    status.optJSONObject("data")?.optString("packageVersion")
                )?.let { add(MediaArrMetric("Package", it)) }
                firstNonBlank(
                    status.optString("branch"),
                    status.optJSONObject("data")?.optString("branch")
                )?.let { add(MediaArrMetric("Branch", it)) }
            },
            metrics = metrics,
            highlights = highlights,
            warnings = warnings,
            actions = emptyList()
        )
    }

    private fun gluetunSnapshot(instance: ServiceInstance): MediaArrSnapshot {
        val vpn = requestInstance(instance, "/v1/openvpn/status", expectJson = true)
        val publicIpResponseJson = requestInstance(instance, "/v1/publicip/ip", expectJson = true)
        val publicIpRaw = requestInstance(instance, "/v1/publicip/ip", expectJson = false).body.trim()
        val forwardedResponseJson = requestInstance(instance, "/v1/openvpn/portforwarded", expectJson = true)
        val forwardedRaw = requestInstance(instance, "/v1/openvpn/portforwarded", expectJson = false).body.trim()

        val vpnObj = vpn.asJsonObject ?: JSONObject()
        val publicIpObj = publicIpResponseJson.asJsonObject ?: JSONObject()
        val forwardedObj = forwardedResponseJson.asJsonObject ?: JSONObject()

        val status = firstNonBlank(
            vpnObj.optString("status"),
            vpnObj.optJSONObject("openvpn")?.optString("status")
        )
        val server = firstNonBlank(
            vpnObj.optString("server_name"),
            vpnObj.optJSONObject("openvpn")?.optString("server_name")
        )
        val provider = firstNonBlank(
            vpnObj.optString("provider"),
            vpnObj.optJSONObject("vpn")?.optString("provider")
        )
        val publicIp = firstNonBlank(
            publicIpObj.optString("public_ip"),
            publicIpObj.optString("ip"),
            publicIpRaw
        )
        val forwardedPort = firstNonBlank(
            forwardedObj.optString("port"),
            forwardedObj.optString("port_forwarded"),
            forwardedRaw
        )
        val country = firstNonBlank(
            publicIpObj.optString("country"),
            publicIpObj.optJSONObject("location")?.optString("country")
        )

        val metrics = buildList {
            add(MediaArrMetric("Status", status ?: "Unknown"))
            add(MediaArrMetric("Public IP", publicIp?.ifBlank { "N/A" } ?: "N/A"))
            add(MediaArrMetric("Forwarded Port", forwardedPort?.ifBlank { "N/A" } ?: "N/A"))
            add(MediaArrMetric("Provider", provider ?: "N/A"))
            country?.let { add(MediaArrMetric("Country", it)) }
        }

        val highlights = buildList {
            server?.let { add("Server: $it") }
            country?.let { add("Country: $it") }
        }

        return MediaArrSnapshot(
            serviceType = instance.type,
            serviceLabel = instance.label,
            version = null,
            status = status,
            details = buildList {
                status?.let { add(MediaArrMetric("Status", it)) }
                publicIp?.let { add(MediaArrMetric("IP", it)) }
                server?.let { add(MediaArrMetric("Server", it)) }
            },
            metrics = metrics,
            highlights = highlights,
            warnings = emptyList(),
            actions = listOf(MediaArrAction.GLUETUN_RESTART_VPN)
        )
    }

    private fun flaresolverrSnapshot(instance: ServiceInstance): MediaArrSnapshot {
        val healthObj = requestInstance(instance, "/health").asJsonObject ?: JSONObject()
        val sessionsObj = requestInstance(
            instance,
            "/v1",
            method = "POST",
            body = JSONObject().put("cmd", "sessions.list").toString(),
            extraHeaders = mapOf("Content-Type" to "application/json")
        ).asJsonObject ?: JSONObject()

        val sessionsArray = sessionsObj.optJSONArray("sessions") ?: JSONArray()
        val sessions = List(sessionsArray.length()) { sessionsArray.optString(it) }.filter { it.isNotBlank() }

        val metrics = listOf(
            MediaArrMetric("Sessions", sessions.size.toString()),
            MediaArrMetric("Status", healthObj.optString("status").ifBlank { "unknown" }),
            MediaArrMetric("Message", healthObj.optString("message").ifBlank { healthObj.optString("msg").ifBlank { "N/A" } })
        )

        return MediaArrSnapshot(
            serviceType = instance.type,
            serviceLabel = instance.label,
            version = healthObj.optString("version").ifBlank { null },
            status = healthObj.optString("status").ifBlank { null },
            details = buildList {
                healthObj.optString("version").ifBlank { null }?.let { add(MediaArrMetric("Version", it)) }
                firstNonBlank(
                    healthObj.optString("userAgent"),
                    healthObj.optString("user_agent")
                )?.let { add(MediaArrMetric("User Agent", it)) }
                firstNonBlank(
                    healthObj.optString("message"),
                    healthObj.optString("msg")
                )?.let { add(MediaArrMetric("Message", it)) }
            },
            metrics = metrics,
            highlights = sessions.take(8),
            warnings = emptyList(),
            actions = listOf(MediaArrAction.FLARESOLVERR_CREATE_SESSION),
            flaresolverrSessions = sessions
        )
    }

    private fun addRadarrMovie(
        instance: ServiceInstance,
        item: MediaArrSearchResultItem,
        selection: MediaArrRequestSelection? = null
    ) {
        val tmdbId = item.requestId?.toIntOrNull()
            ?: throw IllegalStateException("Missing TMDB id for Radarr request")
        val configuration = buildRadarrRequestConfiguration(instance, item.title)
        val resolvedSelection = resolveRequestSelection(configuration, selection)
        val qualityProfileId = resolvedSelection.qualityProfile?.id
            ?: throw IllegalStateException("No Radarr quality profile configured")
        val rootFolderPath = resolvedSelection.rootFolder?.path
            ?: throw IllegalStateException("No Radarr root folder configured")

        val payload = JSONObject()
            .put("title", item.title)
            .put("tmdbId", tmdbId)
            .put("qualityProfileId", qualityProfileId)
            .put("rootFolderPath", rootFolderPath)
            .put("monitored", true)
            .put("minimumAvailability", "released")
            .put("addOptions", JSONObject().put("searchForMovie", true))

        requestInstance(
            instance = instance,
            path = "/api/v3/movie",
            method = "POST",
            body = payload.toString(),
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
    }

    private fun addSonarrSeries(
        instance: ServiceInstance,
        item: MediaArrSearchResultItem,
        selection: MediaArrRequestSelection? = null
    ) {
        val tvdbId = item.requestId?.toIntOrNull()
            ?: throw IllegalStateException("Missing TVDB id for Sonarr request")
        val configuration = buildSonarrRequestConfiguration(instance, item.title)
        val resolvedSelection = resolveRequestSelection(configuration, selection)
        val qualityProfileId = resolvedSelection.qualityProfile?.id
            ?: throw IllegalStateException("No Sonarr quality profile configured")
        val rootFolderPath = resolvedSelection.rootFolder?.path
            ?: throw IllegalStateException("No Sonarr root folder configured")
        val languageProfileId = resolvedSelection.languageProfile?.id

        val payload = JSONObject()
            .put("title", item.title)
            .put("tvdbId", tvdbId)
            .put("qualityProfileId", qualityProfileId)
            .put("rootFolderPath", rootFolderPath)
            .put("monitored", true)
            .put("seasonFolder", true)
            .put("addOptions", JSONObject().put("searchForMissingEpisodes", true))
        if (languageProfileId != null) {
            payload.put("languageProfileId", languageProfileId)
        }

        requestInstance(
            instance = instance,
            path = "/api/v3/series",
            method = "POST",
            body = payload.toString(),
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
    }

    private fun addLidarrArtist(
        instance: ServiceInstance,
        item: MediaArrSearchResultItem,
        selection: MediaArrRequestSelection? = null
    ) {
        val foreignArtistId = item.requestId?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Missing artist id for Lidarr request")
        val configuration = buildLidarrRequestConfiguration(instance, item.title)
        val resolvedSelection = resolveRequestSelection(configuration, selection)
        val qualityProfileId = resolvedSelection.qualityProfile?.id
            ?: throw IllegalStateException("No Lidarr quality profile configured")
        val metadataProfileId = resolvedSelection.metadataProfile?.id
            ?: throw IllegalStateException("No Lidarr metadata profile configured")
        val rootFolderPath = resolvedSelection.rootFolder?.path
            ?: throw IllegalStateException("No Lidarr root folder configured")

        val payload = JSONObject()
            .put("artistName", item.title)
            .put("foreignArtistId", foreignArtistId)
            .put("qualityProfileId", qualityProfileId)
            .put("metadataProfileId", metadataProfileId)
            .put("rootFolderPath", rootFolderPath)
            .put("monitored", true)
            .put("addOptions", JSONObject().put("searchForMissingAlbums", true))

        requestInstance(
            instance = instance,
            path = "/api/v1/artist",
            method = "POST",
            body = payload.toString(),
            extraHeaders = mapOf("Content-Type" to "application/json")
        )
    }

    private fun requestInstance(
        instance: ServiceInstance,
        path: String,
        method: String = "GET",
        body: String? = null,
        extraHeaders: Map<String, String> = emptyMap(),
        expectJson: Boolean = true
    ): RawResponse {
        return requestRaw(
            baseUrl = instance.url,
            path = path,
            method = method,
            headers = extraHeaders + mapOf("X-Homelab-Instance-Id" to instance.id),
            body = body,
            bypass = false,
            expectJson = expectJson
        )
    }

    private fun runArrCommand(instance: ServiceInstance, apiVersion: Int, candidates: List<String>) {
        var lastError: Throwable? = null
        for (name in candidates) {
            try {
                requestInstance(
                    instance,
                    path = "/api/v$apiVersion/command",
                    method = "POST",
                    body = JSONObject().put("name", name).toString(),
                    extraHeaders = mapOf("Content-Type" to "application/json")
                )
                return
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Failed to run command")
    }

    private fun runProwlarrCommand(instance: ServiceInstance, candidates: List<String>, path: String) {
        var lastError: Throwable? = null
        for (name in candidates) {
            try {
                requestInstance(
                    instance,
                    path = path,
                    method = "POST",
                    body = JSONObject().put("name", name).toString(),
                    extraHeaders = mapOf("Content-Type" to "application/json")
                )
                return
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Failed to run command")
    }

    private fun runProwlarrIndexerTest(instance: ServiceInstance) {
        var lastError: Throwable? = null
        listOf("/api/v1/indexer/testall", "/api/v1/indexer/test").forEach { path ->
            try {
                requestInstance(instance = instance, path = path, method = "POST")
                return
            } catch (error: Throwable) {
                lastError = error
            }
        }

        try {
            runProwlarrCommand(
                instance = instance,
                candidates = listOf("TestAllIndexers", "IndexerSync", "ApplicationIndexerSync"),
                path = "/api/v1/command"
            )
            return
        } catch (error: Throwable) {
            lastError = error
        }

        throw lastError ?: IllegalStateException("Failed to trigger Prowlarr indexer test")
    }

    private fun oldestPendingJellyseerrRequest(instance: ServiceInstance): Pair<Int, String?> {
        val requestsObj = requestInstance(
            instance,
            "/api/v1/request?take=50&skip=0&sort=added&filter=all"
        ).asJsonObject ?: JSONObject()
        val rows = requestsObj.optJSONArray("results") ?: JSONArray()

        for (i in 0 until rows.length()) {
            val row = rows.optJSONObject(i) ?: continue
            val id = row.optInt("id", -1)
            if (id <= 0) continue
            val statusValue = row.opt("status")
            val normalized = when (statusValue) {
                is Number -> statusValue.toInt().toString()
                else -> statusValue?.toString().orEmpty().lowercase(Locale.ROOT)
            }
            val pending = normalized == "1" || normalized.contains("pending")
            if (!pending) continue

            val media = row.optJSONObject("media")
            val title = firstNonBlank(
                media?.optString("title"),
                media?.optString("name"),
                row.optString("subject")
            )
            return id to title
        }

        throw IllegalStateException("No pending Jellyseerr requests found")
    }

    private fun runJellyseerrJob(instance: ServiceInstance, keywordCandidates: List<String>): String {
        val jobsObj = requestInstance(instance, "/api/v1/settings/jobs").asJsonObject ?: JSONObject()
        val jobsArray = jobsObj.optJSONArray("jobs")
            ?: jobsObj.optJSONArray("results")
            ?: jobsObj.optJSONArray("data")
            ?: JSONArray()

        if (jobsArray.length() == 0) {
            throw IllegalStateException("No jobs available")
        }

        val normalizedKeywords = keywordCandidates.map { it.lowercase(Locale.ROOT) }
        var fallbackJob: JSONObject? = null
        var runnableFallbackJob: JSONObject? = null
        var keywordFallbackJob: JSONObject? = null

        for (i in 0 until jobsArray.length()) {
            val job = jobsArray.optJSONObject(i) ?: continue
            if (fallbackJob == null) fallbackJob = job
            if (runnableFallbackJob == null && isJellyseerrJobRunnable(job)) {
                runnableFallbackJob = job
            }
            val name = firstNonBlank(
                job.optString("name"),
                job.optString("type"),
                job.optString("id")
            )?.lowercase(Locale.ROOT).orEmpty()
            if (normalizedKeywords.any { keyword -> keyword.isNotBlank() && name.contains(keyword) }) {
                if (keywordFallbackJob == null) keywordFallbackJob = job
                if (isJellyseerrJobRunnable(job)) {
                    return runJellyseerrJobById(instance, job)
                }
            }
        }

        return runJellyseerrJobById(
            instance,
            keywordFallbackJob
                ?: runnableFallbackJob
                ?: fallbackJob
                ?: throw IllegalStateException("No runnable jobs found")
        )
    }

    private fun runJellyseerrJobById(instance: ServiceInstance, job: JSONObject): String {
        val jobId = firstNonBlank(
            job.opt("id")?.toString(),
            job.optString("id"),
            job.optString("jobId")
        )?.trim()
            ?: throw IllegalStateException("Missing Jellyseerr job id")

        requestInstance(
            instance = instance,
            path = "/api/v1/settings/jobs/$jobId/run",
            method = "POST"
        )

        return firstNonBlank(job.optString("name"), job.optString("type"), jobId) ?: jobId
    }

    private fun runGluetunRestart(instance: ServiceInstance) {
        var lastError: Throwable? = null
        listOf("POST", "PUT").forEach { method ->
            try {
                requestInstance(
                    instance = instance,
                    path = "/v1/openvpn/restart",
                    method = method
                )
                return
            } catch (error: Throwable) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("Failed to restart Gluetun VPN")
    }

    private fun isJellyseerrJobRunnable(job: JSONObject): Boolean {
        val enabled = when {
            job.has("enabled") -> job.optBoolean("enabled", true)
            job.has("isEnabled") -> job.optBoolean("isEnabled", true)
            else -> true
        }
        val running = when {
            job.has("running") -> job.optBoolean("running", false)
            job.has("isRunning") -> job.optBoolean("isRunning", false)
            else -> false
        }
        val status = job.optString("status").lowercase(Locale.ROOT)
        val isStatusRunning = status.contains("running") || status.contains("in_progress")
        return enabled && !running && !isStatusRunning
    }

    private fun buildRadarrRequestConfiguration(
        instance: ServiceInstance,
        title: String
    ): MediaArrRequestConfiguration {
        return MediaArrRequestConfiguration(
            title = title,
            qualityProfiles = requestOptionsFromEndpoint(instance, "/api/v3/qualityprofile"),
            rootFolders = requestOptionsFromEndpoint(instance, "/api/v3/rootfolder")
        )
    }

    private fun buildSonarrRequestConfiguration(
        instance: ServiceInstance,
        title: String
    ): MediaArrRequestConfiguration {
        return MediaArrRequestConfiguration(
            title = title,
            qualityProfiles = requestOptionsFromEndpoint(instance, "/api/v3/qualityprofile"),
            rootFolders = requestOptionsFromEndpoint(instance, "/api/v3/rootfolder"),
            languageProfiles = requestOptionsFromEndpoint(instance, "/api/v3/languageprofile")
        )
    }

    private fun buildLidarrRequestConfiguration(
        instance: ServiceInstance,
        title: String
    ): MediaArrRequestConfiguration {
        return MediaArrRequestConfiguration(
            title = title,
            qualityProfiles = requestOptionsFromEndpoint(instance, "/api/v1/qualityprofile"),
            rootFolders = requestOptionsFromEndpoint(instance, "/api/v1/rootfolder"),
            metadataProfiles = requestOptionsFromEndpoint(instance, "/api/v1/metadataprofile")
        )
    }

    private fun resolveRequestSelection(
        configuration: MediaArrRequestConfiguration,
        selection: MediaArrRequestSelection?
    ): MediaArrRequestSelection {
        if (configuration.requiresExplicitSelection && selection == null) {
            throw MediaArrRequestConfigurationRequiredException(configuration)
        }

        return MediaArrRequestSelection(
            qualityProfile = selectRequestOption(configuration.qualityProfiles, selection?.qualityProfile, configuration),
            rootFolder = selectRequestOption(configuration.rootFolders, selection?.rootFolder, configuration),
            languageProfile = selectRequestOption(configuration.languageProfiles, selection?.languageProfile, configuration),
            metadataProfile = selectRequestOption(configuration.metadataProfiles, selection?.metadataProfile, configuration)
        )
    }

    private fun selectRequestOption(
        options: List<MediaArrRequestOption>,
        selected: MediaArrRequestOption?,
        configuration: MediaArrRequestConfiguration
    ): MediaArrRequestOption? {
        if (options.isEmpty()) return null
        selected?.let { candidate ->
            options.firstOrNull { option ->
                option.key == candidate.key ||
                    (option.id != null && option.id == candidate.id) ||
                    (!option.path.isNullOrBlank() && option.path == candidate.path)
            }?.let { return it }
        }
        if (options.size == 1) return options.first()
        throw MediaArrRequestConfigurationRequiredException(configuration)
    }

    private fun requestOptionsFromEndpoint(instance: ServiceInstance, path: String): List<MediaArrRequestOption> {
        val rows = requestInstance(instance, path).asJsonArray ?: JSONArray()
        val options = ArrayList<MediaArrRequestOption>(rows.length())
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optInt("id", -1).takeIf { it > 0 }
            val folderPath = firstNonBlank(
                row.optString("path"),
                row.optString("defaultPath"),
                row.optString("rootFolderPath")
            )
            val label = firstNonBlank(
                row.optString("name"),
                row.optString("title"),
                row.optString("language"),
                row.optString("profileName"),
                row.optString("implementation"),
                folderPath,
                id?.toString()
            ) ?: continue
            val key = buildString {
                append(path)
                append(":")
                append(id?.toString() ?: folderPath ?: label)
            }
            options += MediaArrRequestOption(
                key = key,
                label = label,
                id = id,
                path = folderPath
            )
        }
        return options
    }

    private fun firstIdFromEndpoint(instance: ServiceInstance, path: String): Int? {
        val rows = requestInstance(instance, path).asJsonArray ?: JSONArray()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val id = row.optInt("id", -1)
            if (id > 0) return id
        }
        return null
    }

    private fun firstPathFromEndpoint(instance: ServiceInstance, path: String): String? {
        val rows = requestInstance(instance, path).asJsonArray ?: JSONArray()
        for (index in 0 until rows.length()) {
            val row = rows.optJSONObject(index) ?: continue
            val folderPath = firstNonBlank(
                row.optString("path"),
                row.optString("defaultPath"),
                row.optString("rootFolderPath")
            )
            if (!folderPath.isNullOrBlank()) return folderPath
        }
        return null
    }

    private fun extractPosterUrl(instance: ServiceInstance, row: JSONObject): String? {
        return extractPosterCandidates(instance, row).firstOrNull()
    }

    private fun extractProwlarrPosterUrl(instance: ServiceInstance, row: JSONObject): String? {
        return extractProwlarrPosterCandidates(instance, row).firstOrNull()
    }

    private fun extractPosterCandidates(instance: ServiceInstance, row: JSONObject): List<String> {
        val preferred = LinkedHashSet<String>()
        val fallback = LinkedHashSet<String>()

        collectDirectArtworkCandidates(instance, row, preferred)
        collectJsonArtworkCandidates(instance, row.optJSONArray("images"), preferred, fallback)
        listOf("movie", "series", "artist", "album", "mediaInfo", "media").forEach { nestedKey ->
            row.optJSONObject(nestedKey)?.let { nested ->
                collectDirectArtworkCandidates(instance, nested, preferred)
                collectJsonArtworkCandidates(instance, nested.optJSONArray("images"), preferred, fallback)
            }
        }

        return (preferred + fallback).toList()
    }

    private fun extractProwlarrPosterCandidates(instance: ServiceInstance, row: JSONObject): List<String> {
        val preferred = LinkedHashSet<String>()
        val fallback = LinkedHashSet<String>()
        collectDirectArtworkCandidates(instance, row, preferred)
        collectJsonArtworkCandidates(instance, row.optJSONArray("images"), preferred, fallback)
        return (preferred + fallback).toList()
    }

    private fun collectDirectArtworkCandidates(
        instance: ServiceInstance,
        row: JSONObject,
        target: LinkedHashSet<String>
    ) {
        listOf(
            row.optString("poster"),
            row.optString("posterUrl"),
            row.optString("posterURL"),
            row.optString("remoteUrl"),
            row.optString("remotePoster"),
            row.optString("image"),
            row.optString("imageUrl"),
            row.optString("cover"),
            row.optString("coverUrl"),
            row.optString("thumbnail"),
            row.optString("thumbnailUrl"),
            row.optString("posterPath").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500${if (it.startsWith("/")) it else "/$it"}" },
            row.optString("poster_path").takeIf { it.isNotBlank() }?.let { "https://image.tmdb.org/t/p/w500${if (it.startsWith("/")) it else "/$it"}" }
        ).forEach { candidate ->
            resolveServiceArtworkUrl(instance, candidate)?.let(target::add)
        }
    }

    private fun collectJsonArtworkCandidates(
        instance: ServiceInstance,
        images: JSONArray?,
        preferred: LinkedHashSet<String>,
        fallback: LinkedHashSet<String>
    ) {
        if (images == null) return
        for (index in 0 until images.length()) {
            when (val item = images.opt(index)) {
                is JSONObject -> {
                    val resolved = listOf(
                        item.optString("remoteUrl"),
                        item.optString("url"),
                        item.optString("src"),
                        item.optString("link"),
                        item.optString("href"),
                        item.optString("imageUrl"),
                        item.optString("posterUrl"),
                        item.optString("cover")
                    ).firstNotNullOfOrNull { resolveServiceArtworkUrl(instance, it) }
                    if (resolved != null) {
                        val coverType = item.optString("coverType").lowercase(Locale.ROOT)
                        if (coverType in setOf("poster", "cover", "posterbanner", "banner", "fanart", "screenshot")) {
                            preferred += resolved
                        } else {
                            fallback += resolved
                        }
                    }
                }
                is String -> resolveServiceArtworkUrl(instance, item)?.let(fallback::add)
            }
        }
    }

    private fun buildSearchDetails(vararg pairs: Pair<String, String?>): List<MediaArrMetric> {
        val details = ArrayList<MediaArrMetric>(pairs.size)
        for ((label, raw) in pairs) {
            val value = raw?.trim().orEmpty()
            if (value.isNotBlank()) {
                details += MediaArrMetric(label = label, value = value)
            }
        }
        return details
    }

    private fun jsonStringArray(array: JSONArray?): List<String> {
        if (array == null || array.length() == 0) return emptyList()
        val items = ArrayList<String>(array.length())
        for (index in 0 until array.length()) {
            val value = array.optString(index).trim()
            if (value.isNotBlank()) {
                items += value
            }
        }
        return items
    }

    private fun jsonObjectList(array: JSONArray): List<JSONObject> {
        val items = ArrayList<JSONObject>(array.length())
        for (index in 0 until array.length()) {
            array.optJSONObject(index)?.let(items::add)
        }
        return items
    }

    private fun latestSortKey(row: JSONObject): Long {
        val candidates = listOf(
            row.optString("added"),
            row.optString("dateAdded"),
            row.optString("addedAt"),
            row.optString("releaseDate")
        ).filter { it.isNotBlank() }

        for (candidate in candidates) {
            runCatching { java.time.Instant.parse(candidate).toEpochMilli() }.getOrNull()?.let { return it }
            runCatching { java.time.OffsetDateTime.parse(candidate).toInstant().toEpochMilli() }.getOrNull()?.let { return it }
            runCatching {
                java.time.LocalDate.parse(candidate).atStartOfDay(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
            }.getOrNull()?.let { return it }
        }

        return row.optLong("id", 0L)
    }

    private fun formatMediaReleaseDate(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null

        runCatching {
            val instant = java.time.Instant.parse(value)
            val formatter = java.time.format.DateTimeFormatter
                .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
                .withZone(java.time.ZoneId.systemDefault())
            formatter.format(instant)
        }.getOrNull()?.let { return it }

        runCatching {
            val date = java.time.LocalDate.parse(value)
            val formatter = java.time.format.DateTimeFormatter
                .ofLocalizedDate(java.time.format.FormatStyle.MEDIUM)
                .withLocale(Locale.getDefault())
            formatter.format(date)
        }.getOrNull()?.let { return it }

        return value
    }

    private fun toAbsoluteUrl(baseUrl: String, raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.startsWith("http://") || value.startsWith("https://")) return value
        if (value.startsWith("magnet:", ignoreCase = true)) return value
        return cleanUrl(baseUrl) + if (value.startsWith("/")) value else "/$value"
    }

    private fun resolveServiceArtworkUrl(instance: ServiceInstance, raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        if (value.startsWith("http://") || value.startsWith("https://")) {
            val isServiceHosted = isServiceHostedArtworkUrl(value, instance.url)
                || isServiceHostedArtworkUrl(value, instance.fallbackUrl)
            return if (isServiceHosted) {
                appendArtworkApiKey(value, instance.apiKey)
            } else {
                value
            }
        }
        if (value.startsWith("magnet:", ignoreCase = true)) return value
        val absolute = cleanUrl(instance.url) + if (value.startsWith("/")) value else "/$value"
        return appendArtworkApiKey(absolute, instance.apiKey)
    }

    private fun isServiceHostedArtworkUrl(raw: String, baseUrl: String?): Boolean {
        val base = baseUrl?.trim().orEmpty()
        if (base.isBlank()) return false
        val artworkUri = Uri.parse(raw)
        val serviceUri = Uri.parse(base)
        val artworkHost = artworkUri.host?.lowercase(Locale.ROOT)
        val serviceHost = serviceUri.host?.lowercase(Locale.ROOT)
        if (artworkHost != serviceHost) return false
        return effectivePort(artworkUri) == effectivePort(serviceUri)
    }

    private fun appendArtworkApiKey(raw: String, apiKey: String?): String {
        val key = apiKey?.trim().orEmpty()
        if (key.isBlank()) return raw
        val uri = Uri.parse(raw)
        if (uri.getQueryParameter("apikey") != null) return raw
        return uri.buildUpon()
            .appendQueryParameter("apikey", key)
            .build()
            .toString()
    }

    private fun effectivePort(uri: Uri): Int {
        val explicitPort = uri.port
        if (explicitPort > 0) return explicitPort
        return when (uri.scheme?.lowercase(Locale.ROOT)) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
    }

    private fun countWhere(array: JSONArray, predicate: (JSONObject) -> Boolean): Int {
        var total = 0
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            if (predicate(row)) total += 1
        }
        return total
    }

    private fun jellyseerrStatusLabel(rawStatus: Any?): String {
        val normalizedStatus = when (rawStatus) {
            is Number -> rawStatus.toInt().toString()
            else -> rawStatus?.toString()?.lowercase(Locale.ROOT).orEmpty()
        }
        return when (normalizedStatus) {
            "1", "pending" -> "Pending"
            "2", "approved" -> "Approved"
            "3", "declined" -> "Declined"
            "4", "processing" -> "Processing"
            "5", "available" -> "Available"
            else -> normalizedStatus.ifBlank { "Unknown" }
        }
    }

    private fun parseQbittorrentItems(array: JSONArray): List<QbittorrentTorrentItem> {
        val items = ArrayList<QbittorrentTorrentItem>(array.length())
        for (i in 0 until array.length()) {
            val row = array.optJSONObject(i) ?: continue
            val hash = row.optString("hash")
            if (hash.isBlank()) continue
            val name = row.optString("name").ifBlank { hash.take(8) }
            val state = row.optString("state")
            val ratioValue = if (row.has("ratio")) row.optDouble("ratio") else Double.NaN
            val ratio = ratioValue.takeUnless { it.isNaN() || it.isInfinite() }
            val seeds = row.optInt("num_seeds", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE || it < 0 }
            val leechers = row.optInt("num_leechs", Int.MIN_VALUE).takeUnless { it == Int.MIN_VALUE || it < 0 }
            val category = row.optString("category").takeIf { it.isNotBlank() }
            val tags = row.optString("tags").takeIf { it.isNotBlank() }
            val eta = row.optLong("eta", -1).takeIf { it >= 0 } ?: 0L

            items += QbittorrentTorrentItem(
                hash = hash,
                name = name,
                state = state,
                progress = row.optDouble("progress", 0.0),
                downloadedBytes = row.optLong("downloaded", 0L),
                totalSizeBytes = row.optLong("size", 0L),
                downloadSpeedBytes = row.optLong("dlspeed", 0L),
                uploadSpeedBytes = row.optLong("upspeed", 0L),
                etaSeconds = eta,
                ratio = ratio,
                seeds = seeds,
                leechers = leechers,
                category = category,
                tags = tags
            )
        }
        return items
    }

    private fun speedLabel(bytesPerSecond: Long): String {
        if (bytesPerSecond <= 0) return "0 B/s"
        val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
        var value = bytesPerSecond.toDouble()
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }

    private fun bytesLabel(bytes: Double): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes
        var unitIndex = 0
        while (value >= 1024 && unitIndex < units.lastIndex) {
            value /= 1024.0
            unitIndex += 1
        }
        return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
    }

    private fun requestRaw(
        baseUrl: String,
        path: String,
        method: String,
        headers: Map<String, String>,
        body: String? = null,
        bypass: Boolean,
        expectJson: Boolean = true
    ): RawResponse {
        val cleanBase = cleanUrl(baseUrl)
        val targetPath = if (path.startsWith("/")) path else "/$path"
        val requestBuilder = Request.Builder()
            .url(cleanBase + targetPath)

        if (bypass) {
            requestBuilder.addHeader("X-Homelab-Bypass", "true")
        }

        headers.forEach { (key, value) -> requestBuilder.addHeader(key, value) }

        val requestMethod = method.uppercase(Locale.ROOT)
        val mediaType = (headers["Content-Type"] ?: "application/json; charset=utf-8").toMediaType()
        val requestBody = body?.toRequestBody(mediaType)

        val request = when (requestMethod) {
            "GET" -> requestBuilder.get().build()
            "POST" -> requestBuilder.post(requestBody ?: ByteArray(0).toRequestBody(mediaType)).build()
            "PUT" -> requestBuilder.put(requestBody ?: ByteArray(0).toRequestBody(mediaType)).build()
            "PATCH" -> requestBuilder.patch(requestBody ?: ByteArray(0).toRequestBody(mediaType)).build()
            "DELETE" -> {
                if (requestBody != null) {
                    requestBuilder.delete(requestBody).build()
                } else {
                    requestBuilder.delete().build()
                }
            }
            else -> requestBuilder.method(requestMethod, requestBody).build()
        }

        okHttpClient.newCall(request).execute().use { response ->
            val responseBody = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IllegalStateException("${response.code}: ${response.message}")
            }
            return RawResponse.fromBody(responseBody, expectJson = expectJson)
        }
    }

    private fun cleanUrl(url: String): String {
        var clean = url.trim()
        clean = clean.trimEnd { it == ')' || it == ']' || it == '}' || it == ',' || it == ';' }
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "")
    }

    private fun parseSidFromSetCookie(setCookie: String): String? {
        val marker = "SID="
        val start = setCookie.indexOf(marker)
        if (start < 0) return null
        val valueStart = start + marker.length
        val nextSep = setCookie.indexOf(';', valueStart).takeIf { it >= 0 } ?: setCookie.length
        return setCookie.substring(valueStart, nextSep).trim().ifBlank { null }
    }

    private fun urlEncode(value: String): String {
        return java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
    }

    private fun firstNonBlank(vararg values: String?): String? {
        values.forEach { value ->
            if (!value.isNullOrBlank()) return value
        }
        return null
    }

    private data class RawResponse(
        val body: String,
        val asJsonObject: JSONObject?,
        val asJsonArray: JSONArray?
    ) {
        companion object {
            fun fromBody(body: String, expectJson: Boolean): RawResponse {
                if (!expectJson) {
                    return RawResponse(body = body, asJsonObject = null, asJsonArray = null)
                }
                val trimmed = body.trim()
                return when {
                    trimmed.startsWith("{") -> RawResponse(body = body, asJsonObject = JSONObject(trimmed), asJsonArray = null)
                    trimmed.startsWith("[") -> RawResponse(body = body, asJsonObject = null, asJsonArray = JSONArray(trimmed))
                    else -> RawResponse(body = body, asJsonObject = null, asJsonArray = null)
                }
            }
        }
    }
}
