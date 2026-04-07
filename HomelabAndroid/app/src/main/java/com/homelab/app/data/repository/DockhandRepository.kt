package com.homelab.app.data.repository

import android.net.Uri
import com.homelab.app.data.remote.api.DockhandApi
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

enum class DockhandContainerFilter {
    ALL,
    RUNNING,
    STOPPED,
    ISSUES
}

enum class DockhandContainerAction {
    START,
    STOP,
    RESTART
}

enum class DockhandStackAction {
    START,
    STOP,
    RESTART
}

data class DockhandEnvironment(
    val id: String,
    val name: String,
    val isDefault: Boolean
)

data class DockhandContainer(
    val id: String,
    val name: String,
    val image: String,
    val state: String,
    val status: String,
    val portsSummary: String,
    val health: String?,
    val environmentId: String?
) {
    val isRunning: Boolean
        get() = state.equals("running", ignoreCase = true) || status.contains("up", ignoreCase = true)

    val isIssue: Boolean
        get() {
            val lowered = "$state $status ${health.orEmpty()}".lowercase()
            return lowered.contains("dead") ||
                lowered.contains("error") ||
                lowered.contains("exited") ||
                lowered.contains("unhealthy")
        }
}

data class DockhandStack(
    val id: String,
    val name: String,
    val status: String,
    val services: Int,
    val source: String?,
    val environmentId: String?
)

data class DockhandResourceItem(
    val id: String,
    val name: String,
    val details: String?
)

data class DockhandActivityItem(
    val id: String,
    val action: String,
    val target: String,
    val status: String,
    val createdAt: String?
)

data class DockhandScheduleItem(
    val id: String,
    val name: String,
    val enabled: Boolean,
    val schedule: String?,
    val environmentId: String?,
    val nextRun: String?,
    val lastRun: String?
)

data class DockhandStats(
    val totalContainers: Int,
    val runningContainers: Int,
    val stoppedContainers: Int,
    val issueContainers: Int,
    val stacks: Int,
    val images: Int,
    val volumes: Int,
    val networks: Int
)

data class DockhandDashboardData(
    val stats: DockhandStats,
    val environments: List<DockhandEnvironment>,
    val containers: List<DockhandContainer>,
    val stacks: List<DockhandStack>,
    val images: List<DockhandResourceItem>,
    val volumes: List<DockhandResourceItem>,
    val networks: List<DockhandResourceItem>,
    val activity: List<DockhandActivityItem>,
    val schedules: List<DockhandScheduleItem>
)

data class DockhandContainerDetail(
    val container: DockhandContainer,
    val rawDetails: List<Pair<String, String>>,
    val logs: String
)

data class DockhandStackDetail(
    val stack: DockhandStack,
    val rawDetails: List<Pair<String, String>>,
    val compose: String
)

data class DockhandScheduleDetail(
    val schedule: DockhandScheduleItem,
    val rawDetails: List<Pair<String, String>>
)

data class DockhandActionResult(
    val success: Boolean,
    val message: String
)

@Singleton
class DockhandRepository @Inject constructor(
    private val api: DockhandApi,
    private val okHttpClient: OkHttpClient
) {

    suspend fun authenticate(
        url: String,
        username: String,
        password: String,
        mfaCode: String,
        fallbackUrl: String? = null
    ): String {
        val baseCandidates = listOf(cleanUrl(url), cleanOptionalUrl(fallbackUrl))
            .filterNotNull()
            .distinct()

        var lastError: Exception? = null
        for (base in baseCandidates) {
            try {
                return authenticateAgainst(
                    baseUrl = base,
                    username = username.trim(),
                    password = password,
                    mfaCode = mfaCode.trim()
                )
            } catch (error: Exception) {
                lastError = error
            }
        }

        throw lastError ?: IllegalStateException("Dockhand authentication failed")
    }

    suspend fun getDashboard(instanceId: String, env: String?): DockhandDashboardData = coroutineScope {
        val normalizedEnv = normalizeEnvironmentId(env)
        val environments = runCatching {
            parseEnvironments(api.getEnvironments(instanceId = instanceId))
        }.getOrDefault(emptyList())
        val scopes = resolveScopes(env = normalizedEnv, environments = environments)
        val fallbackScopes = if (normalizedEnv.isNullOrBlank()) {
            environments.map { it.id }
        } else {
            emptyList()
        }

        val containersDeferred = async { loadContainers(instanceId, scopes, fallbackScopes) }
        val stacksDeferred = async { loadStacks(instanceId, scopes, fallbackScopes) }
        val imagesDeferred = async { loadResources(instanceId, scopes, fallbackScopes, kind = "image") { envId -> api.getImages(instanceId = instanceId, env = envId) } }
        val volumesDeferred = async { loadResources(instanceId, scopes, fallbackScopes, kind = "volume") { envId -> api.getVolumes(instanceId = instanceId, env = envId) } }
        val networksDeferred = async { loadResources(instanceId, scopes, fallbackScopes, kind = "network") { envId -> api.getNetworks(instanceId = instanceId, env = envId) } }
        val activityDeferred = async { loadActivity(instanceId, scopes, fallbackScopes) }
        val schedulesDeferred = async { loadSchedules(instanceId, scopes, fallbackScopes) }

        val containers = containersDeferred.await()
        val stacks = stacksDeferred.await()
        val images = imagesDeferred.await()
        val volumes = volumesDeferred.await()
        val networks = networksDeferred.await()
        val activity = activityDeferred.await()
        val schedules = schedulesDeferred.await()

        val stats = if (!normalizedEnv.isNullOrBlank()) {
            runCatching {
                parseStats(
                    api.getDashboardStats(instanceId = instanceId, env = normalizedEnv),
                    containers = containers,
                    stacks = stacks,
                    images = images,
                    volumes = volumes,
                    networks = networks
                )
            }.getOrElse {
                synthesizeStats(
                    containers = containers,
                    stacks = stacks,
                    images = images,
                    volumes = volumes,
                    networks = networks
                )
            }
        } else {
            synthesizeStats(
                containers = containers,
                stacks = stacks,
                images = images,
                volumes = volumes,
                networks = networks
            )
        }

        DockhandDashboardData(
            stats = stats,
            environments = environments,
            containers = containers,
            stacks = stacks,
            images = images,
            volumes = volumes,
            networks = networks,
            activity = activity,
            schedules = schedules
        )
    }

    private fun resolveScopes(env: String?, environments: List<DockhandEnvironment>): List<String?> {
        if (!env.isNullOrBlank()) return listOf(env)
        return listOf(null)
    }

    private suspend fun loadContainers(
        instanceId: String,
        scopes: List<String?>,
        fallbackScopes: List<String?> = emptyList()
    ): List<DockhandContainer> {
        val merged = linkedMapOf<String, DockhandContainer>()
        for (scope in scopes) {
            val items = runCatching {
                parseContainers(api.getContainers(instanceId = instanceId, env = scope), scope)
            }.getOrDefault(emptyList())
            items.forEach { merged["${it.environmentId.orEmpty()}|${it.id}"] = it }
        }
        if (merged.isEmpty() && fallbackScopes.isNotEmpty()) {
            for (scope in fallbackScopes) {
                val fallback = runCatching {
                    parseContainers(api.getContainers(instanceId = instanceId, env = scope), scope)
                }.getOrDefault(emptyList())
                fallback.forEach { merged["${it.environmentId.orEmpty()}|${it.id}"] = it }
            }
        }
        return merged.values.sortedBy { it.name.lowercase() }
    }

    private suspend fun loadStacks(
        instanceId: String,
        scopes: List<String?>,
        fallbackScopes: List<String?> = emptyList()
    ): List<DockhandStack> {
        val merged = linkedMapOf<String, DockhandStack>()
        for (scope in scopes) {
            val items = runCatching {
                parseStacks(api.getStacks(instanceId = instanceId, env = scope), scope)
            }.getOrDefault(emptyList())
            items.forEach { merged["${it.environmentId.orEmpty()}|${it.id}"] = it }
        }
        if (merged.isEmpty() && fallbackScopes.isNotEmpty()) {
            for (scope in fallbackScopes) {
                val fallback = runCatching {
                    parseStacks(api.getStacks(instanceId = instanceId, env = scope), scope)
                }.getOrDefault(emptyList())
                fallback.forEach { merged["${it.environmentId.orEmpty()}|${it.id}"] = it }
            }
        }
        return merged.values.sortedBy { it.name.lowercase() }
    }

    private suspend fun loadResources(
        instanceId: String,
        scopes: List<String?>,
        fallbackScopes: List<String?> = emptyList(),
        kind: String,
        request: suspend (String?) -> JsonElement
    ): List<DockhandResourceItem> {
        val merged = linkedMapOf<String, DockhandResourceItem>()
        for (scope in scopes) {
            val items = runCatching {
                parseResources(request(scope), kind = kind, environmentId = scope)
            }.getOrDefault(emptyList())
            items.forEach { merged[it.id] = it }
        }
        if (merged.isEmpty() && fallbackScopes.isNotEmpty()) {
            for (scope in fallbackScopes) {
                val fallback = runCatching {
                    parseResources(request(scope), kind = kind, environmentId = scope)
                }.getOrDefault(emptyList())
                fallback.forEach { merged[it.id] = it }
            }
        }
        return merged.values.toList()
    }

    private suspend fun loadActivity(
        instanceId: String,
        scopes: List<String?>,
        fallbackScopes: List<String?> = emptyList()
    ): List<DockhandActivityItem> {
        val all = mutableListOf<DockhandActivityItem>()
        for (scope in scopes) {
            all += runCatching {
                parseActivity(api.getActivity(instanceId = instanceId, env = scope))
            }.getOrDefault(emptyList())
        }
        if (all.isEmpty() && fallbackScopes.isNotEmpty()) {
            for (scope in fallbackScopes) {
                all += runCatching {
                    parseActivity(api.getActivity(instanceId = instanceId, env = scope))
                }.getOrDefault(emptyList())
            }
        }
        return all.sortedByDescending { it.createdAt.orEmpty() }
            .distinctBy { it.id }
            .take(80)
    }

    private suspend fun loadSchedules(
        instanceId: String,
        scopes: List<String?>,
        fallbackScopes: List<String?> = emptyList()
    ): List<DockhandScheduleItem> {
        val merged = linkedMapOf<String, DockhandScheduleItem>()
        for (scope in scopes) {
            val items = runCatching {
                parseSchedules(api.getSchedules(instanceId = instanceId, env = scope), scope)
            }.getOrDefault(emptyList())
            items.forEach { merged["${it.environmentId.orEmpty()}|${it.id}"] = it }
        }
        if (merged.isEmpty() && fallbackScopes.isNotEmpty()) {
            for (scope in fallbackScopes) {
                val fallback = runCatching {
                    parseSchedules(api.getSchedules(instanceId = instanceId, env = scope), scope)
                }.getOrDefault(emptyList())
                fallback.forEach { merged["${it.environmentId.orEmpty()}|${it.id}"] = it }
            }
        }
        return merged.values.toList()
    }

    suspend fun getContainerDetail(instanceId: String, env: String?, containerId: String): DockhandContainerDetail {
        val normalizedEnv = normalizeEnvironmentId(env)
        val detailElement = api.getContainerDetail(containerId = containerId, instanceId = instanceId, env = normalizedEnv)
        val rootObject = unwrapPrimaryObject(detailElement)
        val detailObject = normalizePrimaryObject(rootObject, "container", "item", "data")
        val container = parseContainerObject(detailObject, normalizedEnv) ?: parseContainerObject(rootObject, normalizedEnv) ?: DockhandContainer(
            id = containerId,
            name = containerId,
            image = "-",
            state = "unknown",
            status = "unknown",
            portsSummary = "-",
            health = null,
            environmentId = normalizedEnv
        )

        val detailsPairs = compactDetails(
            detailObject,
            maxItems = 14,
            excludedKeys = setOf(
                "id", "Id", "config", "Config", "hostConfig", "HostConfig", "networkSettings",
                "NetworkSettings", "graphDriver", "GraphDriver", "mounts", "Mounts", "labels", "Labels",
                "args", "Args", "logPath", "LogPath"
            )
        )

        val rawLogs = runCatching {
            api.getContainerLogs(containerId = containerId, instanceId = instanceId, env = normalizedEnv)
                .string()
                .trim()
        }.getOrDefault("")
        val logs = parseContainerLogs(rawLogs)

        return DockhandContainerDetail(
            container = container,
            rawDetails = detailsPairs,
            logs = logs
        )
    }

    suspend fun getStackDetail(instanceId: String, env: String?, stackName: String): DockhandStackDetail {
        val normalizedEnv = normalizeEnvironmentId(env)
        val encoded = Uri.encode(stackName)
        var detailObject = findStackObject(instanceId = instanceId, env = normalizedEnv, stackName = stackName)

        val stack = parseStackObject(detailObject, normalizedEnv) ?: DockhandStack(
            id = stackName,
            name = stackName,
            status = "unknown",
            services = 0,
            source = null,
            environmentId = normalizedEnv
        )
        val details = compactDetails(
            detailObject,
            maxItems = 14,
            excludedKeys = setOf("compose", "dockerCompose", "content", "yaml", "stackFile")
        )
        val compose = fetchStackCompose(instanceId = instanceId, env = normalizedEnv, encodedStackName = encoded, detailObject = detailObject)

        return DockhandStackDetail(stack = stack, rawDetails = details, compose = compose)
    }

    suspend fun getScheduleDetail(instanceId: String, env: String?, scheduleId: String): DockhandScheduleDetail {
        val normalizedEnv = normalizeEnvironmentId(env)
        val detailObject = findScheduleObject(instanceId = instanceId, env = normalizedEnv, scheduleId = scheduleId)

        val schedule = parseScheduleObject(
            detailObject,
            normalizedEnv,
            fallbackId = scheduleId,
            fallbackName = "Schedule"
        )
        return DockhandScheduleDetail(
            schedule = schedule,
            rawDetails = compactDetails(detailObject, maxItems = 18)
        )
    }

    suspend fun updateStackCompose(
        instanceId: String,
        env: String?,
        stackName: String,
        compose: String
    ): DockhandActionResult {
        val normalizedEnv = normalizeEnvironmentId(env)
        val encoded = Uri.encode(stackName)
        val candidatePaths = listOf(
            "/api/stacks/$encoded/compose",
            "/api/stacks/$encoded/docker-compose",
            "/api/stacks/$encoded/file",
            "/api/stacks/$encoded/update"
        )
        val payloads = listOf(
            "{\"compose\":\"${escapeJson(compose)}\"}",
            "{\"dockerCompose\":\"${escapeJson(compose)}\"}",
            "{\"content\":\"${escapeJson(compose)}\"}",
            "{\"yaml\":\"${escapeJson(compose)}\"}",
            "{\"stackFile\":\"${escapeJson(compose)}\"}"
        )

        var lastError = "Compose update failed"
        var hasNonCompatibilityResponse = false
        for (path in candidatePaths) {
            val fullPath = appendEnvQuery(path, normalizedEnv)
            for (method in listOf("PUT", "POST")) {
                for (payload in payloads) {
                    val request = Request.Builder()
                        .url("https://placeholder.local$fullPath")
                        .method(method, payload.toRequestBody("application/json".toMediaType()))
                        .addHeader("Accept", "application/json")
                        .addHeader("Content-Type", "application/json")
                        .addHeader("X-Homelab-Service", "Dockhand")
                        .addHeader("X-Homelab-Instance-Id", instanceId)
                        .build()

                    val outcome = withContext(Dispatchers.IO) {
                        okHttpClient.newCall(request).execute().use { response ->
                            val responseBody = response.body?.string().orEmpty()
                            if (!response.isSuccessful) {
                                if (response.code in listOf(404, 405)) {
                                    return@withContext null
                                }
                                hasNonCompatibilityResponse = true
                                lastError = responseBody.ifBlank { "Server error ${response.code}: ${response.message}" }
                                return@withContext null
                            }

                            val element = responseBody.takeIf { it.isNotBlank() }?.let {
                                runCatching { Json.parseToJsonElement(it) }.getOrNull()
                            } ?: JsonObject(emptyMap())

                            element
                        }
                    }
                    if (outcome == null) {
                        continue
                    }

                    val result = runCatching {
                        resolveActionResult(
                            instanceId = instanceId,
                            actionLabel = "Compose update",
                            response = outcome
                        )
                    }.getOrElse { error ->
                        lastError = error.localizedMessage ?: lastError
                        null
                    }

                    if (result != null) {
                        if (result.success) {
                            return result
                        }
                        hasNonCompatibilityResponse = true
                        lastError = result.message
                    }
                }
            }
        }

        if (!hasNonCompatibilityResponse) {
            throw IllegalStateException("Compose editing is not supported by this Dockhand API version")
        }
        throw IllegalStateException(lastError)
    }

    suspend fun runContainerAction(
        instanceId: String,
        env: String?,
        containerId: String,
        action: DockhandContainerAction
    ): DockhandActionResult {
        val normalizedEnv = normalizeEnvironmentId(env)
        val response = when (action) {
            DockhandContainerAction.START -> api.startContainer(containerId = containerId, instanceId = instanceId, env = normalizedEnv)
            DockhandContainerAction.STOP -> api.stopContainer(containerId = containerId, instanceId = instanceId, env = normalizedEnv)
            DockhandContainerAction.RESTART -> api.restartContainer(containerId = containerId, instanceId = instanceId, env = normalizedEnv)
        }

        return resolveActionResult(
            instanceId = instanceId,
            actionLabel = action.name.lowercase().replaceFirstChar { it.uppercase() },
            response = response
        )
    }

    suspend fun runStackAction(
        instanceId: String,
        env: String?,
        stackName: String,
        action: DockhandStackAction
    ): DockhandActionResult {
        val normalizedEnv = normalizeEnvironmentId(env)
        val encoded = Uri.encode(stackName)
        val response = when (action) {
            DockhandStackAction.START -> api.startStack(stackName = encoded, instanceId = instanceId, env = normalizedEnv)
            DockhandStackAction.STOP -> api.stopStack(stackName = encoded, instanceId = instanceId, env = normalizedEnv)
            DockhandStackAction.RESTART -> api.restartStack(stackName = encoded, instanceId = instanceId, env = normalizedEnv)
        }

        return resolveActionResult(
            instanceId = instanceId,
            actionLabel = "Stack ${action.name.lowercase().replaceFirstChar { it.uppercase() }}",
            response = response
        )
    }

    private suspend fun authenticateAgainst(
        baseUrl: String,
        username: String,
        password: String,
        mfaCode: String
    ): String = withContext(Dispatchers.IO) {
        if (username.isBlank() && password.isBlank()) {
            if (canAccessDashboard(baseUrl, cookie = null)) {
                return@withContext ""
            }
            throw IllegalStateException("Username and password are required when Dockhand authentication is enabled")
        }

        val loginPaths = listOf("/api/auth/login", "/api/auth/local/login", "/api/login")
        val payloads = buildLoginPayloads(username = username, password = password, mfaCode = mfaCode)

        var mfaRequired = false
        var localLoginDisabled = false
        var lastBody = ""

        for (path in loginPaths) {
            for (payload in payloads) {
                val response = postJson(baseUrl, path, payload)
                val body = response.body?.string().orEmpty()
                lastBody = body.ifBlank { lastBody }
                val lowered = body.lowercase()

                if (response.code == 403 && lowered.contains("local login")) {
                    localLoginDisabled = true
                }

                if (lowered.contains("mfa") || lowered.contains("2fa") || lowered.contains("totp") || lowered.contains("backup code")) {
                    mfaRequired = true
                }

                if (response.isSuccessful) {
                    val cookie = extractCookieHeader(response)
                    response.close()

                    if (cookie.isNotBlank()) {
                        if (canAccessDashboard(baseUrl, cookie)) {
                            return@withContext cookie
                        }
                    } else if (canAccessDashboard(baseUrl, cookie = null)) {
                        // Authentication can be disabled.
                        return@withContext ""
                    }
                } else {
                    response.close()
                }
            }
        }

        if (localLoginDisabled) {
            throw IllegalStateException("Local login is disabled on this Dockhand instance")
        }
        if (mfaRequired && mfaCode.isBlank()) {
            throw IllegalStateException("Two-factor authentication code required")
        }

        val normalized = lastBody.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
        throw IllegalStateException(normalized?.take(200) ?: "Dockhand authentication failed")
    }

    private fun buildLoginPayloads(username: String, password: String, mfaCode: String): List<String> {
        val codeFragment = if (mfaCode.isNotBlank()) {
            listOf(
                "\"mfaToken\":\"${escapeJson(mfaCode)}\"",
                "\"code\":\"${escapeJson(mfaCode)}\"",
                "\"totp\":\"${escapeJson(mfaCode)}\"",
                "\"otp\":\"${escapeJson(mfaCode)}\""
            )
        } else {
            emptyList()
        }

        val base = listOf(
            "{\"username\":\"${escapeJson(username)}\",\"password\":\"${escapeJson(password)}\"}",
            "{\"identity\":\"${escapeJson(username)}\",\"secret\":\"${escapeJson(password)}\"}",
            "{\"email\":\"${escapeJson(username)}\",\"password\":\"${escapeJson(password)}\"}"
        )

        if (codeFragment.isEmpty()) {
            return base
        }

        val withCode = mutableListOf<String>()
        for (raw in base) {
            for (code in codeFragment) {
                withCode += raw.dropLast(1) + ",$code}"
            }
        }
        return withCode + base
    }

    private fun postJson(baseUrl: String, path: String, body: String): Response {
        val request = Request.Builder()
            .url(baseUrl + path)
            .post(body.toRequestBody("application/json".toMediaType()))
            .addHeader("Accept", "application/json")
            .addHeader("Content-Type", "application/json")
            .build()
        return okHttpClient.newCall(request).execute()
    }

    private fun canAccessDashboard(baseUrl: String, cookie: String?): Boolean {
        val builder = Request.Builder()
            .url("$baseUrl/api/dashboard/stats")
            .get()
            .addHeader("Accept", "application/json")

        if (!cookie.isNullOrBlank()) {
            builder.addHeader("Cookie", cookie)
        }

        return runCatching {
            okHttpClient.newCall(builder.build()).execute().use { response ->
                response.code in 200..399
            }
        }.getOrDefault(false)
    }

    private fun extractCookieHeader(response: Response): String {
        val cookies = response.headers("Set-Cookie")
            .mapNotNull { raw -> raw.substringBefore(';').trim().takeIf { it.contains('=') } }
            .distinct()
        return cookies.joinToString("; ")
    }

    private suspend fun resolveActionResult(
        instanceId: String,
        actionLabel: String,
        response: JsonElement
    ): DockhandActionResult {
        val root = unwrapPrimaryObject(response)
        val jobId = root.string("jobId") ?: root.string("job_id")
        if (!jobId.isNullOrBlank()) {
            return pollJob(instanceId = instanceId, jobId = jobId, actionLabel = actionLabel)
        }

        val success = root.boolean("success") ?: !root.string("status").equals("failed", ignoreCase = true)
        val message = firstNonBlank(
            root.string("message"),
            root.string("output"),
            root.string("error"),
            "$actionLabel completed"
        )

        return DockhandActionResult(success = success, message = message)
    }

    private suspend fun pollJob(
        instanceId: String,
        jobId: String,
        actionLabel: String,
        timeoutMs: Long = 180_000L,
        pollDelayMs: Long = 1_200L
    ): DockhandActionResult {
        val start = System.currentTimeMillis()
        while (System.currentTimeMillis() - start <= timeoutMs) {
            val element = api.getJobStatus(jobId = jobId, instanceId = instanceId)
            val root = unwrapPrimaryObject(element)
            val status = root.string("status").orEmpty().lowercase()

            val resultObject = (root["result"] as? JsonObject) ?: JsonObject(emptyMap())
            val nestedStatus = resultObject.string("status").orEmpty().lowercase()

            if (status == "running" || nestedStatus == "running") {
                delay(pollDelayMs)
                continue
            }

            val failed = status == "failed" || nestedStatus == "failed"
            val message = firstNonBlank(
                resultObject.string("message"),
                resultObject.string("output"),
                resultObject.string("error"),
                root.string("error"),
                root.string("message"),
                if (failed) "$actionLabel failed" else "$actionLabel completed"
            )
            return DockhandActionResult(success = !failed, message = message)
        }

        return DockhandActionResult(success = false, message = "$actionLabel timed out")
    }

    private fun parseStats(
        element: JsonElement,
        containers: List<DockhandContainer>,
        stacks: List<DockhandStack>,
        images: List<DockhandResourceItem>,
        volumes: List<DockhandResourceItem>,
        networks: List<DockhandResourceItem>
    ): DockhandStats {
        val root = unwrapPrimaryObject(element)
        val nested = (root["stats"] as? JsonObject) ?: root

        val totalContainers = nested.int("containers")
            ?: nested.int("totalContainers")
            ?: nested.int("total_containers")
            ?: containers.size

        val runningContainers = nested.int("running")
            ?: nested.int("runningContainers")
            ?: nested.int("running_containers")
            ?: containers.count { it.isRunning }

        val stoppedContainers = nested.int("stopped")
            ?: nested.int("stoppedContainers")
            ?: nested.int("stopped_containers")
            ?: (totalContainers - runningContainers).coerceAtLeast(0)

        val issueContainers = nested.int("issues")
            ?: nested.int("issueContainers")
            ?: nested.int("issue_containers")
            ?: containers.count { it.isIssue }

        val stacksCount = nested.int("stacks") ?: stacks.size
        val imagesCount = nested.int("images") ?: images.size
        val volumesCount = nested.int("volumes") ?: volumes.size
        val networksCount = nested.int("networks") ?: networks.size

        return DockhandStats(
            totalContainers = totalContainers,
            runningContainers = runningContainers,
            stoppedContainers = stoppedContainers,
            issueContainers = issueContainers,
            stacks = stacksCount,
            images = imagesCount,
            volumes = volumesCount,
            networks = networksCount
        )
    }

    private fun synthesizeStats(
        containers: List<DockhandContainer>,
        stacks: List<DockhandStack>,
        images: List<DockhandResourceItem>,
        volumes: List<DockhandResourceItem>,
        networks: List<DockhandResourceItem>
    ): DockhandStats {
        val total = containers.size
        val running = containers.count { it.isRunning }
        return DockhandStats(
            totalContainers = total,
            runningContainers = running,
            stoppedContainers = (total - running).coerceAtLeast(0),
            issueContainers = containers.count { it.isIssue },
            stacks = stacks.size,
            images = images.size,
            volumes = volumes.size,
            networks = networks.size
        )
    }

    private fun parseEnvironments(element: JsonElement): List<DockhandEnvironment> {
        return extractObjectArray(element, "environments", "items", "data").mapIndexed { index, obj ->
            val id = obj.string("id")
                ?: obj.int("id")?.toString()
                ?: obj.string("env")
                ?: index.toString()

            DockhandEnvironment(
                id = id,
                name = firstNonBlank(obj.string("name"), obj.string("label"), "Environment $id"),
                isDefault = obj.boolean("isDefault") ?: obj.boolean("default") ?: (index == 0)
            )
        }
    }

    private fun parseContainers(element: JsonElement, environmentId: String?): List<DockhandContainer> {
        return extractObjectArray(element, "containers", "items", "data")
            .mapNotNull { parseContainerObject(it, environmentId) }
            .sortedBy { it.name.lowercase() }
    }

    private fun parseSingleContainer(element: JsonElement, environmentId: String?): DockhandContainer? {
        val obj = unwrapPrimaryObject(element)
        return parseContainerObject(obj, environmentId)
    }

    private fun parseContainerObject(obj: JsonObject, environmentId: String?): DockhandContainer? {
        val stateObject = obj.objectValue("State")
        val configObject = obj.objectValue("Config")
        val healthObject = stateObject?.objectValue("Health")
        val id = obj.string("id") ?: obj.string("Id") ?: obj.string("containerId") ?: return null
        val resolvedEnvironmentId = firstNonBlank(
            obj.string("environmentId"),
            obj.string("environment_id"),
            obj.string("envId"),
            obj.string("env"),
            environmentId
        ).ifBlank { null }

        val rawName = firstNonBlank(
            obj.string("name"),
            obj.firstArrayString("Names"),
            obj.string("Name"),
            configObject?.string("Hostname"),
            id
        )
        val cleanedName = rawName
            .removePrefix("/")
            .removePrefix("[")
            .removeSuffix("]")
            .ifBlank { id.take(12) }

        val image = firstNonBlank(
            obj.string("image"),
            obj.string("Image"),
            configObject?.string("Image"),
            "-"
        )
        val state = firstNonBlank(
            obj.string("state"),
            stateObject?.string("Status"),
            stateObject?.string("State"),
            obj.string("State"),
            stateObject?.boolean("Running")?.let { if (it) "running" else "stopped" },
            "unknown"
        )
        val status = firstNonBlank(
            obj.string("status"),
            obj.string("Status"),
            stateObject?.string("Status"),
            stateObject?.string("Error"),
            state
        )
        val health = firstNonBlank(
            obj.string("health"),
            obj.string("Health"),
            healthObject?.string("Status")
        )

        val portsSummary = parsePortsSummary(obj)

        return DockhandContainer(
            id = id,
            name = cleanedName,
            image = image,
            state = state,
            status = status,
            portsSummary = portsSummary,
            health = health,
            environmentId = resolvedEnvironmentId
        )
    }

    private fun parsePortsSummary(obj: JsonObject): String {
        val candidates = listOf("ports", "Ports")
        for (key in candidates) {
            val portsElement = obj[key] ?: continue
            if (portsElement is JsonArray) {
                val chunks = portsElement.mapNotNull { item ->
                    val entry = item as? JsonObject ?: return@mapNotNull null
                    val privatePort = entry.int("privatePort") ?: entry.int("PrivatePort")
                    val publicPort = entry.int("publicPort") ?: entry.int("PublicPort")
                    val typ = entry.string("type") ?: entry.string("Type")
                    when {
                        privatePort != null && publicPort != null -> "$publicPort:$privatePort${typ?.let { "/$it" } ?: ""}"
                        privatePort != null -> "$privatePort${typ?.let { "/$it" } ?: ""}"
                        else -> null
                    }
                }
                if (chunks.isNotEmpty()) return chunks.take(3).joinToString(", ")
            }
        }
        val networkPorts = obj.objectValue("NetworkSettings")?.get("Ports") as? JsonObject
        if (networkPorts != null) {
            val chunks = networkPorts.entries.mapNotNull { (key, value) ->
                val bindings = value as? JsonArray
                val hostPort = bindings
                    ?.firstOrNull()
                    ?.let { it as? JsonObject }
                    ?.string("HostPort")
                when {
                    !hostPort.isNullOrBlank() -> "$hostPort:$key"
                    key.isNotBlank() -> key
                    else -> null
                }
            }
            if (chunks.isNotEmpty()) return chunks.take(3).joinToString(", ")
        }
        return "-"
    }

    private fun parseStacks(element: JsonElement, environmentId: String?): List<DockhandStack> {
        return extractObjectArray(element, "stacks", "items", "data")
            .map { obj -> parseStackObject(obj, environmentId) ?: DockhandStack(
                id = firstNonBlank(obj.string("id"), obj.int("id")?.toString(), "stack"),
                name = firstNonBlank(obj.string("name"), obj.string("Name"), obj.string("stack"), "stack"),
                status = firstNonBlank(obj.string("status"), obj.string("state"), "unknown"),
                services = obj.int("services") ?: obj.int("serviceCount") ?: 0,
                source = firstNonBlank(obj.string("source"), obj.string("type")),
                environmentId = environmentId
            ) }
            .sortedBy { it.name.lowercase() }
    }

    private fun parseResources(element: JsonElement, kind: String, environmentId: String?): List<DockhandResourceItem> {
        return extractObjectArray(element, kind + "s", "items", "data")
            .mapIndexed { index, obj ->
                val id = firstNonBlank(obj.string("id"), obj.string("name"), obj.int("id")?.toString(), "${kind}_$index")
                val name = firstNonBlank(obj.string("name"), obj.string("repoTags"), obj.string("driver"), id)
                val details = firstNonBlank(
                    obj.string("size"),
                    obj.string("driver"),
                    obj.string("scope"),
                    obj.string("created")
                )
                DockhandResourceItem(id = "${environmentId.orEmpty()}|$id", name = name, details = details)
            }
    }

    private fun parseActivity(element: JsonElement): List<DockhandActivityItem> {
        return extractObjectArray(element, "activity", "items", "data")
            .mapIndexed { index, obj ->
                DockhandActivityItem(
                    id = firstNonBlank(obj.string("id"), obj.int("id")?.toString(), "activity_$index"),
                    action = firstNonBlank(obj.string("action"), obj.string("event"), "event"),
                    target = firstNonBlank(obj.string("target"), obj.string("resource"), obj.string("name"), "-"),
                    status = firstNonBlank(obj.string("status"), obj.string("level"), "info"),
                    createdAt = firstNonBlank(obj.string("createdAt"), obj.string("timestamp"), obj.string("time"))
                )
            }
            .sortedByDescending { it.createdAt.orEmpty() }
            .take(40)
    }

    private fun parseSchedules(element: JsonElement, environmentId: String?): List<DockhandScheduleItem> {
        return extractObjectArray(element, "schedules", "items", "data")
            .mapIndexed { index, obj ->
                parseScheduleObject(
                    obj = obj,
                    environmentId = environmentId,
                    fallbackId = "schedule_$index",
                    fallbackName = "Schedule ${index + 1}"
                )
            }
    }

    private fun parseStackObject(obj: JsonObject, environmentId: String?): DockhandStack? {
        val name = firstNonBlank(obj.string("name"), obj.string("Name"), obj.string("stack"))
        if (name.isBlank()) return null
        val resolvedEnvironmentId = firstNonBlank(
            obj.string("environmentId"),
            obj.string("environment_id"),
            obj.string("envId"),
            obj.string("env"),
            environmentId
        ).ifBlank { null }

        return DockhandStack(
            id = firstNonBlank(obj.string("id"), obj.int("id")?.toString(), name),
            name = name,
            status = firstNonBlank(obj.string("status"), obj.string("state"), "unknown"),
            services = obj.int("services") ?: obj.int("serviceCount") ?: 0,
            source = firstNonBlank(obj.string("source"), obj.string("type")),
            environmentId = resolvedEnvironmentId
        )
    }

    private fun parseScheduleObject(
        obj: JsonObject,
        environmentId: String?,
        fallbackId: String,
        fallbackName: String
    ): DockhandScheduleItem {
        val resolvedEnvironmentId = firstNonBlank(
            obj.string("environmentId"),
            obj.string("environment_id"),
            obj.string("envId"),
            obj.string("env"),
            environmentId
        ).ifBlank { null }

        return DockhandScheduleItem(
            id = firstNonBlank(obj.string("id"), obj.int("id")?.toString(), fallbackId),
            name = firstNonBlank(obj.string("name"), obj.string("task"), fallbackName),
            enabled = obj.boolean("enabled") ?: obj.boolean("isEnabled") ?: true,
            schedule = firstNonBlank(obj.string("cronExpression"), obj.string("cron"), obj.string("schedule"), obj.string("interval")),
            environmentId = resolvedEnvironmentId,
            nextRun = firstNonBlank(obj.string("nextRun"), obj.string("nextExecution"), obj.string("next"), obj.string("nextRound")),
            lastRun = firstNonBlank(obj.string("lastRun"), obj.string("lastExecution"), obj.string("last"), obj.string("completedAt"))
        )
    }

    private fun unwrapPrimaryObject(element: JsonElement): JsonObject {
        return when (element) {
            is JsonObject -> element
            is JsonArray -> JsonObject(mapOf("items" to element))
            else -> JsonObject(emptyMap())
        }
    }

    private fun extractObjectArray(element: JsonElement, vararg keys: String): List<JsonObject> {
        when (element) {
            is JsonArray -> return element.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                keys.forEach { key ->
                    val candidate = element[key]
                    if (candidate is JsonArray) {
                        return candidate.mapNotNull { it as? JsonObject }
                    }
                    if (candidate is JsonObject) {
                        val mapped = candidate.asObjectMapValues()
                        if (mapped.isNotEmpty()) {
                            return mapped
                        }
                    }
                }

                val values = element.values
                if (values.any { it is JsonArray }) {
                    val firstArray = values.firstOrNull { it is JsonArray } as? JsonArray
                    if (firstArray != null) {
                        return firstArray.mapNotNull { it as? JsonObject }
                    }
                }
                if (values.any { it is JsonObject }) {
                    val firstObjectMap = values.firstOrNull { it is JsonObject } as? JsonObject
                    if (firstObjectMap != null) {
                        val mapped = firstObjectMap.asObjectMapValues()
                        if (mapped.isNotEmpty()) {
                            return mapped
                        }
                    }
                }
            }
            else -> Unit
        }
        return emptyList()
    }

    private fun JsonObject.withSyntheticId(fallbackId: String): JsonObject {
        if (containsKey("id") || containsKey("Id") || fallbackId.isBlank()) {
            return this
        }
        return JsonObject(this + ("id" to JsonPrimitive(fallbackId)))
    }

    private fun JsonObject.asObjectMapValues(): List<JsonObject> {
        if (isEmpty()) return emptyList()
        if (!entries.all { it.value is JsonObject }) return emptyList()
        return entries.mapNotNull { (entryKey, entryValue) ->
            (entryValue as? JsonObject)?.withSyntheticId(entryKey)
        }
    }

    private fun JsonObject.string(key: String): String? {
        return (this[key] as? JsonPrimitive)
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.objectValue(key: String): JsonObject? = this[key] as? JsonObject

    private fun JsonObject.firstArrayString(key: String): String? {
        val array = this[key] as? JsonArray ?: return null
        return array.firstOrNull()
            ?.let { it as? JsonPrimitive }
            ?.contentOrNull
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
    }

    private fun JsonObject.int(key: String): Int? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.intOrNull
            ?: primitive.doubleOrNull?.toInt()
            ?: primitive.contentOrNull?.toIntOrNull()
    }

    private fun JsonObject.boolean(key: String): Boolean? {
        val primitive = this[key] as? JsonPrimitive ?: return null
        return primitive.booleanOrNull ?: primitive.contentOrNull?.let {
            when (it.lowercase()) {
                "1", "true", "yes", "on" -> true
                "0", "false", "no", "off" -> false
                else -> null
            }
        }
    }

    private fun JsonElement.toReadableText(): String {
        return when (this) {
            is JsonNull -> ""
            is JsonPrimitive -> this.contentOrNull.orEmpty()
            is JsonArray -> this.joinToString(prefix = "[", postfix = "]") { it.toReadableText() }
            is JsonObject -> this.entries.joinToString(prefix = "{", postfix = "}") {
                "${it.key}: ${it.value.toReadableText()}"
            }
        }
    }

    private fun compactDetails(
        obj: JsonObject,
        maxItems: Int = 18,
        excludedKeys: Set<String> = emptySet()
    ): List<Pair<String, String>> {
        val loweredExcluded = excludedKeys.map { it.lowercase() }.toSet()
        val preferred = listOf(
            "name", "image", "state", "status", "created", "createdAt",
            "command", "entrypoint", "restartPolicy", "networkMode",
            "platform", "runtime", "health", "ports", "mounts", "labels"
        )

        val out = mutableListOf<Pair<String, String>>()
        preferred.forEach { key ->
            if (key.lowercase() in loweredExcluded) return@forEach
            val value = obj[key]?.toReadableText()?.trim().orEmpty()
            if (value.isNotEmpty() && value != "{}" && value != "[]") {
                out += key to value.replace('\n', ' ').take(220)
            }
        }

        if (out.size < maxItems) {
            val existing = out.map { it.first.lowercase() }.toSet()
            obj.entries
                .filter { it.key.lowercase() !in existing && it.key.lowercase() !in loweredExcluded }
                .sortedBy { it.key.lowercase() }
                .forEach { (key, value) ->
                    if (out.size >= maxItems) return@forEach
                    val display = value.toReadableText().trim()
                    if (display.isNotEmpty() && display != "{}" && display != "[]") {
                        out += key to display.replace('\n', ' ').take(220)
                    }
                }
        }

        return out
    }

    private fun firstNonBlank(vararg values: String?): String {
        return values.firstOrNull { !it.isNullOrBlank() }?.trim().orEmpty()
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "")
    }

    private fun cleanOptionalUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        return if (value.isBlank()) null else cleanUrl(value)
    }

    private fun normalizeEnvironmentId(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        if (value.isBlank()) return null
        return when (value.lowercase()) {
            "all", "*", "any" -> null
            else -> value
        }
    }

    private suspend fun findStackObject(instanceId: String, env: String?, stackName: String): JsonObject {
        val normalizedName = stackName.trim().lowercase()
        val direct = runCatching {
            extractObjectArray(api.getStacks(instanceId = instanceId, env = env), "stacks", "items", "data")
                .firstOrNull { obj ->
                    val candidateName = firstNonBlank(obj.string("name"), obj.string("Name"), obj.string("stack")).lowercase()
                    val candidateId = firstNonBlank(obj.string("id"), obj.int("id")?.toString()).lowercase()
                    candidateName == normalizedName || candidateId == normalizedName
                }
        }.getOrNull()
        if (direct != null) {
            return normalizePrimaryObject(direct, "stack", "item", "data")
        }

        if (!env.isNullOrBlank()) {
            val fallback = runCatching {
                extractObjectArray(api.getStacks(instanceId = instanceId, env = null), "stacks", "items", "data")
                    .firstOrNull { obj ->
                        val candidateName = firstNonBlank(obj.string("name"), obj.string("Name"), obj.string("stack")).lowercase()
                        val candidateId = firstNonBlank(obj.string("id"), obj.int("id")?.toString()).lowercase()
                        candidateName == normalizedName || candidateId == normalizedName
                    }
            }.getOrNull()
            if (fallback != null) {
                return normalizePrimaryObject(fallback, "stack", "item", "data")
            }
        }

        return JsonObject(emptyMap())
    }

    private suspend fun findScheduleObject(instanceId: String, env: String?, scheduleId: String): JsonObject {
        val normalizedId = scheduleId.trim().lowercase()
        val direct = runCatching {
            extractObjectArray(api.getSchedules(instanceId = instanceId, env = env), "schedules", "items", "data")
                .firstOrNull { obj ->
                    firstNonBlank(obj.string("id"), obj.int("id")?.toString()).lowercase() == normalizedId
                }
        }.getOrNull()
        if (direct != null) {
            return normalizePrimaryObject(direct, "schedule", "item", "data")
        }

        if (!env.isNullOrBlank()) {
            val fallback = runCatching {
                extractObjectArray(api.getSchedules(instanceId = instanceId, env = null), "schedules", "items", "data")
                    .firstOrNull { obj ->
                        firstNonBlank(obj.string("id"), obj.int("id")?.toString()).lowercase() == normalizedId
                    }
            }.getOrNull()
            if (fallback != null) {
                return normalizePrimaryObject(fallback, "schedule", "item", "data")
            }
        }

        return JsonObject(emptyMap())
    }

    private suspend fun fetchStackCompose(
        instanceId: String,
        env: String?,
        encodedStackName: String,
        detailObject: JsonObject
    ): String = withContext(Dispatchers.IO) {
        val candidatePaths = listOf(
            "/api/stacks/$encodedStackName/compose",
            "/api/stacks/$encodedStackName/docker-compose",
            "/api/stacks/$encodedStackName/file",
            "/api/stacks/$encodedStackName/yaml"
        )

        for (path in candidatePaths) {
            val request = Request.Builder()
                .url("https://placeholder.local${appendEnvQuery(path, env)}")
                .get()
                .addHeader("Accept", "application/json")
                .addHeader("X-Homelab-Service", "Dockhand")
                .addHeader("X-Homelab-Instance-Id", instanceId)
                .build()

            val value = runCatching {
                okHttpClient.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) return@use null
                    extractComposeText(response.body?.string().orEmpty())
                }
            }.getOrNull()

            if (!value.isNullOrBlank()) {
                return@withContext value.trim()
            }
        }

        val fallback = firstNonBlank(
            detailObject.string("compose"),
            detailObject.string("dockerCompose"),
            detailObject.string("content"),
            detailObject.string("yaml"),
            detailObject.string("stackFile")
        )
        return@withContext fallback.ifBlank { "Compose not available" }
    }

    private fun extractComposeText(raw: String): String? {
        val body = raw.trim()
        if (body.isBlank()) return null

        if (body.startsWith("{") || body.startsWith("[")) {
            val parsed = runCatching { Json.parseToJsonElement(body) }.getOrNull()
            if (parsed is JsonObject) {
                val candidate = firstNonBlank(
                    parsed.string("compose"),
                    parsed.string("dockerCompose"),
                    parsed.string("content"),
                    parsed.string("yaml"),
                    parsed.string("stackFile")
                )
                if (candidate.isNotBlank()) return candidate
            }
        } else {
            return body
        }
        return null
    }

    private fun parseContainerLogs(raw: String): String {
        val body = raw.trim()
        if (body.isBlank()) return ""
        if (body.startsWith("{") || body.startsWith("[")) {
            val parsed = runCatching { Json.parseToJsonElement(body) }.getOrNull()
            if (parsed is JsonObject) {
                val candidate = firstNonBlank(
                    parsed.string("logs"),
                    parsed.string("output"),
                    parsed.string("message")
                )
                if (candidate.isNotBlank()) return candidate
            }
        }
        return body
    }

    private fun normalizePrimaryObject(obj: JsonObject, vararg preferredKeys: String): JsonObject {
        preferredKeys.forEach { key ->
            val candidate = obj[key] as? JsonObject
            if (candidate != null && candidate.isNotEmpty()) {
                return candidate
            }
        }
        return obj
    }

    private fun mergeObjects(base: JsonObject, override: JsonObject): JsonObject {
        if (base.isEmpty()) return override
        if (override.isEmpty()) return base

        val merged = base.toMutableMap()
        override.forEach { (key, value) ->
            val text = value.toReadableText().trim()
            if (text.isNotEmpty() && text != "{}" && text != "[]") {
                merged[key] = value
            }
        }
        return JsonObject(merged)
    }

    private fun appendEnvQuery(path: String, env: String?): String {
        if (env.isNullOrBlank()) return path
        val separator = if (path.contains("?")) "&" else "?"
        return "$path${separator}env=${Uri.encode(env)}"
    }

    private fun escapeJson(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
