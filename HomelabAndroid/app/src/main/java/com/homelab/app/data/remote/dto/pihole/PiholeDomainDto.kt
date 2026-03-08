package com.homelab.app.data.remote.dto.pihole

import kotlinx.serialization.Serializable

@Serializable
enum class PiholeDomainListType(val value: String) {
    ALLOW("allow"),
    DENY("deny")
}

@Serializable
data class PiholeDomainListResponse(
    val domains: List<PiholeDomainDto> = emptyList()
)

@Serializable
data class PiholeDomainDto(
    val id: Int,
    val domain: String,
    val kind: String, // "exact" or "regex"
    val list: String? = null
) {
    val type: PiholeDomainListType?
        get() = PiholeDomainListType.entries.find { it.value == list }
}
