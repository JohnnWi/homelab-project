package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.TechnitiumApi
import com.homelab.app.data.remote.TlsClientSelector
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request

enum class TechnitiumStatsRange(val apiValue: String) {
    LAST_HOUR("LastHour"),
    LAST_DAY("LastDay"),
    LAST_WEEK("LastWeek"),
    LAST_MONTH("LastMonth")
}

data class TechnitiumTopClient(
    val name: String,
    val domain: String?,
    val hits: Int,
    val rateLimited: Boolean
)

data class TechnitiumTopDomain(
    val name: String,
    val hits: Int
)

data class TechnitiumChartSeries(
    val label: String,
    val values: List<Double>,
    val colorHex: String?
)

data class TechnitiumSummary(
    val totalQueries: Int,
    val totalBlocked: Int,
    val totalClients: Int,
    val blockedZones: Int,
    val cacheEntries: Int,
    val zones: Int,
    val blockListZones: Int,
    val totalNoError: Int,
    val totalNxDomain: Int,
    val totalServerFailure: Int
)

data class TechnitiumSettingsSnapshot(
    val enableBlocking: Boolean,
    val blockListUrls: List<String>,
    val blockListNextUpdatedOn: String?,
    val temporaryDisableBlockingTill: String?,
    val version: String?
)

data class TechnitiumLogFile(
    val fileName: String,
    val size: String
)

data class TechnitiumDashboardData(
    val range: TechnitiumStatsRange,
    val summary: TechnitiumSummary,
    val chartLabels: List<String>,
    val chartSeries: List<TechnitiumChartSeries>,
    val topClients: List<TechnitiumTopClient>,
    val topDomains: List<TechnitiumTopDomain>,
    val topBlockedDomains: List<TechnitiumTopDomain>,
    val blockedDomains: List<String>,
    val zoneCount: Int,
    val cacheRecordCount: Int,
    val logFiles: List<TechnitiumLogFile>,
    val settings: TechnitiumSettingsSnapshot
)

data class TechnitiumOverview(
    val totalQueries: Int,
    val totalBlocked: Int,
    val totalClients: Int,
    val blockedZones: Int
)

data class TechnitiumActionResult(
    val success: Boolean,
    val message: String
)

private class TechnitiumInvalidTokenException : IllegalStateException("Session token expired")

@Singleton
class TechnitiumRepository @Inject constructor(
    private val api: TechnitiumApi,
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val tlsClientSelector: TlsClientSelector
) {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    suspend fun authenticate(
        url: String,
        username: String,
        password: String,
        totp: String = "",
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ): String {
        val cleanUrl = cleanUrl(url)
        val cleanFallback = cleanOptionalUrl(fallbackUrl)
        val user = username.trim()
        val pass = password

        require(user.isNotEmpty()) { "Username is required" }
        require(pass.isNotEmpty()) { "Password is required" }

        return withContext(Dispatchers.IO) {
            val candidates = listOfNotNull(cleanUrl, cleanFallback).distinct()
            var lastError: Exception? = null

            for (base in candidates) {
                try {
                    return@withContext authenticateAgainst(base, user, pass, totp, allowSelfSigned)
                } catch (error: Exception) {
                    lastError = error
                }
            }

            throw lastError ?: IllegalStateException("Technitium authentication failed")
        }
    }

    suspend fun getOverview(instanceId: String): TechnitiumOverview = withValidToken(instanceId) { token ->
        val root = api.getDashboardStats(
            token = token,
            type = TechnitiumStatsRange.LAST_HOUR.apiValue,
            utc = true,
            instanceId = instanceId
        )

        val response = requireSuccess(root)
        val stats = response.obj("stats")

        TechnitiumOverview(
            totalQueries = stats.int("totalQueries"),
            totalBlocked = stats.int("totalBlocked"),
            totalClients = stats.int("totalClients"),
            blockedZones = stats.int("blockedZones")
        )
    }

    suspend fun getDashboard(instanceId: String, range: TechnitiumStatsRange): TechnitiumDashboardData =
        withValidToken(instanceId) { token ->
            coroutineScope {
                val statsDeferred = async {
                    api.getDashboardStats(
                        token = token,
                        type = range.apiValue,
                        utc = true,
                        instanceId = instanceId
                    )
                }

                val topClientsDeferred = async {
                    runCatching {
                        api.getTopStats(
                            token = token,
                            type = range.apiValue,
                            statsType = "TopClients",
                            limit = 20,
                            instanceId = instanceId
                        )
                    }.getOrNull()
                }

                val topDomainsDeferred = async {
                    runCatching {
                        api.getTopStats(
                            token = token,
                            type = range.apiValue,
                            statsType = "TopDomains",
                            limit = 20,
                            instanceId = instanceId
                        )
                    }.getOrNull()
                }

                val topBlockedDeferred = async {
                    runCatching {
                        api.getTopStats(
                            token = token,
                            type = range.apiValue,
                            statsType = "TopBlockedDomains",
                            limit = 20,
                            instanceId = instanceId
                        )
                    }.getOrNull()
                }

                val settingsDeferred = async {
                    runCatching { api.getSettings(token = token, instanceId = instanceId) }.getOrNull()
                }

                val blockedDeferred = async {
                    runCatching { api.listBlockedZones(token = token, domain = "", instanceId = instanceId) }.getOrNull()
                }

                val zonesDeferred = async {
                    runCatching { api.listZones(token = token, pageNumber = 1, zonesPerPage = 1, instanceId = instanceId) }.getOrNull()
                }

                val cacheDeferred = async {
                    runCatching { api.listCache(token = token, domain = "", instanceId = instanceId) }.getOrNull()
                }

                val logsDeferred = async {
                    runCatching { api.listLogs(token = token, instanceId = instanceId) }.getOrNull()
                }

                val statsResponse = requireSuccess(statsDeferred.await())
                val stats = statsResponse.obj("stats")
                val mainChart = statsResponse.obj("mainChartData")

                val chartLabels = mainChart.array("labels").mapNotNull { it.content() }
                val chartSeries = mainChart.array("datasets").mapNotNull { datasetEl ->
                    val dataset = datasetEl.asObjectOrEmpty()
                    val values = dataset.array("data").map { item -> item.doubleOrNull() ?: 0.0 }
                    if (values.isEmpty()) {
                        null
                    } else {
                        TechnitiumChartSeries(
                            label = dataset.string("label").ifEmpty { "Series" },
                            values = values,
                            colorHex = dataset.string("borderColor").ifEmpty { null }
                        )
                    }
                }

                val statsTopClients = parseTopClients(statsResponse)
                val statsTopDomains = parseTopDomains(statsResponse, key = "topDomains")
                val statsTopBlockedDomains = parseTopDomains(statsResponse, key = "topBlockedDomains")

                val topClients = topClientsDeferred.await()
                    ?.let(::requireSuccessOrNull)
                    ?.let(::parseTopClients)
                    ?.takeIf { it.isNotEmpty() }
                    ?: statsTopClients

                val topDomains = topDomainsDeferred.await()
                    ?.let(::requireSuccessOrNull)
                    ?.let { parseTopDomains(it, key = "topDomains") }
                    ?.takeIf { it.isNotEmpty() }
                    ?: statsTopDomains

                val topBlockedDomains = topBlockedDeferred.await()
                    ?.let(::requireSuccessOrNull)
                    ?.let { parseTopDomains(it, key = "topBlockedDomains") }
                    ?.takeIf { it.isNotEmpty() }
                    ?: statsTopBlockedDomains

                val settingsResponse = settingsDeferred.await()?.let(::requireSuccessOrNull)
                val settings = TechnitiumSettingsSnapshot(
                    enableBlocking = settingsResponse?.bool("enableBlocking") ?: true,
                    blockListUrls = settingsResponse?.array("blockListUrls")?.mapNotNull { it.content() } ?: emptyList(),
                    blockListNextUpdatedOn = settingsResponse?.string("blockListNextUpdatedOn").orEmpty().ifBlank { null },
                    temporaryDisableBlockingTill = settingsResponse?.string("temporaryDisableBlockingTill").orEmpty().ifBlank { null },
                    version = settingsResponse?.string("version").orEmpty().ifBlank { null }
                )

                val blockedResponse = blockedDeferred.await()?.let(::requireSuccessOrNull)
                val blockedDomains = mutableListOf<String>()
                blockedResponse?.array("zones")?.forEach { zone ->
                    val zoneName = zone.asObjectOrEmpty().string("name")
                    if (zoneName.isNotBlank()) blockedDomains += zoneName
                }
                blockedResponse?.array("records")?.forEach { record ->
                    val recordName = record.asObjectOrEmpty().string("name")
                    if (recordName.isNotBlank()) blockedDomains += recordName
                }
                val uniqueBlockedDomains = blockedDomains
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .distinct()
                    .sortedBy { it.lowercase() }

                val zoneCount = zonesDeferred.await()
                    ?.let(::requireSuccessOrNull)
                    ?.int("totalZones")
                    ?: zonesDeferred.await()
                        ?.let(::requireSuccessOrNull)
                        ?.array("zones")
                        ?.size
                    ?: 0

                val cacheResponse = cacheDeferred.await()?.let(::requireSuccessOrNull)
                val cacheRecords = (cacheResponse?.array("records")?.size ?: 0) + (cacheResponse?.array("zones")?.size ?: 0)

                val logFiles = logsDeferred.await()
                    ?.let(::requireSuccessOrNull)
                    ?.array("logFiles")
                    ?.map { item ->
                        val log = item.asObjectOrEmpty()
                        TechnitiumLogFile(
                            fileName = log.string("fileName"),
                            size = log.string("size")
                        )
                    }
                    ?.filter { it.fileName.isNotBlank() }
                    ?.sortedByDescending { it.fileName }
                    ?: emptyList()

                val summary = TechnitiumSummary(
                    totalQueries = stats.int("totalQueries"),
                    totalBlocked = stats.int("totalBlocked"),
                    totalClients = stats.int("totalClients"),
                    blockedZones = stats.int("blockedZones"),
                    cacheEntries = stats.int("cachedEntries"),
                    zones = stats.int("zones"),
                    blockListZones = stats.int("blockListZones"),
                    totalNoError = stats.int("totalNoError"),
                    totalNxDomain = stats.int("totalNxDomain"),
                    totalServerFailure = stats.int("totalServerFailure")
                )

                TechnitiumDashboardData(
                    range = range,
                    summary = summary,
                    chartLabels = chartLabels,
                    chartSeries = chartSeries,
                    topClients = topClients,
                    topDomains = topDomains,
                    topBlockedDomains = topBlockedDomains,
                    blockedDomains = uniqueBlockedDomains,
                    zoneCount = zoneCount,
                    cacheRecordCount = cacheRecords,
                    logFiles = logFiles,
                    settings = settings
                )
            }
        }

    suspend fun setBlockingEnabled(instanceId: String, enabled: Boolean): TechnitiumActionResult =
        withValidToken(instanceId) { token ->
            val response = api.setSettings(
                params = mapOf(
                    "token" to token,
                    "enableBlocking" to enabled.toString()
                ),
                instanceId = instanceId
            )
            ensureSuccess(response)
            TechnitiumActionResult(
                success = true,
                message = if (enabled) "DNS blocking enabled" else "DNS blocking disabled"
            )
        }

    suspend fun forceUpdateBlockLists(instanceId: String): TechnitiumActionResult =
        withValidToken(instanceId) { token ->
            val response = api.forceUpdateBlockLists(token = token, instanceId = instanceId)
            ensureSuccess(response)
            TechnitiumActionResult(success = true, message = "Block lists update started")
        }

    suspend fun temporaryDisableBlocking(instanceId: String, minutes: Int): TechnitiumActionResult =
        withValidToken(instanceId) { token ->
            val safeMinutes = minutes.coerceIn(1, 240)
            val response = api.temporaryDisableBlocking(token = token, minutes = safeMinutes, instanceId = instanceId)
            val body = requireSuccess(response)
            val till = body.string("temporaryDisableBlockingTill")
            val msg = if (till.isNotBlank()) {
                "Blocking disabled for $safeMinutes min"
            } else {
                "Blocking temporarily disabled"
            }
            TechnitiumActionResult(success = true, message = msg)
        }

    suspend fun addBlockedDomain(instanceId: String, domain: String): TechnitiumActionResult =
        withValidToken(instanceId) { token ->
            val cleanDomain = normalizeDomain(domain)
            require(cleanDomain.isNotEmpty()) { "Domain is required" }
            val response = api.addBlockedZone(token = token, domain = cleanDomain, instanceId = instanceId)
            ensureSuccess(response)
            TechnitiumActionResult(success = true, message = "$cleanDomain blocked")
        }

    suspend fun removeBlockedDomain(instanceId: String, domain: String): TechnitiumActionResult =
        withValidToken(instanceId) { token ->
            val cleanDomain = normalizeDomain(domain)
            require(cleanDomain.isNotEmpty()) { "Domain is required" }
            val response = api.deleteBlockedZone(token = token, domain = cleanDomain, instanceId = instanceId)
            ensureSuccess(response)
            TechnitiumActionResult(success = true, message = "$cleanDomain unblocked")
        }

    private suspend fun <T> withValidToken(instanceId: String, call: suspend (token: String) -> T): T {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalStateException("Technitium instance not found")
        val storedToken = resolveStoredToken(instance)

        try {
            return call(storedToken)
        } catch (invalid: TechnitiumInvalidTokenException) {
            val username = instance.username.orEmpty().trim()
            val password = instance.password.orEmpty()
            if (username.isBlank() || password.isBlank()) {
                throw IllegalStateException("Technitium session expired. Please login again.")
            }

            val refreshedToken = authenticate(
                url = instance.url,
                username = username,
                password = password,
                totp = "",
                fallbackUrl = instance.fallbackUrl,
                allowSelfSigned = instance.allowSelfSigned
            )

            serviceInstancesRepository.saveInstance(
                instance.copy(
                    token = refreshedToken,
                    apiKey = null
                )
            )

            return call(refreshedToken)
        }
    }

    private fun resolveStoredToken(instance: com.homelab.app.domain.model.ServiceInstance): String {
        val token = instance.token.trim()
        if (token.isNotEmpty()) return token

        val apiKey = instance.apiKey.orEmpty().trim()
        if (apiKey.isNotEmpty()) return apiKey

        throw IllegalStateException("Technitium session token missing")
    }

    private fun authenticateAgainst(baseUrl: String, username: String, password: String, totp: String, allowSelfSigned: Boolean): String {
        val url = buildLoginUrl(baseUrl, username, password, totp)
        val request = Request.Builder().url(url).get().build()

        tlsClientSelector.forAllowSelfSigned(allowSelfSigned).newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            val root = parseObject(body)
            val status = root.string("status").lowercase()

            when (status) {
                "ok" -> {
                    val token = root.string("token")
                    if (token.isBlank()) {
                        throw IllegalStateException("Technitium login returned no session token")
                    }
                    return token
                }
                "2fa-required" -> throw IllegalStateException("Two-factor authentication code required")
                "invalid-token" -> throw IllegalStateException("Invalid token received from Technitium")
                else -> {
                    val message = firstNonEmpty(
                        root.string("errorMessage"),
                        root.string("innerErrorMessage"),
                        body.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty(),
                        "Technitium authentication failed"
                    )
                    throw IllegalStateException(message)
                }
            }
        }
    }

    private fun buildLoginUrl(baseUrl: String, username: String, password: String, totp: String): String {
        val builder = ("$baseUrl/api/user/login").toHttpUrlOrNull()?.newBuilder()
            ?: throw IllegalStateException("Invalid Technitium URL")

        builder.addQueryParameter("user", username)
        builder.addQueryParameter("pass", password)
        if (totp.trim().isNotEmpty()) {
            builder.addQueryParameter("totp", totp.trim())
        }
        builder.addQueryParameter("includeInfo", "true")

        return builder.build().toString()
    }

    private fun requireSuccess(root: JsonObject): JsonObject {
        when (root.string("status").lowercase()) {
            "ok" -> return root.obj("response")
            "invalid-token" -> throw TechnitiumInvalidTokenException()
            "2fa-required" -> throw IllegalStateException("Two-factor authentication code required")
            else -> {
                val message = firstNonEmpty(
                    root.string("errorMessage"),
                    root.string("innerErrorMessage"),
                    "Technitium request failed"
                )
                throw IllegalStateException(message)
            }
        }
    }

    private fun requireSuccessOrNull(root: JsonObject): JsonObject? {
        return try {
            requireSuccess(root)
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureSuccess(root: JsonObject) {
        requireSuccess(root)
    }

    private fun parseTopClients(response: JsonObject): List<TechnitiumTopClient> {
        return response.array("topClients")
            .map { it.asObjectOrEmpty() }
            .map {
                TechnitiumTopClient(
                    name = it.string("name"),
                    domain = it.string("domain").ifBlank { null },
                    hits = it.int("hits"),
                    rateLimited = it.bool("rateLimited")
                )
            }
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.hits }
            .take(20)
    }

    private fun parseTopDomains(response: JsonObject, key: String): List<TechnitiumTopDomain> {
        return response.array(key)
            .map { it.asObjectOrEmpty() }
            .map {
                TechnitiumTopDomain(
                    name = it.string("name"),
                    hits = it.int("hits")
                )
            }
            .filter { it.name.isNotBlank() }
            .sortedByDescending { it.hits }
            .take(20)
    }

    private fun parseObject(raw: String): JsonObject {
        return runCatching {
            json.parseToJsonElement(raw).jsonObject
        }.getOrElse {
            JsonObject(mapOf("status" to JsonPrimitive("error"), "errorMessage" to JsonPrimitive("Invalid Technitium response")))
        }
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "")
    }

    private fun cleanOptionalUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return cleanUrl(raw)
    }

    private fun normalizeDomain(raw: String): String {
        return raw.trim().trim('.').lowercase()
    }

    private fun firstNonEmpty(vararg values: String): String {
        return values.firstOrNull { it.trim().isNotEmpty() }?.trim()?.take(220).orEmpty()
    }
}

private fun JsonElement?.asObjectOrEmpty(): JsonObject {
    return (this as? JsonObject) ?: JsonObject(emptyMap())
}

private fun JsonObject.obj(key: String): JsonObject {
    return this[key].asObjectOrEmpty()
}

private fun JsonObject.array(key: String): JsonArray {
    return this[key] as? JsonArray ?: JsonArray(emptyList())
}

private fun JsonObject.string(key: String): String {
    return this[key]?.jsonPrimitive?.contentOrNull?.trim().orEmpty()
}

private fun JsonObject.int(key: String): Int {
    val value = this[key] ?: return 0
    return when (value) {
        is JsonPrimitive -> {
            value.intOrNull
                ?: value.doubleOrNull?.toInt()
                ?: when (value.content.lowercase()) {
                    "true" -> 1
                    "false" -> 0
                    else -> 0
                }
        }
        else -> 0
    }
}

private fun JsonObject.bool(key: String): Boolean {
    val value = this[key] ?: return false
    return when (value) {
        is JsonPrimitive -> {
            value.booleanOrNull
                ?: when (value.content.lowercase()) {
                    "1", "true", "yes" -> true
                    else -> false
                }
        }
        else -> false
    }
}

private fun JsonElement.content(): String? {
    return (this as? JsonPrimitive)?.contentOrNull
}

private fun JsonElement.doubleOrNull(): Double? {
    val primitive = this as? JsonPrimitive ?: return null
    return primitive.doubleOrNull ?: primitive.contentOrNull?.toDoubleOrNull()
}
