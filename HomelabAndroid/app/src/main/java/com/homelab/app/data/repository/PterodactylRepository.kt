package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.PterodactylApi
import com.homelab.app.data.remote.dto.pterodactyl.PterodactylPowerRequest
import com.homelab.app.data.remote.dto.pterodactyl.PterodactylResources
import com.homelab.app.data.remote.dto.pterodactyl.PterodactylServer
import kotlinx.serialization.json.Json
import okhttp3.Request
import retrofit2.HttpException
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

data class PterodactylDashboardData(
    val servers: List<PterodactylServer>
) {
    val totalServers: Int get() = servers.size
    val runningServers: Int get() = servers.count { it.status == null && !it.isSuspended && !it.isInstalling }
}

class PterodactylApiException(
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
class PterodactylRepository @Inject constructor(
    private val api: PterodactylApi,
    private val tlsClientSelector: TlsClientSelector,
    private val json: Json
) {

    suspend fun authenticate(
        url: String,
        apiKey: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) {
        return withContext(Dispatchers.IO) {
            val candidates = listOf(url.trim().trimEnd('/'), fallbackUrl?.trim()?.trimEnd('/'))
                .filterNotNull()
                .filter { it.isNotBlank() }
                .distinct()

            var lastError: PterodactylApiException? = null
            for (baseUrl in candidates) {
                try {
                    authenticateAgainst(baseUrl, apiKey, allowSelfSigned)
                    return@withContext
                } catch (error: PterodactylApiException) {
                    lastError = error
                    if (error.kind == PterodactylApiException.Kind.INVALID_CREDENTIALS) throw error
                }
            }
            throw lastError ?: PterodactylApiException(PterodactylApiException.Kind.CONNECTION_ERROR)
        }
    }

    suspend fun getServers(instanceId: String): List<PterodactylServer> {
        return try {
            api.getServers(instanceId).data.map { it.attributes }
        } catch (e: Exception) {
            throw handleException(e)
        }
    }

    suspend fun getServerResources(instanceId: String, identifier: String): PterodactylResources {
        return try {
            api.getServerResources(instanceId, identifier).attributes
        } catch (e: Exception) {
            throw handleException(e)
        }
    }

    suspend fun sendPowerSignal(instanceId: String, identifier: String, signal: String) {
        try {
            api.sendPowerSignal(instanceId, identifier, PterodactylPowerRequest(signal))
        } catch (e: Exception) {
            throw handleException(e)
        }
    }

    private fun authenticateAgainst(baseUrl: String, apiKey: String, allowSelfSigned: Boolean) {
        val request = Request.Builder()
            .url("$baseUrl/api/client")
            .get()
            .addHeader("Authorization", "Bearer $apiKey")
            .addHeader("Accept", "application/json")
            .build()

        try {
            tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
                when {
                    response.code == 401 || response.code == 403 ->
                        throw PterodactylApiException(PterodactylApiException.Kind.INVALID_CREDENTIALS)
                    !response.isSuccessful ->
                        throw PterodactylApiException(PterodactylApiException.Kind.SERVER_ERROR)
                }
            }
        } catch (e: IOException) {
            throw PterodactylApiException(PterodactylApiException.Kind.CONNECTION_ERROR, e)
        } catch (e: PterodactylApiException) {
            throw e
        } catch (e: Exception) {
            throw PterodactylApiException(PterodactylApiException.Kind.CONNECTION_ERROR, e)
        }
    }

    private fun handleException(e: Throwable): PterodactylApiException {
        return when (e) {
            is HttpException -> when (e.code()) {
                401, 403 -> PterodactylApiException(PterodactylApiException.Kind.INVALID_CREDENTIALS, e)
                else -> PterodactylApiException(PterodactylApiException.Kind.SERVER_ERROR, e)
            }
            is SerializationException -> PterodactylApiException(PterodactylApiException.Kind.SERVER_ERROR, e)
            is IOException -> PterodactylApiException(PterodactylApiException.Kind.CONNECTION_ERROR, e)
            is PterodactylApiException -> e
            else -> PterodactylApiException(PterodactylApiException.Kind.SERVER_ERROR, e)
        }
    }
}
