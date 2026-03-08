package com.homelab.app.data.repository

import com.homelab.app.util.ServiceType
import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.remote.api.PiholeApi
import com.homelab.app.data.remote.dto.pihole.*
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.serialization.json.*
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PiholeRepository @Inject constructor(
    private val api: PiholeApi,
    private val settingsManager: SettingsManager
) {
    private suspend fun getAuth(): String? {
        return settingsManager.getConnection(ServiceType.PIHOLE).firstOrNull()?.token
    }

    suspend fun authenticate(url: String, password: String): String {
        val cleanUrl = url.trimEnd('/') + "/api/auth"
        try {
            val response = api.authenticate(
                url = cleanUrl, 
                credentials = mapOf("password" to password)
            )
            return response.session.sid
        } catch (e: Exception) {
            // Fallback for v5: if auth endpoint fails, use password/token directly
            return password
        }
    }

    suspend fun getStats(): PiholeStats = api.getStats(auth = getAuth())

    suspend fun getBlockingStatus(): PiholeBlockingStatus = api.getBlockingStatus(auth = getAuth())

    suspend fun setBlocking(enabled: Boolean, timer: Int? = null) {
        api.setBlocking(auth = getAuth(), request = PiholeBlockingRequest(blocking = enabled, timer = timer))
    }

    // Domains (v6 API)
    suspend fun getDomains(): List<PiholeDomainDto> = api.getDomains(auth = getAuth()).domains

    suspend fun addDomain(domain: String, list: PiholeDomainListType) {
        api.addDomain(list = list.value, auth = getAuth(), request = PiholeAddDomainRequest(domain = domain))
    }

    suspend fun removeDomain(domain: String, list: PiholeDomainListType) {
        api.removeDomain(list = list.value, domain = domain, auth = getAuth())
    }

    suspend fun getTopDomains(count: Int = 10): List<PiholeTopItem> {
        return try {
            val raw = api.getTopDomains(auth = getAuth(), count = count)
            parseTopItems(raw, listOf("top_domains", "top_queries", "domains", "queries"))
        } catch (e: Exception) {
            val raw = api.getTopQueries(auth = getAuth(), count = count)
            parseTopItems(raw, listOf("top_domains", "top_queries", "domains", "queries"))
        }
    }

    suspend fun getTopBlocked(count: Int = 10): List<PiholeTopItem> {
        return try {
            val raw = api.getTopBlocked(auth = getAuth(), count = count)
            parseTopItems(raw, listOf("top_blocked", "top_ads", "blocked", "ads"))
        } catch (e: Exception) {
            val raw = api.getTopAds(auth = getAuth(), count = count)
            parseTopItems(raw, listOf("top_blocked", "top_ads", "blocked", "ads"))
        }
    }

    suspend fun getTopClients(count: Int = 10): List<PiholeTopClient> {
        return try {
            val raw = api.getTopClients(auth = getAuth(), count = count)
            parseTopClients(raw)
        } catch (e: Exception) {
            val raw = api.getTopSources(auth = getAuth(), count = count)
            parseTopClients(raw)
        }
    }

    suspend fun getQueryHistory(): PiholeQueryHistory = api.getQueryHistory(auth = getAuth())

    suspend fun getUpstreams(): PiholeUpstream = api.getUpstreams(auth = getAuth())

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
}
