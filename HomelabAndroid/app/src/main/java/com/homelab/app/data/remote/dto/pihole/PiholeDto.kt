package com.homelab.app.data.remote.dto.pihole

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName

@Serializable
data class PiholeStats(
    val queries: PiholeQueryStats,
    val gravity: PiholeGravityStats
)

@Serializable
data class PiholeQueryStats(
    val total: Int,
    val blocked: Int,
    val percent_blocked: Double,
    val unique_domains: Int,
    val forwarded: Int,
    val cached: Int,
    val types: Map<String, Int>? = null
)

@Serializable
data class PiholeGravityStats(
    val domains_being_blocked: Int,
    val last_update: Long
)

@Serializable
data class PiholeBlockingStatus(
    val blocking: String
) {
    val isEnabled: Boolean
        get() = blocking == "enabled"
}

@Serializable
data class PiholeQueryHistory(
    val history: List<PiholeHistoryEntry>
)

@Serializable
data class PiholeHistoryEntry(
    val timestamp: Long,
    val total: Int,
    val blocked: Int
)

@Serializable
data class PiholeUpstream(
    val upstreams: Map<String, PiholeUpstreamEntry>,
    val total_queries: Int,
    val forwarded_queries: Int
)

@Serializable
data class PiholeUpstreamEntry(
    val count: Int,
    val ip: String,
    val name: String,
    val port: Int
)

@Serializable
data class PiholeAuthResponse(
    val session: Session
) {
    @Serializable
    data class Session(
        val sid: String,
        val valid: Boolean,
        val totp: Boolean? = null
    )
}

// Custom domain objects for UI
data class PiholeTopItem(
    val id: String = java.util.UUID.randomUUID().toString(),
    val domain: String,
    val count: Int
)

data class PiholeTopClient(
    val id: String = java.util.UUID.randomUUID().toString(),
    val name: String,
    val ip: String,
    val count: Int
)

data class PiholeQueryLogEntry(
    val id: String = java.util.UUID.randomUUID().toString(),
    val timestamp: Long,
    val domain: String,
    val client: String,
    val status: String
) {
    val isBlocked: Boolean
        get() = status.lowercase().contains("block") || status.lowercase().contains("deny") || status.lowercase().contains("gravity")
}

@Serializable
data class PiholeBlockingRequest(
    val blocking: Boolean,
    val timer: Int? = null
)

@Serializable
data class PiholeStatsLegacyResponse(
    val domains_being_blocked: Int,
    val dns_queries_today: Int,
    val ads_blocked_today: Int,
    val ads_percentage_today: Double,
    val unique_domains: Int,
    val queries_forwarded: Int,
    val queries_cached: Int,
    val clients_ever_seen: Int,
    val unique_clients: Int,
    val dns_queries_all_types: Int,
    val reply_NODATA: Int,
    val reply_NXDOMAIN: Int,
    val reply_CNAME: Int,
    val reply_IP: Int,
    val privacy_level: Int,
    val status: String,
    val gravity_last_updated: GravityLastUpdated? = null
)

@Serializable
data class GravityLastUpdated(
    val file_exists: Boolean,
    val absolute: Long,
    val relative: GravityRelative
)

@Serializable
data class GravityRelative(
    val days: Int,
    val hours: Int,
    val minutes: Int
)
