package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.HealthchecksApi
import com.homelab.app.data.remote.dto.healthchecks.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthchecksRepository @Inject constructor(
    private val api: HealthchecksApi,
    private val okHttpClient: OkHttpClient
) {
    suspend fun validateApiKey(url: String, apiKey: String) {
        withContext(Dispatchers.IO) {
            val clean = url.trimEnd('/')
            val request = Request.Builder()
                .url("$clean/api/v3/checks/")
                .addHeader("X-Api-Key", apiKey)
                .build()
            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Healthchecks authentication failed")
                }
            }
        }
    }

    suspend fun listChecks(instanceId: String): List<HealthchecksCheck> =
        api.listChecks(instanceId = instanceId).checks

    suspend fun getCheck(instanceId: String, id: String): HealthchecksCheck =
        api.getCheck(instanceId = instanceId, id = id)

    suspend fun createCheck(instanceId: String, payload: HealthchecksCheckPayload) {
        api.createCheck(instanceId = instanceId, payload = payload)
    }

    suspend fun updateCheck(instanceId: String, id: String, payload: HealthchecksCheckPayload) {
        api.updateCheck(instanceId = instanceId, id = id, payload = payload)
    }

    suspend fun pauseCheck(instanceId: String, id: String) {
        api.pauseCheck(instanceId = instanceId, id = id)
    }

    suspend fun resumeCheck(instanceId: String, id: String) {
        api.resumeCheck(instanceId = instanceId, id = id)
    }

    suspend fun deleteCheck(instanceId: String, id: String) {
        api.deleteCheck(instanceId = instanceId, id = id)
    }

    suspend fun listPings(instanceId: String, id: String): List<HealthchecksPing> =
        api.listPings(instanceId = instanceId, id = id).pings

    suspend fun getPingBody(instanceId: String, id: String, n: Int): String =
        api.getPingBody(instanceId = instanceId, id = id, n = n)

    suspend fun listFlips(instanceId: String, id: String): List<HealthchecksFlip> =
        api.listFlips(instanceId = instanceId, id = id).flips

    suspend fun listChannels(instanceId: String): List<HealthchecksChannel> =
        api.listChannels(instanceId = instanceId).channels

    suspend fun listBadges(instanceId: String): HealthchecksBadgesResponse =
        api.listBadges(instanceId = instanceId)
}
