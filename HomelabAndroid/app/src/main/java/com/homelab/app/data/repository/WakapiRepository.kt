package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.WakapiApi
import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.dto.wakapi.WakapiDailySummariesResponse
import com.homelab.app.data.remote.dto.wakapi.WakapiSummaryResponse
import java.io.IOException
import java.util.Base64
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import retrofit2.HttpException

class WakapiApiException(
    val kind: Kind,
    override val cause: Throwable? = null
) : Exception(kind.name, cause) {
    enum class Kind {
        INVALID_CREDENTIALS,
        SERVER_ERROR,
        CONNECTION_ERROR
    }
}

data class WakapiSummaryFilter(
    val dimension: Dimension,
    val value: String
) {
    enum class Dimension {
        PROJECT,
        LANGUAGE,
        EDITOR,
        OPERATING_SYSTEM,
        MACHINE,
        LABEL
    }
}

@Singleton
class WakapiRepository @Inject constructor(
    private val api: WakapiApi,
    private val tlsClientSelector: TlsClientSelector
) {
    private data class TimedCache<T>(
        val value: T,
        val timestampMs: Long
    )

    private val summaryCache = ConcurrentHashMap<String, TimedCache<WakapiSummaryResponse>>()
    private val activityCache = ConcurrentHashMap<String, TimedCache<WakapiDailySummariesResponse>>()

    suspend fun authenticate(
        url: String,
        apiKey: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) {
        withContext(Dispatchers.IO) {
            val baseCandidates = listOf(url.trim().trimEnd('/'), fallbackUrl?.trim()?.trimEnd('/'))
                .filter { !it.isNullOrBlank() }
                .distinct()
                .filterNotNull()

            var lastError: WakapiApiException? = null
            for (baseUrl in baseCandidates) {
                try {
                    authenticateAgainst(baseUrl = baseUrl, apiKey = apiKey, allowSelfSigned = allowSelfSigned)
                    return@withContext
                } catch (error: WakapiApiException) {
                    lastError = error
                    if (error.kind == WakapiApiException.Kind.INVALID_CREDENTIALS) {
                        throw error
                    }
                }
            }

            throw lastError ?: WakapiApiException(WakapiApiException.Kind.CONNECTION_ERROR)
        }
    }

    suspend fun getSummary(
        instanceId: String,
        interval: String = "today",
        filter: WakapiSummaryFilter? = null,
        forceRefresh: Boolean = false
    ): WakapiSummaryResponse {
        val cacheKey = summaryCacheKey(instanceId, interval, filter)
        if (!forceRefresh) {
            summaryCache[cacheKey]?.takeIf(::isFresh)?.let { return it.value }
        }

        return try {
            val response = withSummaryPathFallback(
                primary = {
                    api.getSummary(
                        instanceId = instanceId,
                        interval = interval,
                        project = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.PROJECT },
                        language = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.LANGUAGE },
                        editor = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.EDITOR },
                        operatingSystem = filter?.value.takeIf {
                            filter?.dimension == WakapiSummaryFilter.Dimension.OPERATING_SYSTEM
                        },
                        machine = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.MACHINE },
                        label = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.LABEL }
                    )
                },
                fallback = {
                    api.getSummaryRoot(
                        instanceId = instanceId,
                        interval = interval,
                        project = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.PROJECT },
                        language = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.LANGUAGE },
                        editor = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.EDITOR },
                        operatingSystem = filter?.value.takeIf {
                            filter?.dimension == WakapiSummaryFilter.Dimension.OPERATING_SYSTEM
                        },
                        machine = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.MACHINE },
                        label = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.LABEL }
                    )
                }
            )
            summaryCache[cacheKey] = TimedCache(response, System.currentTimeMillis())
            response
        } catch (e: Exception) {
            throw handleException(e)
        }
    }

    suspend fun getDailySummaries(
        instanceId: String,
        range: String = "last_6_months",
        filter: WakapiSummaryFilter? = null,
        forceRefresh: Boolean = false
    ): WakapiDailySummariesResponse {
        val cacheKey = activityCacheKey(instanceId, range, filter)
        if (!forceRefresh) {
            activityCache[cacheKey]?.takeIf(::isFresh)?.let { return it.value }
        }

        return try {
            val response = withSummaryPathFallback(
                primary = {
                    api.getDailySummaries(
                        instanceId = instanceId,
                        range = range,
                        project = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.PROJECT },
                        language = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.LANGUAGE },
                        editor = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.EDITOR },
                        operatingSystem = filter?.value.takeIf {
                            filter?.dimension == WakapiSummaryFilter.Dimension.OPERATING_SYSTEM
                        },
                        machine = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.MACHINE },
                        label = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.LABEL }
                    )
                },
                fallback = {
                    api.getDailySummariesRoot(
                        instanceId = instanceId,
                        range = range,
                        project = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.PROJECT },
                        language = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.LANGUAGE },
                        editor = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.EDITOR },
                        operatingSystem = filter?.value.takeIf {
                            filter?.dimension == WakapiSummaryFilter.Dimension.OPERATING_SYSTEM
                        },
                        machine = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.MACHINE },
                        label = filter?.value.takeIf { filter?.dimension == WakapiSummaryFilter.Dimension.LABEL }
                    )
                }
            )
            activityCache[cacheKey] = TimedCache(response, System.currentTimeMillis())
            response
        } catch (e: Exception) {
            throw handleException(e)
        }
    }

    private fun handleException(error: Throwable): WakapiApiException {
        return when (error) {
            is HttpException -> {
                if (error.code() == 401 || error.code() == 403) {
                    WakapiApiException(WakapiApiException.Kind.INVALID_CREDENTIALS, error)
                } else {
                    WakapiApiException(WakapiApiException.Kind.SERVER_ERROR, error)
                }
            }
            is IOException -> WakapiApiException(WakapiApiException.Kind.CONNECTION_ERROR, error)
            is WakapiApiException -> error
            else -> WakapiApiException(WakapiApiException.Kind.SERVER_ERROR, error)
        }
    }

    private fun authenticateAgainst(baseUrl: String, apiKey: String, allowSelfSigned: Boolean) {
        val credentials = Base64.getEncoder()
            .encodeToString(apiKey.trim().toByteArray(Charsets.UTF_8))

        try {
            val paths = listOf("/api/summary?interval=today", "/summary?interval=today")
            var lastStatusCode: Int? = null

            for ((index, path) in paths.withIndex()) {
                val request = Request.Builder()
                    .url("$baseUrl$path")
                    .addHeader("Authorization", "Basic $credentials")
                    .addHeader("Content-Type", "application/json")
                    .build()

                tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        return
                    }

                    lastStatusCode = response.code
                    if (response.code == 401 || response.code == 403) {
                        throw WakapiApiException(WakapiApiException.Kind.INVALID_CREDENTIALS)
                    }

                    if (response.code != 404 || index == paths.lastIndex) {
                        throw WakapiApiException(WakapiApiException.Kind.SERVER_ERROR)
                    }
                }
            }

            if (lastStatusCode == 404) {
                throw WakapiApiException(WakapiApiException.Kind.SERVER_ERROR)
            }
        } catch (error: IOException) {
            throw WakapiApiException(WakapiApiException.Kind.CONNECTION_ERROR, error)
        }
    }

    private suspend fun <T> withSummaryPathFallback(
        primary: suspend () -> T,
        fallback: suspend () -> T
    ): T {
        return try {
            primary()
        } catch (error: HttpException) {
            if (error.code() == 404) fallback() else throw error
        }
    }

    private fun summaryCacheKey(
        instanceId: String,
        interval: String,
        filter: WakapiSummaryFilter?
    ): String = "$instanceId|$interval|${filter?.dimension}|${filter?.value}"

    private fun activityCacheKey(
        instanceId: String,
        range: String,
        filter: WakapiSummaryFilter?
    ): String = "$instanceId|$range|${filter?.dimension}|${filter?.value}"

    private fun <T> isFresh(entry: TimedCache<T>): Boolean =
        System.currentTimeMillis() - entry.timestampMs <= CACHE_TTL_MS

    private companion object {
        const val CACHE_TTL_MS = 120_000L
    }
}
