package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.PatchmonApi
import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.dto.patchmon.PatchmonAgentQueueResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonDeleteResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostInfo
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostNetwork
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostStats
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostSystem
import com.homelab.app.data.remote.dto.patchmon.PatchmonHostsResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonIntegrationsResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonNotesResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonPackagesResponse
import com.homelab.app.data.remote.dto.patchmon.PatchmonReportsResponse
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.HttpException

class PatchmonApiException(
    val kind: Kind,
    override val cause: Throwable? = null
) : Exception(kind.name, cause) {
    enum class Kind {
        BAD_REQUEST,
        INVALID_HOST_ID,
        DELETE_CONSTRAINT,
        INVALID_CREDENTIALS,
        IP_NOT_ALLOWED,
        ACCESS_DENIED,
        FORBIDDEN,
        HOST_NOT_FOUND,
        NOT_FOUND,
        RATE_LIMITED,
        SERVER_ERROR,
        CONNECTION_ERROR
    }
}

@Singleton
class PatchmonRepository @Inject constructor(
    private val api: PatchmonApi,
    private val tlsClientSelector: TlsClientSelector
) {

    private data class HostsCacheEntry(
        val response: PatchmonHostsResponse,
        val savedAtMs: Long
    )

    private val maxRateLimitAttempts = 5
    private val hostsCacheTtlMs = 5 * 60_000L
    private val hostsCache = ConcurrentHashMap<String, HostsCacheEntry>()

    suspend fun authenticate(url: String, tokenKey: String, tokenSecret: String, allowSelfSigned: Boolean = false) {
        withContext(Dispatchers.IO) {
            val clean = url.trimEnd('/')
            val credentials = Base64.getEncoder()
                .encodeToString("${tokenKey.trim()}:${tokenSecret}".toByteArray(Charsets.UTF_8))
            val request = Request.Builder()
                .url("$clean/api/v1/api/hosts?include=stats")
                .addHeader("Authorization", "Basic $credentials")
                .addHeader("Content-Type", "application/json")
                .build()

            tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
                if (response.isSuccessful) return@use
                val body = response.body?.string().orEmpty()
                throw mapHttpStatus(response.code, body)
            }
        }
    }

    suspend fun getHosts(instanceId: String, hostGroup: String? = null): PatchmonHostsResponse {
        val normalizedGroup = hostGroup?.trim()?.takeIf { it.isNotEmpty() }
        val response = executeWithBackoff {
            api.getHosts(instanceId = instanceId, hostGroup = normalizedGroup)
        }
        hostsCache[hostsCacheKey(instanceId, normalizedGroup)] = HostsCacheEntry(
            response = response,
            savedAtMs = System.currentTimeMillis()
        )
        return response
    }

    suspend fun getHostInfo(instanceId: String, hostId: String): PatchmonHostInfo {
        return executeWithBackoff { api.getHostInfo(instanceId = instanceId, hostId = hostId) }
    }

    suspend fun getHostStats(instanceId: String, hostId: String): PatchmonHostStats {
        return executeWithBackoff { api.getHostStats(instanceId = instanceId, hostId = hostId) }
    }

    suspend fun getHostSystem(instanceId: String, hostId: String): PatchmonHostSystem {
        return executeWithBackoff { api.getHostSystem(instanceId = instanceId, hostId = hostId) }
    }

    suspend fun getHostNetwork(instanceId: String, hostId: String): PatchmonHostNetwork {
        return executeWithBackoff { api.getHostNetwork(instanceId = instanceId, hostId = hostId) }
    }

    suspend fun getHostPackages(instanceId: String, hostId: String, updatesOnly: Boolean): PatchmonPackagesResponse {
        return executeWithBackoff {
            api.getHostPackages(
                instanceId = instanceId,
                hostId = hostId,
                updatesOnly = if (updatesOnly) true else null
            )
        }
    }

    suspend fun getHostReports(instanceId: String, hostId: String, limit: Int = 10): PatchmonReportsResponse {
        return executeWithBackoff {
            api.getHostReports(instanceId = instanceId, hostId = hostId, limit = limit.coerceAtLeast(1))
        }
    }

    suspend fun getHostAgentQueue(instanceId: String, hostId: String, limit: Int = 10): PatchmonAgentQueueResponse {
        return executeWithBackoff {
            api.getHostAgentQueue(instanceId = instanceId, hostId = hostId, limit = limit.coerceAtLeast(1))
        }
    }

    suspend fun getHostNotes(instanceId: String, hostId: String): PatchmonNotesResponse {
        return executeWithBackoff { api.getHostNotes(instanceId = instanceId, hostId = hostId) }
    }

    suspend fun getHostIntegrations(instanceId: String, hostId: String): PatchmonIntegrationsResponse {
        return executeWithBackoff { api.getHostIntegrations(instanceId = instanceId, hostId = hostId) }
    }

    suspend fun deleteHost(instanceId: String, hostId: String): PatchmonDeleteResponse {
        val response = executeWithBackoff { api.deleteHost(instanceId = instanceId, hostId = hostId) }
        hostsCache.keys.removeIf { key -> key.startsWith("$instanceId|") }
        return response
    }

    fun peekHosts(instanceId: String, hostGroup: String? = null): PatchmonHostsResponse? {
        val normalizedGroup = hostGroup?.trim()?.takeIf { it.isNotEmpty() }
        val key = hostsCacheKey(instanceId, normalizedGroup)
        val cached = hostsCache[key] ?: return null
        if (System.currentTimeMillis() - cached.savedAtMs > hostsCacheTtlMs) {
            hostsCache.remove(key)
            return null
        }
        return cached.response
    }

    private fun hostsCacheKey(instanceId: String, hostGroup: String?): String {
        return "$instanceId|${hostGroup.orEmpty()}"
    }

    private suspend fun <T> executeWithBackoff(block: suspend () -> T): T {
        var lastError: Throwable? = null

        repeat(maxRateLimitAttempts) { attempt ->
            try {
                return block()
            } catch (error: Throwable) {
                lastError = error
                when (error) {
                    is HttpException -> {
                        if (error.code() == 429 && attempt < maxRateLimitAttempts - 1) {
                            val delaySeconds = (1 shl attempt).coerceAtMost(8)
                            delay(delaySeconds * 1000L)
                            return@repeat
                        }
                        throw mapHttpException(error)
                    }
                    is IOException -> throw PatchmonApiException(PatchmonApiException.Kind.CONNECTION_ERROR, error)
                    is PatchmonApiException -> throw error
                    else -> throw error
                }
            }
        }

        if (lastError is HttpException) {
            throw mapHttpException(lastError as HttpException)
        }
        if (lastError is IOException) {
            throw PatchmonApiException(PatchmonApiException.Kind.CONNECTION_ERROR, lastError)
        }

        throw PatchmonApiException(PatchmonApiException.Kind.SERVER_ERROR, lastError)
    }

    private fun mapHttpException(error: HttpException): PatchmonApiException {
        val body = runCatching { error.response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        return mapHttpStatus(error.code(), body)
    }

    private fun mapHttpStatus(status: Int, body: String): PatchmonApiException {
        val detail = extractErrorDetail(body)
        val normalized = detail.lowercase()
        val kind = when (status) {
            400 -> when {
                normalized.contains("invalid host id format") -> PatchmonApiException.Kind.INVALID_HOST_ID
                normalized.contains("foreign key constraints") -> PatchmonApiException.Kind.DELETE_CONSTRAINT
                else -> PatchmonApiException.Kind.BAD_REQUEST
            }
            401 -> PatchmonApiException.Kind.INVALID_CREDENTIALS
            403 -> when {
                normalized.contains("ip address not allowed") -> PatchmonApiException.Kind.IP_NOT_ALLOWED
                normalized.contains("access denied") -> PatchmonApiException.Kind.ACCESS_DENIED
                else -> PatchmonApiException.Kind.FORBIDDEN
            }
            404 -> when {
                normalized.contains("host not found") -> PatchmonApiException.Kind.HOST_NOT_FOUND
                else -> PatchmonApiException.Kind.NOT_FOUND
            }
            429 -> PatchmonApiException.Kind.RATE_LIMITED
            in 500..599 -> PatchmonApiException.Kind.SERVER_ERROR
            else -> PatchmonApiException.Kind.SERVER_ERROR
        }
        return PatchmonApiException(kind)
    }

    private fun extractErrorDetail(body: String): String {
        val trimmed = body.trim()
        if (trimmed.isEmpty()) return ""

        return runCatching {
            val root = org.json.JSONObject(trimmed)
            sequenceOf("message", "error", "detail")
                .mapNotNull { key -> root.optString(key).takeIf { it.isNotBlank() } }
                .firstOrNull()
                ?: trimmed
        }.getOrElse { trimmed }
    }
}
