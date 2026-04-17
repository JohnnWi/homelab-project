package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.CraftyApi
import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.dto.crafty.CraftyLoginData
import com.homelab.app.data.remote.dto.crafty.CraftyLoginRequest
import com.homelab.app.data.remote.dto.crafty.CraftyResponse
import com.homelab.app.data.remote.dto.crafty.CraftyServer
import com.homelab.app.data.remote.dto.crafty.CraftyServerStats
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

data class CraftyServerWithStats(
    val server: CraftyServer,
    val stats: CraftyServerStats?
)

data class CraftyDashboardData(
    val servers: List<CraftyServerWithStats>
) {
    val totalServers: Int get() = servers.size
    val runningServers: Int get() = servers.count { it.stats?.running == true }
    val totalPlayers: Int get() = servers.sumOf { it.stats?.online ?: 0 }
}

class CraftyApiException(
    val kind: Kind,
    override val cause: Throwable? = null
) : Exception(kind.name, cause) {
    enum class Kind {
        INVALID_CREDENTIALS,
        SERVER_ERROR,
        CONNECTION_ERROR
    }
}

@Singleton
class CraftyRepository @Inject constructor(
    private val api: CraftyApi,
    private val tlsClientSelector: TlsClientSelector,
    private val json: Json
) {
    suspend fun authenticate(
        url: String,
        username: String,
        password: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ): String {
        return withContext(Dispatchers.IO) {
            val baseCandidates = listOf(url.trim().trimEnd('/'), fallbackUrl?.trim()?.trimEnd('/'))
                .filter { !it.isNullOrBlank() }
                .distinct()
                .filterNotNull()

            var lastError: CraftyApiException? = null
            for (baseUrl in baseCandidates) {
                try {
                    return@withContext authenticateAgainst(baseUrl, username, password, allowSelfSigned)
                } catch (error: CraftyApiException) {
                    lastError = error
                    if (error.kind == CraftyApiException.Kind.INVALID_CREDENTIALS) {
                        throw error
                    }
                }
            }

            throw lastError ?: CraftyApiException(CraftyApiException.Kind.CONNECTION_ERROR)
        }
    }

    suspend fun getServers(instanceId: String): List<CraftyServer> {
        return try {
            api.getServers(instanceId).data
        } catch (error: Exception) {
            throw handleException(error)
        }
    }

    suspend fun getServerStats(instanceId: String, serverId: Int): CraftyServerStats {
        return try {
            api.getServerStats(instanceId, serverId).data
        } catch (error: Exception) {
            throw handleException(error)
        }
    }

    suspend fun getServerLogs(instanceId: String, serverId: Int): List<String> {
        return try {
            api.getServerLogs(instanceId = instanceId, serverId = serverId).data
        } catch (error: Exception) {
            throw handleException(error)
        }
    }

    suspend fun getDashboard(instanceId: String): CraftyDashboardData = coroutineScope {
        val servers = getServers(instanceId)
        val enriched = mutableListOf<CraftyServerWithStats>()
        servers.chunked(4).forEach { chunk ->
            enriched += chunk.map { server ->
                async {
                    val stats = runCatching { getServerStats(instanceId, server.serverId) }.getOrNull()
                    CraftyServerWithStats(server = server, stats = stats)
                }
            }.awaitAll()
        }
        CraftyDashboardData(enriched)
    }

    suspend fun sendAction(instanceId: String, serverId: Int, action: CraftyServerAction) {
        try {
            api.sendAction(instanceId = instanceId, serverId = serverId, action = action.apiValue)
        } catch (error: Exception) {
            throw handleException(error)
        }
    }

    suspend fun sendCommand(instanceId: String, serverId: Int, command: String) {
        val normalizedCommand = command.trim().removePrefix("/")
        require(normalizedCommand.isNotBlank()) { "Command cannot be blank" }

        try {
            api.sendCommand(
                instanceId = instanceId,
                serverId = serverId,
                command = normalizedCommand.toRequestBody("text/plain".toMediaType())
            )
        } catch (error: Exception) {
            throw handleException(error)
        }
    }

    private fun handleException(error: Throwable): CraftyApiException {
        return when (error) {
            is HttpException -> {
                if (error.code() == 401 || error.code() == 403) {
                    CraftyApiException(CraftyApiException.Kind.INVALID_CREDENTIALS, error)
                } else {
                    CraftyApiException(CraftyApiException.Kind.SERVER_ERROR, error)
                }
            }
            is IOException -> CraftyApiException(CraftyApiException.Kind.CONNECTION_ERROR, error)
            is CraftyApiException -> error
            else -> CraftyApiException(CraftyApiException.Kind.SERVER_ERROR, error)
        }
    }

    private fun authenticateAgainst(baseUrl: String, username: String, password: String, allowSelfSigned: Boolean): String {
        val requestBody = json.encodeToString(
            CraftyLoginRequest.serializer(),
            CraftyLoginRequest(username = username.trim(), password = password)
        ).toRequestBody("application/json".toMediaType())

        val request = Request.Builder()
            .url("$baseUrl/api/v2/auth/login")
            .post(requestBody)
            .addHeader("Content-Type", "application/json")
            .build()

        try {
            tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    if (response.code == 401 || response.code == 403) {
                        throw CraftyApiException(CraftyApiException.Kind.INVALID_CREDENTIALS)
                    }
                    throw CraftyApiException(CraftyApiException.Kind.SERVER_ERROR)
                }

                val payload = response.body?.string().orEmpty()
                val decoded = json.decodeFromString(
                    CraftyResponse.serializer(CraftyLoginData.serializer()),
                    payload
                )
                return decoded.data.token?.takeIf { it.isNotBlank() }
                    ?: throw CraftyApiException(CraftyApiException.Kind.SERVER_ERROR)
            }
        } catch (error: IOException) {
            throw CraftyApiException(CraftyApiException.Kind.CONNECTION_ERROR, error)
        }
    }
}

enum class CraftyServerAction(val apiValue: String) {
    START("start_server"),
    STOP("stop_server"),
    RESTART("restart_server"),
    UPDATE("update_executable"),
    BACKUP("backup_server"),
    KILL("kill_server")
}
