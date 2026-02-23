package com.homelab.app.domain.model

import com.homelab.app.util.ServiceType
import kotlinx.serialization.Serializable

@Serializable
data class ServiceConnection(
    val type: ServiceType,
    val url: String, // Primary URL (usually Internal IP)
    val token: String = "",
    val username: String? = null,
    val apiKey: String? = null,
    val fallbackUrl: String? = null // Secondary URL (usually External/Cloudlare)
) {
    val id: String get() = type.name
}
