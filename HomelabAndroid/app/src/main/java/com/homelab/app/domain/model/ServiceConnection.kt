package com.homelab.app.domain.model

import com.homelab.app.util.ServiceType
import kotlinx.serialization.Serializable

@Serializable
enum class PiHoleAuthMode {
    SESSION,
    LEGACY
}

@Serializable
data class ServiceConnection(
    val type: ServiceType,
    val url: String, // Primary URL (usually Internal IP)
    val token: String = "",
    val username: String? = null,
    val apiKey: String? = null,
    val piholePassword: String? = null,
    val piholeAuthMode: PiHoleAuthMode? = null,
    val fallbackUrl: String? = null // Secondary URL (usually External/Cloudlare)
) {
    val id: String get() = type.name

    val piHoleStoredSecret: String?
        get() = when {
            !piholePassword.isNullOrBlank() -> piholePassword
            type == ServiceType.PIHOLE && !apiKey.isNullOrBlank() -> apiKey
            else -> null
        }
}
