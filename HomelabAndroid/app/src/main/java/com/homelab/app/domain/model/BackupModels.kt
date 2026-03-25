package com.homelab.app.domain.model

import com.homelab.app.util.ServiceType
import kotlinx.serialization.Serializable
import java.util.UUID

// MARK: - Backup Envelope

@Serializable
data class BackupEnvelope(
    val version: Int,
    val exportedAt: String,
    val appVersion: String,
    val services: List<BackupServiceEntry>
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

// MARK: - Backup Service Entry

@Serializable
data class BackupServiceEntry(
    val type: String,
    val label: String,
    val url: String,
    val token: String? = null,
    val username: String? = null,
    val apiKey: String? = null,
    val piholePassword: String? = null,
    val piholeAuthMode: String? = null,
    val fallbackUrl: String? = null,
    val allowSelfSigned: Boolean,
    val password: String? = null,
    val isPreferred: Boolean
)

// MARK: - ServiceType ↔ Backup String Mapping

object BackupServiceTypeMapper {

    /** Canonical lowercase backup key for a given ServiceType. */
    fun backupKey(type: ServiceType): String {
        return when (type) {
            ServiceType.PORTAINER -> "portainer"
            ServiceType.PIHOLE -> "pihole"
            ServiceType.ADGUARD_HOME -> "adguard_home"
            ServiceType.BESZEL -> "beszel"
            ServiceType.HEALTHCHECKS -> "healthchecks"
            ServiceType.GITEA -> "gitea"
            ServiceType.NGINX_PROXY_MANAGER -> "nginx_proxy_manager"
            ServiceType.PATCHMON -> "patchmon"
            ServiceType.JELLYSTAT -> "jellystat"
            ServiceType.PLEX -> "plex"
            ServiceType.UNKNOWN -> "unknown"
        }
    }

    /** Resolve a backup key string back to a ServiceType, or null if unknown. */
    fun serviceType(key: String): ServiceType? {
        val normalized = key.lowercase().trim()
        return when (normalized) {
            "portainer" -> ServiceType.PORTAINER
            "pihole" -> ServiceType.PIHOLE
            "adguard_home", "adguardhome" -> ServiceType.ADGUARD_HOME
            "beszel" -> ServiceType.BESZEL
            "healthchecks" -> ServiceType.HEALTHCHECKS
            "gitea" -> ServiceType.GITEA
            "nginx_proxy_manager", "nginxproxymanager" -> ServiceType.NGINX_PROXY_MANAGER
            "patchmon" -> ServiceType.PATCHMON
            "jellystat" -> ServiceType.JELLYSTAT
            "plex" -> ServiceType.PLEX
            else -> null
        }
    }

    /** PiHoleAuthMode string mapping. */
    fun piholeAuthMode(string: String?): PiHoleAuthMode? {
        return when (string?.lowercase()) {
            "session" -> PiHoleAuthMode.SESSION
            "legacy" -> PiHoleAuthMode.LEGACY
            else -> null
        }
    }

    fun backupAuthMode(mode: PiHoleAuthMode?): String? {
        return when (mode) {
            PiHoleAuthMode.SESSION -> "session"
            PiHoleAuthMode.LEGACY -> "legacy"
            null -> null
        }
    }
}

// MARK: - Conversion Helpers

fun ServiceInstance.toBackupEntry(isPreferred: Boolean): BackupServiceEntry {
    return BackupServiceEntry(
        type = BackupServiceTypeMapper.backupKey(type),
        label = label, // in iOS it was displayLabel, here label is equivalent
        url = url,
        token = token.ifBlank { null },
        username = username,
        apiKey = apiKey,
        piholePassword = piholePassword,
        piholeAuthMode = BackupServiceTypeMapper.backupAuthMode(piholeAuthMode),
        fallbackUrl = fallbackUrl,
        allowSelfSigned = allowSelfSigned,
        password = password,
        isPreferred = isPreferred
    )
}

fun BackupServiceEntry.toServiceInstance(): ServiceInstance? {
    val mappedType = BackupServiceTypeMapper.serviceType(type) ?: return null
    return ServiceInstance(
        id = UUID.randomUUID().toString(),
        type = mappedType,
        label = label,
        url = url,
        token = token ?: "",
        username = username,
        apiKey = apiKey,
        piholePassword = piholePassword,
        piholeAuthMode = BackupServiceTypeMapper.piholeAuthMode(piholeAuthMode),
        fallbackUrl = fallbackUrl,
        allowSelfSigned = allowSelfSigned,
        password = password
    )
}
