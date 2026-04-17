package com.homelab.app.domain.model

import com.homelab.app.util.ServiceType
import kotlinx.serialization.Serializable

@Serializable
enum class PiHoleAuthMode {
    SESSION,
    LEGACY
}

@Serializable
data class ServiceInstance(
    val id: String,
    val type: ServiceType,
    val label: String,
    val url: String,
    val token: String = "",
    val proxmoxCsrfToken: String? = null,
    val proxmoxOtp: String? = null,
    val username: String? = null,
    val apiKey: String? = null,
    val piholePassword: String? = null,
    val piholeAuthMode: PiHoleAuthMode? = null,
    val fallbackUrl: String? = null,
    val allowSelfSigned: Boolean = false,
    val password: String? = null
) {
    val piHoleStoredSecret: String?
        get() = when {
            !piholePassword.isNullOrBlank() -> piholePassword
            type == ServiceType.PIHOLE && !apiKey.isNullOrBlank() -> apiKey
            else -> null
        }

    fun updatingToken(token: String, authMode: PiHoleAuthMode? = piholeAuthMode): ServiceInstance {
        return copy(
            token = token,
            piholePassword = if (type == ServiceType.PIHOLE) piHoleStoredSecret else piholePassword,
            piholeAuthMode = authMode
        )
    }
}

@Serializable
data class ServiceConnection(
    val type: ServiceType,
    val url: String, // Primary URL (usually Internal IP)
    val token: String = "",
    val proxmoxCsrfToken: String? = null,
    val proxmoxOtp: String? = null,
    val username: String? = null,
    val apiKey: String? = null,
    val piholePassword: String? = null,
    val piholeAuthMode: PiHoleAuthMode? = null,
    val fallbackUrl: String? = null, // Secondary URL (usually External/Cloudlare)
    val allowSelfSigned: Boolean = false
) {
    val id: String get() = type.name

    val piHoleStoredSecret: String?
        get() = when {
            !piholePassword.isNullOrBlank() -> piholePassword
            type == ServiceType.PIHOLE && !apiKey.isNullOrBlank() -> apiKey
            else -> null
        }

    fun migratedInstance(id: String): ServiceInstance {
        return ServiceInstance(
            id = id,
            type = type,
            label = type.displayName,
            url = url,
            token = token,
            proxmoxCsrfToken = proxmoxCsrfToken,
            proxmoxOtp = proxmoxOtp,
            username = username,
            apiKey = apiKey,
            piholePassword = if (type == ServiceType.PIHOLE) piHoleStoredSecret else piholePassword,
            piholeAuthMode = piholeAuthMode,
            fallbackUrl = fallbackUrl,
            allowSelfSigned = allowSelfSigned
        )
    }
}
