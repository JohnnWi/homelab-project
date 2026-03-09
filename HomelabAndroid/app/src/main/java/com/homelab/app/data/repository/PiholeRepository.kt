package com.homelab.app.data.repository

import com.homelab.app.util.ServiceType
import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.remote.api.PiholeApi
import com.homelab.app.data.remote.dto.pihole.*
import com.homelab.app.domain.model.PiHoleAuthMode
import com.homelab.app.domain.model.ServiceConnection
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.*
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PiholeRepository @Inject constructor(
    private val api: PiholeApi,
    private val settingsManager: SettingsManager
) {
    private suspend fun getConnection(): ServiceConnection? {
        return settingsManager.getConnection(ServiceType.PIHOLE).firstOrNull()
    }

    private fun getAuth(connection: ServiceConnection?): String? {
        if (connection == null || connection.token.isBlank()) return null
        return when (connection.piholeAuthMode) {
            PiHoleAuthMode.SESSION -> null
            PiHoleAuthMode.LEGACY, null -> connection.token
        }
    }

    private fun isUnauthorized(error: Throwable): Boolean {
        return error is HttpException && error.code() == 401
    }

    private suspend fun refreshAuth(connection: ServiceConnection): String? {
        val secret = connection.piHoleStoredSecret ?: return connection.token.takeIf { it.isNotBlank() }
        val refreshed = authenticate(url = connection.url, password = secret)
        val mode = if (refreshed == secret) PiHoleAuthMode.LEGACY else PiHoleAuthMode.SESSION
        val updated = connection.copy(
            token = refreshed,
            piholePassword = connection.piholePassword ?: secret,
            piholeAuthMode = mode
        )
        if (updated != connection) {
            settingsManager.saveConnection(updated)
        }
        return updated.token
    }

    private suspend fun <T> withAuthRetry(block: suspend (String?) -> T): T {
        val connection = getConnection()
        val auth = getAuth(connection)
        try {
            return block(auth)
        } catch (error: Exception) {
            if (connection != null && isUnauthorized(error)) {
                val oldToken = connection.token
                val refreshedToken = try {
                    refreshAuth(connection)
                } catch (_: Exception) {
                    null
                }
                val refreshedConnection = getConnection()
                val retryAuth = getAuth(refreshedConnection ?: connection.copy(token = refreshedToken ?: oldToken))
                val tokenChanged = refreshedToken != null && refreshedToken != oldToken
                if (tokenChanged || retryAuth != auth) {
                    return block(retryAuth)
                }
            }
            throw error
        }
    }

    suspend fun authenticate(url: String, password: String): String {
        val cleanUrl = url.trimEnd('/') + "/api/auth"
        var authFailure: Exception? = null
        try {
            val response = api.authenticate(
                url = cleanUrl, 
                credentials = mapOf("password" to password)
            )
            return response.session.sid
        } catch (e: Exception) {
            authFailure = e
        }

        val encodedSecret = java.net.URLEncoder.encode(password, Charsets.UTF_8.name())
        val legacyUrl = "${url.trimEnd('/')}/admin/api.php?summaryRaw&auth=$encodedSecret"
        val legacyValid = try {
            when (val response = api.validateLegacyAuth(url = legacyUrl)) {
                is JsonObject -> response.isNotEmpty()
                is JsonArray -> response.isNotEmpty()
                else -> false
            }
        } catch (_: Exception) {
            false
        }

        if (legacyValid) {
            return password
        }

        throw authFailure ?: IllegalStateException("Authentication failed")
    }

    suspend fun getStats(): PiholeStats = withAuthRetry { auth -> api.getStats(auth = auth) }

    suspend fun getBlockingStatus(): PiholeBlockingStatus = withAuthRetry { auth -> api.getBlockingStatus(auth = auth) }

    suspend fun setBlocking(enabled: Boolean, timer: Int? = null) {
        withAuthRetry { auth ->
            api.setBlocking(auth = auth, request = PiholeBlockingRequest(blocking = enabled, timer = timer))
        }
    }

    // Domains (v6 + legacy v5)
    suspend fun getDomains(): List<PiholeDomainDto> {
        return withAuthRetry { auth ->
            try {
                val raw = api.getDomainsRaw(auth = auth)
                PiholeDomainListResponse.fromJson(raw).domains
            } catch (e: Exception) {
                val raw = api.getDomainsLegacy(auth = auth)
                PiholeDomainListResponse.fromJson(raw).domains
            }
        }
    }

    suspend fun addDomain(domain: String, list: PiholeDomainListType) {
        withAuthRetry { auth ->
            try {
                api.addDomain(list = list.value, auth = auth, request = PiholeAddDomainRequest(domain = domain))
            } catch (e: Exception) {
                val legacyList = if (list == PiholeDomainListType.ALLOW) "white" else "black"
                api.addDomainLegacy(list = legacyList, domain = domain, auth = auth)
            }
        }
    }

    suspend fun removeDomain(domain: String, list: PiholeDomainListType) {
        withAuthRetry { auth ->
            try {
                api.removeDomain(list = list.value, domain = domain, auth = auth)
            } catch (e: Exception) {
                val legacyList = if (list == PiholeDomainListType.ALLOW) "white" else "black"
                api.removeDomainLegacy(list = legacyList, domain = domain, auth = auth)
            }
        }
    }

    suspend fun getTopDomains(count: Int = 10): List<PiholeTopItem> {
        return withAuthRetry { auth ->
            try {
                val raw = api.getTopDomains(auth = auth, count = count)
                parseTopItems(raw, listOf("top_domains", "top_queries", "domains", "queries"))
            } catch (e: Exception) {
                val raw = api.getTopQueries(auth = auth, count = count)
                parseTopItems(raw, listOf("top_domains", "top_queries", "domains", "queries"))
            }
        }
    }

    suspend fun getTopBlocked(count: Int = 10): List<PiholeTopItem> {
        return withAuthRetry { auth ->
            try {
                val raw = api.getTopBlocked(auth = auth, count = count)
                parseTopItems(raw, listOf("top_blocked", "top_ads", "blocked", "ads"))
            } catch (e: Exception) {
                val raw = api.getTopAds(auth = auth, count = count)
                parseTopItems(raw, listOf("top_blocked", "top_ads", "blocked", "ads"))
            }
        }
    }

    suspend fun getTopClients(count: Int = 10): List<PiholeTopClient> {
        return withAuthRetry { auth ->
            try {
                val raw = api.getTopClients(auth = auth, count = count)
                parseTopClients(raw)
            } catch (e: Exception) {
                val raw = api.getTopSources(auth = auth, count = count)
                parseTopClients(raw)
            }
        }
    }

    suspend fun getQueryHistory(): PiholeQueryHistory = withAuthRetry { auth -> api.getQueryHistory(auth = auth) }

    suspend fun getQueries(from: Long, until: Long): List<PiholeQueryLogEntry> {
        return withAuthRetry { auth ->
            try {
                val raw = api.getQueries(auth = auth, from = from, until = until)
                val parsed = parseQueryEntries(raw)
                if (parsed.isNotEmpty()) parsed else {
                    val legacy = api.getQueriesLegacy(auth = auth, from = from, until = until)
                    parseQueryEntries(legacy)
                }
            } catch (e: Exception) {
                val legacy = api.getQueriesLegacy(auth = auth, from = from, until = until)
                parseQueryEntries(legacy)
            }
        }.sortedByDescending { it.timestamp }
    }

    suspend fun getUpstreams(): PiholeUpstream = withAuthRetry { auth -> api.getUpstreams(auth = auth) }

    // MARK: - Legacy / Dynamic Parsing (Matches iOS Swift logic for v5/v6 APIs)

    private fun parseTopItems(jsonObj: JsonElement, rootKeys: List<String>): List<PiholeTopItem> {
        if (jsonObj !is JsonObject) return emptyList()

        for (key in rootKeys) {
            val element = jsonObj[key] ?: continue

            // format: { "domain": count }
            if (element is JsonObject) {
                return element.entries.mapNotNull {
                    val countStr = it.value.jsonPrimitive.content
                    val count = countStr.toDoubleOrNull()?.toInt() ?: countStr.toIntOrNull() ?: 0
                    PiholeTopItem(domain = it.key, count = count)
                }.sortedByDescending { it.count }
            }

            // format: [ { "domain": "...", "count": ... } ]
            if (element is JsonArray) {
                return element.mapNotNull { item ->
                    if (item !is JsonObject) return@mapNotNull null
                    val domain = item["domain"]?.jsonPrimitive?.content 
                        ?: item["query"]?.jsonPrimitive?.content 
                        ?: item["name"]?.jsonPrimitive?.content 
                        ?: return@mapNotNull null
                    val count = item["count"]?.jsonPrimitive?.intOrNull 
                        ?: item["hits"]?.jsonPrimitive?.intOrNull 
                        ?: return@mapNotNull null
                    PiholeTopItem(domain = domain, count = count)
                }.sortedByDescending { it.count }
            }
        }
        return emptyList()
    }

    private fun parseTopClients(jsonObj: JsonElement): List<PiholeTopClient> {
        if (jsonObj !is JsonObject) return emptyList()
        val rootKeys = listOf("top_clients", "top_sources", "clients", "sources")

        for (key in rootKeys) {
            val element = jsonObj[key] ?: continue

            // format: { "hostname|ip": count }
            if (element is JsonObject) {
                return element.entries.mapNotNull {
                    val countStr = it.value.jsonPrimitive.content
                    val count = countStr.toIntOrNull() ?: 0
                    val ipStr = it.key
                    val name = if (ipStr.contains("|")) ipStr.substringBefore("|") else ipStr
                    val ip = if (ipStr.contains("|")) ipStr.substringAfter("|") else ipStr
                    PiholeTopClient(name = name, ip = ip, count = count)
                }.sortedByDescending { it.count }
            }

            // format: [ { "name": "...", "ip": "...", "count": ... } ]
            if (element is JsonArray) {
                return element.mapNotNull { item ->
                    if (item !is JsonObject) return@mapNotNull null
                    val name = item["name"]?.jsonPrimitive?.content ?: item["ip"]?.jsonPrimitive?.content ?: "Unknown"
                    val ip = item["ip"]?.jsonPrimitive?.content ?: name
                    val count = item["count"]?.jsonPrimitive?.intOrNull ?: return@mapNotNull null
                    if (count == 0) return@mapNotNull null
                    PiholeTopClient(name = name, ip = ip, count = count)
                }.sortedByDescending { it.count }
            }
        }
        return emptyList()
    }

    private fun parseQueryEntries(element: JsonElement): List<PiholeQueryLogEntry> {
        return when (element) {
            is JsonObject -> {
                val rootKeys = listOf("queries", "data", "query_log", "results")
                for (key in rootKeys) {
                    val arr = element[key] as? JsonArray ?: continue
                    val parsed = parseQueryArray(arr)
                    if (parsed.isNotEmpty()) return parsed
                }
                emptyList()
            }
            is JsonArray -> parseQueryArray(element)
            else -> emptyList()
        }
    }

    private fun parseQueryArray(array: JsonArray): List<PiholeQueryLogEntry> {
        return array.mapNotNull { item ->
            when (item) {
                is JsonObject -> parseQueryObject(item)
                is JsonArray -> parseLegacyQueryArray(item)
                else -> null
            }
        }
    }

    private fun parseQueryObject(obj: JsonObject): PiholeQueryLogEntry? {
        val timestamp = parseTimestamp(obj["timestamp"])
            ?: parseTimestamp(obj["time"])
            ?: parseTimestamp(obj["t"])
            ?: parseTimestamp(obj["date"])
            ?: 0L

        val domain = parseString(obj["domain"])
            ?: parseString(obj["query"])
            ?: parseString(obj["name"])
            ?: "unknown"

        val client = parseString(obj["client"])
            ?: parseString(obj["client_name"])
            ?: parseString(obj["client_ip"])
            ?: parseString(obj["source"])
            ?: "unknown"

        val statusRaw = parseString(obj["status"])
            ?: parseString(obj["reply"])
            ?: parseString(obj["type"])
            ?: parseString(obj["result"])
            ?: "unknown"

        if (timestamp == 0L && domain == "unknown" && client == "unknown") return null

        val status = normalizeQueryStatus(statusRaw)
        val id = "${timestamp}|${domain}|${client}|${status}"
        return PiholeQueryLogEntry(id = id, timestamp = timestamp, domain = domain, client = client, status = status)
    }

    private fun parseLegacyQueryArray(array: JsonArray): PiholeQueryLogEntry? {
        if (array.size < 4) return null

        val timestamp = parseTimestamp(array.getOrNull(0)) ?: 0L
        val domain = parseString(array.getOrNull(2)) ?: "unknown"
        val client = parseString(array.getOrNull(3)) ?: "unknown"
        val explicitStatus = parseString(array.getOrNull(4))
        val typeStatus = parseString(array.getOrNull(1))
        val status = normalizeQueryStatus(explicitStatus ?: typeStatus ?: "unknown")

        val id = "${timestamp}|${domain}|${client}|${status}"
        return PiholeQueryLogEntry(id = id, timestamp = timestamp, domain = domain, client = client, status = status)
    }

    private fun parseString(element: JsonElement?): String? {
        return when (element) {
            is JsonPrimitive -> element.contentOrNull
            is JsonObject -> {
                val keys = listOf("domain", "query", "name", "ip", "client", "id")
                keys.firstNotNullOfOrNull { key -> (element[key] as? JsonPrimitive)?.contentOrNull }
            }
            else -> null
        }
    }

    private fun parseTimestamp(element: JsonElement?): Long? {
        return when (element) {
            is JsonPrimitive -> element.longOrNull ?: element.contentOrNull?.toLongOrNull()
            else -> null
        }
    }

    private fun normalizeQueryStatus(raw: String): String {
        val lower = raw.lowercase()
        if (lower.contains("block") || lower.contains("deny") || lower.contains("gravity")) return "blocked"
        if (lower.contains("cache")) return "cached"
        if (lower.contains("forward") || lower.contains("allow") || lower.contains("ok")) return "allowed"

        val code = raw.toIntOrNull()
        return when (code) {
            null -> raw
            0 -> "unknown"
            1 -> "blocked"
            2 -> "allowed"
            3 -> "cached"
            else -> "code $code"
        }
    }
}
