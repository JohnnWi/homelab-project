package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.AdGuardHomeApi
import com.homelab.app.data.remote.dto.adguard.*
import com.homelab.app.domain.model.ServiceInstance
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdGuardHomeRepository @Inject constructor(
    private val api: AdGuardHomeApi,
    private val serviceInstancesRepository: ServiceInstancesRepository
) {

    suspend fun authenticate(url: String, username: String, password: String): String {
        val cleanUrl = normalizeControlStatusUrl(url)
        val creds = "$username:$password"
        val token = java.util.Base64.getEncoder().encodeToString(creds.toByteArray(Charsets.UTF_8))
        api.authenticate(url = cleanUrl, authorization = "Basic $token")
        return token
    }

    suspend fun getStatus(instanceId: String): AdGuardStatus = api.getStatus(instanceId = instanceId)

    suspend fun getStats(instanceId: String, intervalMs: Long? = null): AdGuardStats = api.getStats(instanceId = instanceId, intervalMs = intervalMs)

    suspend fun setProtection(instanceId: String, enabled: Boolean, durationSeconds: Int? = null) {
        val durationMs = durationSeconds?.let { it.coerceAtLeast(0) * 1000L }
        api.setProtection(instanceId = instanceId, request = AdGuardProtectionRequest(enabled = enabled, duration = durationMs))
    }

    suspend fun getQueryLog(
        instanceId: String,
        limit: Int = 200,
        search: String? = null,
        responseStatus: String? = null,
        offset: Int? = null
    ): List<AdGuardQueryLogEntry> {
        val response = api.getQueryLog(instanceId = instanceId, limit = limit, search = search, responseStatus = responseStatus, offset = offset)
        val raw = if (response.data.isNotEmpty()) response.data else response.items
        return raw.map { mapQueryEntry(it) }
    }

    suspend fun getFilteringStatus(instanceId: String): AdGuardFilteringStatus = api.getFilteringStatus(instanceId = instanceId)

    suspend fun setUserRules(instanceId: String, rules: List<String>) {
        api.setUserRules(instanceId = instanceId, request = AdGuardSetRulesRequest(rules = rules))
    }

    suspend fun addFilter(instanceId: String, name: String, url: String, whitelist: Boolean, enabled: Boolean = true) {
        api.addFilter(instanceId = instanceId, request = AdGuardFilterAddRequest(name = name, url = url, whitelist = whitelist, enabled = enabled))
    }

    suspend fun setFilter(instanceId: String, filter: AdGuardFilter, enabled: Boolean, whitelist: Boolean) {
        val data = AdGuardFilterSetUrlData(enabled = enabled, name = filter.name, url = filter.url, whitelist = whitelist)
        api.setFilter(instanceId = instanceId, request = AdGuardFilterSetUrlRequest(data = data, url = filter.url, whitelist = whitelist))
    }

    suspend fun removeFilter(instanceId: String, url: String, whitelist: Boolean) {
        api.removeFilter(instanceId = instanceId, request = AdGuardFilterRemoveRequest(url = url, whitelist = whitelist))
    }

    suspend fun getBlockedServicesAll(instanceId: String): AdGuardBlockedServicesAll = api.getBlockedServicesAll(instanceId = instanceId)

    suspend fun getBlockedServicesSchedule(instanceId: String): AdGuardBlockedServicesSchedule = api.getBlockedServicesSchedule(instanceId = instanceId)

    suspend fun updateBlockedServices(instanceId: String, ids: List<String>, schedule: AdGuardBlockedServicesSchedule) {
        api.updateBlockedServices(instanceId = instanceId, request = schedule.copy(ids = ids))
    }

    suspend fun getRewrites(instanceId: String): List<AdGuardRewriteEntry> = api.getRewrites(instanceId = instanceId)

    suspend fun addRewrite(instanceId: String, domain: String, answer: String, enabled: Boolean = true) {
        api.addRewrite(instanceId = instanceId, request = AdGuardRewriteEntry(domain = domain, answer = answer, enabled = enabled))
    }

    suspend fun updateRewrite(instanceId: String, target: AdGuardRewriteEntry, update: AdGuardRewriteEntry) {
        api.updateRewrite(instanceId = instanceId, request = AdGuardRewriteUpdate(target = target, update = update))
    }

    suspend fun deleteRewrite(instanceId: String, entry: AdGuardRewriteEntry) {
        api.deleteRewrite(instanceId = instanceId, request = entry)
    }

    suspend fun getRewriteSettings(instanceId: String): AdGuardRewriteSettings = api.getRewriteSettings(instanceId = instanceId)

    suspend fun updateRewriteSettings(instanceId: String, enabled: Boolean) {
        api.updateRewriteSettings(instanceId = instanceId, request = AdGuardRewriteSettings(enabled = enabled))
    }

    suspend fun getInstance(instanceId: String): ServiceInstance? = serviceInstancesRepository.getInstance(instanceId)

    fun mapTopItems(items: List<Map<String, Long>>): List<AdGuardTopItem> {
        return items.mapNotNull { map ->
            val first = map.entries.firstOrNull() ?: return@mapNotNull null
            AdGuardTopItem(name = first.key, count = first.value)
        }
    }

    private fun mapQueryEntry(item: AdGuardQueryLogItem): AdGuardQueryLogEntry {
        val domain = item.question?.name ?: item.rule ?: ""
        val clientIp = item.client.orEmpty()
        val clientName = item.clientInfo?.name?.takeIf { it.isNotBlank() }
        val clientDisplay = when {
            clientName != null && clientIp.isNotBlank() && clientName != clientIp -> "$clientName ($clientIp)"
            clientName != null -> clientName
            else -> clientIp
        }
        val reason = item.reason
        val status = item.status?.lowercase().orEmpty()
        val blockedStatus = status in setOf(
            "blocked",
            "blocked_safebrowsing",
            "blocked_parental",
            "filtered",
            "safe_search"
        )
        val blockedReason = reason?.contains("blocked", ignoreCase = true) == true ||
            reason?.contains("filtered", ignoreCase = true) == true ||
            reason?.contains("safe browsing", ignoreCase = true) == true ||
            reason?.contains("parental", ignoreCase = true) == true ||
            reason?.contains("safe search", ignoreCase = true) == true
        val blocked = blockedStatus || blockedReason || item.clientInfo?.disallowed == true
        val id = listOfNotNull(item.time, domain, clientDisplay).joinToString("|").ifBlank { java.util.UUID.randomUUID().toString() }
        return AdGuardQueryLogEntry(
            id = id,
            time = item.time ?: "",
            domain = domain,
            client = clientDisplay,
            status = status,
            reason = reason,
            blocked = blocked,
            rule = item.rule ?: item.rules.firstOrNull()?.text
        )
    }

    fun toAllowRule(domain: String): String {
        var clean = domain.trim()
        if (clean.startsWith("@@")) return clean
        if (!clean.startsWith("||")) {
            clean = "||$clean"
        }
        if (!clean.endsWith("^")) {
            clean = "$clean^"
        }
        return "@@$clean"
    }

    private fun normalizeControlStatusUrl(url: String): String {
        val trimmed = url.trim().trimEnd('/')
        return when {
            trimmed.endsWith("/control/status") -> trimmed
            trimmed.endsWith("/control") -> "$trimmed/status"
            else -> "$trimmed/control/status"
        }
    }
}
