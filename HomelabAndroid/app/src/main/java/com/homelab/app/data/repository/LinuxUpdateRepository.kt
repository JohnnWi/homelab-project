package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.LinuxUpdateApi
import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateDashboardStats
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateHistoryEntry
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateJobStartResponse
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateJobStatusResponse
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdatePackageUpdate
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateSystem
import com.homelab.app.data.remote.dto.linux_update.LinuxUpdateUpgradePackagesRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import retrofit2.HttpException

data class LinuxUpdateSystemDetail(
    val system: LinuxUpdateSystem,
    val updates: List<LinuxUpdatePackageUpdate>,
    val hiddenUpdates: List<LinuxUpdatePackageUpdate>,
    val history: List<LinuxUpdateHistoryEntry>
)

data class LinuxUpdateActionResult(
    val success: Boolean,
    val message: String
)

@Singleton
class LinuxUpdateRepository @Inject constructor(
    private val api: LinuxUpdateApi,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(url: String, apiKey: String, allowSelfSigned: Boolean = false) {
        withContext(Dispatchers.IO) {
            val cleanUrl = cleanUrl(url)
            val token = cleanToken(apiKey)

            val request = Request.Builder()
                .url("$cleanUrl/api/dashboard/stats")
                .addHeader("Authorization", "Bearer $token")
                .addHeader("Content-Type", "application/json")
                .build()

            tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Linux Update authentication failed")
                }
            }
        }
    }

    suspend fun getDashboardStats(instanceId: String): LinuxUpdateDashboardStats {
        return api.getDashboardStats(instanceId = instanceId).stats
    }

    suspend fun getDashboardSystems(instanceId: String): List<LinuxUpdateSystem> {
        return api.getDashboardSystems(instanceId = instanceId).systems
            .sortedWith(compareBy<LinuxUpdateSystem> { it.sortOrder }.thenBy { it.name.lowercase() })
    }

    suspend fun runCheckAll(instanceId: String): LinuxUpdateActionResult {
        return runAsyncAction(
            instanceId = instanceId,
            actionLabel = "Check all systems",
            startCall = {
                startDashboardAction(
                    instanceId = instanceId,
                    candidatePaths = listOf(
                        "/api/systems/check-all",
                        "/api/updates/check-all",
                        "/api/check-all"
                    ),
                    fallbackStatus = "checking_all"
                )
            }
        )
    }

    suspend fun runRefreshCache(instanceId: String): LinuxUpdateActionResult {
        return runAsyncAction(
            instanceId = instanceId,
            actionLabel = "Refresh cache",
            startCall = {
                startDashboardAction(
                    instanceId = instanceId,
                    candidatePaths = listOf(
                        "/api/cache/refresh",
                        "/api/updates/cache/refresh",
                        "/api/refresh-cache"
                    ),
                    fallbackStatus = "refreshing"
                )
            }
        )
    }

    suspend fun getSystemDetail(instanceId: String, systemId: Int): LinuxUpdateSystemDetail {
        val response = api.getSystemDetail(systemId = systemId, instanceId = instanceId)
        return LinuxUpdateSystemDetail(
            system = response.system,
            updates = response.updates.sortedWith(
                compareByDescending<LinuxUpdatePackageUpdate> { it.isSecurityFlag }
                    .thenByDescending { it.isKeptBackFlag }
                    .thenBy { it.packageName.lowercase() }
            ),
            hiddenUpdates = response.hiddenUpdates.sortedWith(
                compareByDescending<LinuxUpdatePackageUpdate> { it.isSecurityFlag }
                    .thenBy { it.packageName.lowercase() }
            ),
            history = response.history.sortedByDescending { it.startedAt.orEmpty() }
        )
    }

    suspend fun runCheck(instanceId: String, systemId: Int): LinuxUpdateActionResult {
        return runAsyncAction(
            instanceId = instanceId,
            actionLabel = "Check",
            startCall = { api.checkSystem(systemId = systemId, instanceId = instanceId) }
        )
    }

    suspend fun runUpgradeAll(instanceId: String, systemId: Int): LinuxUpdateActionResult {
        return runAsyncAction(
            instanceId = instanceId,
            actionLabel = "Upgrade",
            startCall = { api.upgradeSystem(systemId = systemId, instanceId = instanceId) }
        )
    }

    suspend fun runFullUpgrade(instanceId: String, systemId: Int): LinuxUpdateActionResult {
        return runAsyncAction(
            instanceId = instanceId,
            actionLabel = "Full upgrade",
            startCall = { api.fullUpgradeSystem(systemId = systemId, instanceId = instanceId) }
        )
    }

    suspend fun runUpgradePackage(
        instanceId: String,
        systemId: Int,
        packageName: String
    ): LinuxUpdateActionResult {
        val normalizedName = packageName.trim()
        if (normalizedName.isEmpty()) {
            throw IllegalArgumentException("Package name is required")
        }

        return runUpgradePackages(
            instanceId = instanceId,
            systemId = systemId,
            packageNames = listOf(normalizedName)
        )
    }

    suspend fun runUpgradePackages(
        instanceId: String,
        systemId: Int,
        packageNames: List<String>
    ): LinuxUpdateActionResult {
        val normalized = packageNames
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()

        if (normalized.isEmpty()) {
            throw IllegalArgumentException("At least one package is required")
        }

        return runAsyncAction(
            instanceId = instanceId,
            actionLabel = if (normalized.size == 1) "Package upgrade" else "Package upgrades",
            startCall = {
                try {
                    api.upgradePackages(
                        systemId = systemId,
                        request = LinuxUpdateUpgradePackagesRequest(
                            packageNames = normalized,
                            packages = normalized
                        ),
                        instanceId = instanceId
                    )
                } catch (error: HttpException) {
                    if (normalized.size == 1 && shouldRetryWithSinglePackageAlias(error)) {
                        api.upgradePackage(
                            systemId = systemId,
                            packageName = normalized.first(),
                            instanceId = instanceId
                        )
                    } else {
                        throw error
                    }
                }
            }
        )
    }

    suspend fun rebootSystem(instanceId: String, systemId: Int): LinuxUpdateActionResult {
        val response = api.rebootSystem(systemId = systemId, instanceId = instanceId)
        val message = response.message?.trim().orEmpty()
        val fallback = if (response.success) "Reboot command sent" else "Reboot failed"
        return LinuxUpdateActionResult(
            success = response.success,
            message = message.ifEmpty { fallback }
        )
    }

    private suspend fun runAsyncAction(
        instanceId: String,
        actionLabel: String,
        startCall: suspend () -> LinuxUpdateJobStartResponse
    ): LinuxUpdateActionResult {
        val started = startCall()
        val startError = started.error?.trim().takeUnless { it.isNullOrEmpty() }
        if (startError != null) {
            throw IllegalStateException(startError)
        }

        val jobId = started.resolvedJobId()
        if (jobId == null) {
            if (started.isAcceptedWithoutJobId()) {
                return LinuxUpdateActionResult(
                    success = true,
                    message = started.startMessage(actionLabel)
                )
            }

            val message = started.message?.trim().takeUnless { it.isNullOrEmpty() }
                ?: "$actionLabel failed to start"
            throw IllegalStateException(message)
        }

        val terminalStatus = pollJob(instanceId = instanceId, jobId = jobId)
        return terminalStatus.asActionResult(actionLabel)
    }

    private suspend fun pollJob(
        instanceId: String,
        jobId: String,
        timeoutMs: Long = 180_000L,
        pollDelayMs: Long = 1_200L
    ): LinuxUpdateJobStatusResponse {
        val startedAt = System.currentTimeMillis()

        while (System.currentTimeMillis() - startedAt <= timeoutMs) {
            val response = api.getJobStatus(jobId = jobId, instanceId = instanceId)
            when (response.status.trim().lowercase()) {
                "running" -> delay(pollDelayMs)
                "done", "failed" -> return response
                else -> {
                    if (!response.error.isNullOrBlank()) {
                        throw IllegalStateException(response.error.trim())
                    }
                    delay(pollDelayMs)
                }
            }
        }

        throw IllegalStateException("Operation timed out")
    }

    private fun LinuxUpdateJobStatusResponse.asActionResult(actionLabel: String): LinuxUpdateActionResult {
        val status = status.trim().lowercase()
        val resultObject = result.asJsonObjectOrNull()
        val nestedStatus = resultObject.stringValue("status")?.lowercase()
        val errorText = error?.trim().takeUnless { it.isNullOrEmpty() }
            ?: resultObject.stringValue("error")
        val outputText = resultObject.stringValue("output")
            ?: resultObject.stringValue("message")
        val packageName = resultObject.stringValue("package")

        val successful = when {
            status == "failed" -> false
            nestedStatus == "failed" -> false
            else -> true
        }

        val fallback = when {
            successful && packageName != null -> "$actionLabel completed for $packageName"
            successful -> "$actionLabel completed"
            else -> "$actionLabel failed"
        }

        return LinuxUpdateActionResult(
            success = successful,
            message = normalizeMessage(errorText ?: outputText) ?: fallback
        )
    }

    private suspend fun startDashboardAction(
        instanceId: String,
        candidatePaths: List<String>,
        fallbackStatus: String
    ): LinuxUpdateJobStartResponse {
        var lastError: Exception = IllegalStateException("Action failed to start")

        return withContext(Dispatchers.IO) {
            for (path in candidatePaths) {
                val request = Request.Builder()
                    .url("https://placeholder.local$path")
                    .post("".toRequestBody("application/json".toMediaType()))
                    .addHeader("Accept", "application/json")
                    .addHeader("Content-Type", "application/json")
                    .addHeader("X-Homelab-Service", "Linux Update")
                    .addHeader("X-Homelab-Instance-Id", instanceId)
                    .build()

                try {
                    tlsClientSelector.forInstance(instanceId).newCall(request).execute().use { response ->
                        val rawBody = response.body?.string().orEmpty()
                        val trimmedBody = rawBody.trim()

                        if (!response.isSuccessful) {
                            if (response.code in listOf(404, 405)) {
                                lastError = IllegalStateException("Endpoint $path not supported")
                                return@use
                            }

                            val parsedError = trimmedBody
                                .takeIf { it.startsWith("{") }
                                ?.let { runCatching { Json.parseToJsonElement(it) }.getOrNull() as? JsonObject }
                                ?.stringValue("error")
                            val message = parsedError
                                ?: normalizeMessage(trimmedBody)
                                ?: "Server error ${response.code}: ${response.message}"
                            throw IllegalStateException(message)
                        }

                        if (trimmedBody.isEmpty()) {
                            return@withContext LinuxUpdateJobStartResponse(status = fallbackStatus)
                        }

                        val json = runCatching { Json.parseToJsonElement(trimmedBody) }.getOrNull()
                        val objectBody = json as? JsonObject
                        if (objectBody != null) {
                            return@withContext LinuxUpdateJobStartResponse(
                                status = objectBody.stringValue("status") ?: fallbackStatus,
                                job = objectBody.stringValue("job"),
                                jobAlias = objectBody.stringValue("job_id"),
                                id = objectBody.stringValue("id"),
                                jobId = objectBody.stringValue("jobId"),
                                error = objectBody.stringValue("error"),
                                message = objectBody.stringValue("message")
                            )
                        }

                        return@withContext LinuxUpdateJobStartResponse(
                            status = fallbackStatus,
                            message = normalizeMessage(trimmedBody)
                        )
                    }
                } catch (error: Exception) {
                    lastError = error
                }
            }

            throw lastError
        }
    }

    private fun JsonElement?.asJsonObjectOrNull(): JsonObject {
        return this as? JsonObject ?: JsonObject(emptyMap())
    }

    private fun JsonObject.stringValue(key: String): String? {
        return this[key]?.jsonPrimitive?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun normalizeMessage(value: String?): String? {
        val message = value?.lineSequence()
            ?.map { it.trim() }
            ?.firstOrNull { it.isNotEmpty() }
            ?: return null
        return message.take(220)
    }

    private fun shouldRetryWithSinglePackageAlias(error: HttpException): Boolean {
        return when (error.code()) {
            400, 404, 405 -> true
            else -> false
        }
    }

    private fun LinuxUpdateJobStartResponse.resolvedJobId(): String? {
        return listOf(jobId, job, jobAlias, id)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }

    private fun LinuxUpdateJobStartResponse.isAcceptedWithoutJobId(): Boolean {
        val normalizedStatus = status.trim().lowercase()
        if (normalizedStatus in ACCEPTED_START_STATUSES) {
            return true
        }
        if (normalizedStatus.isNotEmpty() && normalizedStatus != "failed" && normalizedStatus != "error") {
            return true
        }
        return !message.isNullOrBlank() && error.isNullOrBlank()
    }

    private fun LinuxUpdateJobStartResponse.startMessage(actionLabel: String): String {
        val normalizedStatus = status.trim().lowercase()
        val fallback = when (normalizedStatus) {
            "checking_all" -> "Check all systems started"
            "refreshing" -> "Refresh cache started"
            else -> "$actionLabel started"
        }
        return normalizeMessage(message) ?: fallback
    }

    private companion object {
        val ACCEPTED_START_STATUSES = setOf(
            "accepted",
            "checking_all",
            "done",
            "ok",
            "queued",
            "refreshing",
            "running",
            "started",
            "success"
        )
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "")
    }

    private fun cleanToken(raw: String): String {
        val token = raw.trim()
        if (token.startsWith("bearer ", ignoreCase = true)) {
            return token.substring(7).trim()
        }
        return token
    }
}
