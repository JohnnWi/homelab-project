package com.homelab.app.data.remote.dto.adguard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class AdGuardStatus(
    @SerialName("dns_addresses") val dnsAddresses: List<String> = emptyList(),
    @SerialName("dns_port") val dnsPort: Int = 0,
    @SerialName("http_port") val httpPort: Int = 0,
    @SerialName("protection_enabled") val protectionEnabled: Boolean = false,
    @SerialName("protection_disabled_duration") val protectionDisabledDuration: Long? = null,
    @SerialName("protection_disabled_until") val protectionDisabledUntil: Long? = null,
    @SerialName("dhcp_available") val dhcpAvailable: Boolean? = null,
    val running: Boolean = false,
    val version: String = "",
    val language: String = "",
    @SerialName("start_time") val startTime: Double? = null
)

@Serializable
data class AdGuardStats(
    @SerialName("time_units") val timeUnits: String? = null,
    @SerialName("num_dns_queries") val numDnsQueries: Long = 0,
    @SerialName("num_blocked_filtering") val numBlockedFiltering: Long = 0,
    @SerialName("num_replaced_safebrowsing") val numReplacedSafebrowsing: Long = 0,
    @SerialName("num_replaced_safesearch") val numReplacedSafesearch: Long = 0,
    @SerialName("num_replaced_parental") val numReplacedParental: Long = 0,
    @SerialName("avg_processing_time") val avgProcessingTime: Double = 0.0,
    @SerialName("top_queried_domains") val topQueriedDomains: List<Map<String, Long>> = emptyList(),
    @SerialName("top_clients") val topClients: List<Map<String, Long>> = emptyList(),
    @SerialName("top_blocked_domains") val topBlockedDomains: List<Map<String, Long>> = emptyList(),
    @SerialName("dns_queries") val dnsQueries: List<Int> = emptyList(),
    @SerialName("blocked_filtering") val blockedFiltering: List<Int> = emptyList(),
    @SerialName("replaced_safebrowsing") val replacedSafebrowsing: List<Int> = emptyList(),
    @SerialName("replaced_parental") val replacedParental: List<Int> = emptyList()
)

@Serializable
data class AdGuardTopItem(
    val name: String,
    val count: Long
)

@Serializable
data class AdGuardQueryLogResponse(
    val data: List<AdGuardQueryLogItem> = emptyList(),
    val items: List<AdGuardQueryLogItem> = emptyList(),
    val oldest: String? = null
)

@Serializable
data class AdGuardQueryLogItem(
    val answer: List<AdGuardDnsAnswer>? = null,
    @SerialName("original_answer") val originalAnswer: List<AdGuardDnsAnswer>? = null,
    val cached: Boolean? = null,
    val upstream: String? = null,
    @SerialName("answer_dnssec") val answerDnssec: Boolean? = null,
    val client: String? = null,
    @SerialName("client_id") val clientId: String? = null,
    @SerialName("client_info") val clientInfo: AdGuardQueryLogClient? = null,
    @SerialName("client_proto") val clientProto: String? = null,
    val ecs: String? = null,
    val elapsedMs: String? = null,
    val question: AdGuardDnsQuestion? = null,
    val rule: String? = null,
    val rules: List<AdGuardResultRule> = emptyList(),
    val reason: String? = null,
    @SerialName("service_name") val serviceName: String? = null,
    val status: String? = null,
    val time: String? = null
)

@Serializable
data class AdGuardDnsQuestion(
    val name: String? = null,
    val type: String? = null
)

@Serializable
data class AdGuardDnsAnswer(
    val name: String? = null,
    val type: String? = null,
    val ttl: Int? = null,
    val value: String? = null
)

@Serializable
data class AdGuardResultRule(
    @SerialName("filter_list_id") val filterListId: Long? = null,
    val text: String? = null
)

@Serializable
data class AdGuardQueryLogClient(
    val disallowed: Boolean? = null,
    @SerialName("disallowed_rule") val disallowedRule: String? = null,
    val name: String? = null,
    val whois: AdGuardQueryLogClientWhois? = null
)

@Serializable
data class AdGuardQueryLogClientWhois(
    val city: String? = null,
    val country: String? = null,
    val orgname: String? = null
)

@Serializable
data class AdGuardFilteringStatus(
    val enabled: Boolean? = null,
    val interval: Int? = null,
    val filters: List<AdGuardFilter> = emptyList(),
    @SerialName("whitelist_filters") val whitelistFilters: List<AdGuardFilter> = emptyList(),
    @SerialName("user_rules") val userRules: List<String> = emptyList()
)

@Serializable
data class AdGuardFilter(
    val enabled: Boolean = true,
    val id: Long = 0,
    @SerialName("last_updated") val lastUpdated: String? = null,
    val name: String = "",
    @SerialName("rules_count") val rulesCount: Int? = null,
    val url: String = ""
)

@Serializable
data class AdGuardFilterAddRequest(
    val name: String,
    val url: String,
    val whitelist: Boolean,
    val enabled: Boolean = true
)

@Serializable
data class AdGuardFilterRemoveRequest(
    val url: String,
    val whitelist: Boolean
)

@Serializable
data class AdGuardFilterSetUrlRequest(
    val data: AdGuardFilterSetUrlData,
    val url: String,
    val whitelist: Boolean
)

@Serializable
data class AdGuardFilterSetUrlData(
    val enabled: Boolean,
    val name: String,
    val url: String,
    val whitelist: Boolean
)

@Serializable
data class AdGuardSetRulesRequest(
    val rules: List<String>
)

@Serializable
data class AdGuardProtectionRequest(
    val enabled: Boolean,
    val duration: Long? = null
)

@Serializable
data class AdGuardBlockedServicesAll(
    @SerialName("blocked_services") val blockedServices: List<AdGuardBlockedService> = emptyList(),
    val groups: List<AdGuardServiceGroup> = emptyList()
)

@Serializable
data class AdGuardBlockedService(
    val id: String,
    val name: String,
    val rules: List<String> = emptyList(),
    @SerialName("group_id") val groupId: String? = null,
    @SerialName("icon_svg") val iconSvg: String? = null
)

@Serializable
data class AdGuardServiceGroup(
    val id: String,
    val name: String? = null
)

@Serializable
data class AdGuardBlockedServicesSchedule(
    val ids: List<String> = emptyList(),
    val schedule: JsonElement? = null
)

@Serializable
data class AdGuardRewriteEntry(
    val domain: String,
    val answer: String,
    val enabled: Boolean? = null
)

@Serializable
data class AdGuardRewriteUpdate(
    val target: AdGuardRewriteEntry,
    val update: AdGuardRewriteEntry
)

@Serializable
data class AdGuardRewriteSettings(
    val enabled: Boolean = true
)

@Serializable
data class AdGuardQueryLogEntry(
    val id: String,
    val time: String,
    val domain: String,
    val client: String,
    val status: String,
    val reason: String?,
    val blocked: Boolean,
    val rule: String? = null
)
