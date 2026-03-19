package com.homelab.app.data.remote.dto.healthchecks

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HealthchecksChecksResponse(
    val checks: List<HealthchecksCheck> = emptyList()
)

@Serializable
data class HealthchecksCheck(
    val name: String,
    val slug: String? = null,
    val tags: String? = null,
    val desc: String? = null,
    val grace: Int? = null,
    @SerialName("n_pings") val nPings: Int? = null,
    val status: String = "",
    val started: Boolean? = null,
    @SerialName("last_ping") val lastPing: String? = null,
    @SerialName("next_ping") val nextPing: String? = null,
    @SerialName("manual_resume") val manualResume: Boolean? = null,
    val methods: String? = null,
    @SerialName("start_kw") val startKw: String? = null,
    @SerialName("success_kw") val successKw: String? = null,
    @SerialName("failure_kw") val failureKw: String? = null,
    @SerialName("filter_subject") val filterSubject: Boolean? = null,
    @SerialName("filter_body") val filterBody: Boolean? = null,
    @SerialName("filter_http_body") val filterHttpBody: Boolean? = null,
    @SerialName("filter_default_fail") val filterDefaultFail: Boolean? = null,
    @SerialName("badge_url") val badgeUrl: String? = null,
    val uuid: String? = null,
    @SerialName("unique_key") val uniqueKey: String? = null,
    @SerialName("ping_url") val pingUrl: String? = null,
    @SerialName("update_url") val updateUrl: String? = null,
    @SerialName("pause_url") val pauseUrl: String? = null,
    @SerialName("resume_url") val resumeUrl: String? = null,
    val channels: String? = null,
    val timeout: Int? = null,
    val schedule: String? = null,
    val tz: String? = null
) {
    val id: String get() = uuid ?: uniqueKey ?: name
    val tagsList: List<String> get() = tags?.split(" ")?.filter { it.isNotBlank() }.orEmpty()
    val channelsList: List<String> get() = channels?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() }.orEmpty()
    val apiIdentifier: String? get() = uuid ?: uniqueKey
    val isReadOnly: Boolean get() = uuid == null
    val isPaused: Boolean get() = status == "paused"
}

@Serializable
data class HealthchecksPingResponse(
    val pings: List<HealthchecksPing> = emptyList()
)

@Serializable
data class HealthchecksPing(
    val type: String,
    val date: String,
    val n: Int,
    val scheme: String? = null,
    @SerialName("remote_addr") val remoteAddr: String? = null,
    val method: String? = null,
    @SerialName("ua") val userAgent: String? = null,
    @SerialName("rid") val runId: String? = null,
    val duration: Double? = null,
    @SerialName("body_url") val bodyUrl: String? = null
)

@Serializable
data class HealthchecksFlip(
    val timestamp: String,
    val up: Int
) {
    val isUp: Boolean get() = up == 1
}

@Serializable
data class HealthchecksFlipsResponse(
    val flips: List<HealthchecksFlip> = emptyList()
)

@Serializable
data class HealthchecksChannelsResponse(
    val channels: List<HealthchecksChannel> = emptyList()
)

@Serializable
data class HealthchecksChannel(
    val id: String,
    val name: String,
    val kind: String
)

@Serializable
data class HealthchecksBadgesResponse(
    val badges: Map<String, HealthchecksBadgeFormats> = emptyMap()
)

@Serializable
data class HealthchecksBadgeFormats(
    val svg: String? = null,
    val svg3: String? = null,
    val json: String? = null,
    val json3: String? = null,
    val shields: String? = null,
    val shields3: String? = null
)

@Serializable
data class HealthchecksCheckPayload(
    val name: String? = null,
    val slug: String? = null,
    val tags: String? = null,
    val desc: String? = null,
    val timeout: Int? = null,
    val grace: Int? = null,
    val schedule: String? = null,
    val tz: String? = null,
    @SerialName("manual_resume") val manualResume: Boolean? = null,
    val methods: String? = null,
    val channels: String? = null
)
