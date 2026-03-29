package com.homelab.app.util

import androidx.annotation.Keep
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class ServiceType(val displayName: String) {
    PORTAINER("Portainer"),
    PIHOLE("Pi-hole"),
    ADGUARD_HOME("AdGuard Home"),
    TECHNITIUM("Technitium DNS"),
    PLEX("Plex"),
    JELLYSTAT("Jellystat"),
    BESZEL("Beszel"),
    GITEA("Gitea"),
    NGINX_PROXY_MANAGER("Nginx Proxy Manager"),
    PANGOLIN("Pangolin"),
    HEALTHCHECKS("Healthchecks"),
    LINUX_UPDATE("Linux Update"),
    DOCKHAND("Dockhand"),
    PATCHMON("PatchMon"),
    RADARR("Radarr"),
    SONARR("Sonarr"),
    LIDARR("Lidarr"),
    QBITTORRENT("qBittorrent"),
    JELLYSEERR("Jellyseerr"),
    PROWLARR("Prowlarr"),
    BAZARR("Bazarr"),
    GLUETUN("Gluetun"),
    FLARESOLVERR("FlareSolverr"),
    JELLYFIN("Jellyfin"),
    IMMICH("Immich"),
    GRAFANA("Grafana"),
    SABNZBD("SABnzbd"),
    PROXMOX("Proxmox VE"),
    PROXMOX_BACKUP_SERVER("Proxmox Backup Server"),
    TDARR("Tdarr"),
    UNKNOWN("Unknown");

    companion object {
        val arrStackTypes: List<ServiceType> = listOf(
            RADARR,
            SONARR,
            LIDARR,
            QBITTORRENT,
            JELLYSEERR,
            PROWLARR,
            BAZARR,
            GLUETUN,
            FLARESOLVERR,
            JELLYFIN,
            SABNZBD,
            TDARR
        )

        val homeTypes: List<ServiceType> = entries.filter { it != UNKNOWN && it !in arrStackTypes }

        fun fromStoredName(raw: String?): ServiceType {
            if (raw.isNullOrBlank()) return UNKNOWN
            val normalized = raw.trim().replace('-', '_').uppercase()
            return when (normalized) {
                "LINUXUPDATE",
                "LINUX_UPDATE" -> LINUX_UPDATE
                "TECHNITIUM",
                "TECHNITIUM_DNS",
                "TECHNITIUMDNS" -> TECHNITIUM
                "PANGOLIN" -> PANGOLIN
                "DOCKHAND" -> DOCKHAND
                "JELLYFIN" -> JELLYFIN
                "IMMICH" -> IMMICH
                "GRAFANA" -> GRAFANA
                "SABNZBD" -> SABNZBD
                "PROXMOX",
                "PROXMOX_VE",
                "PROXMOXVE" -> PROXMOX
                "PBS",
                "PROXMOX_BACKUP_SERVER",
                "PROXMOXBACKUPSERVER" -> PROXMOX_BACKUP_SERVER
                "TDARR" -> TDARR
                else -> entries.firstOrNull { it.name == normalized } ?: UNKNOWN
            }
        }
    }

    val isArrStack: Boolean
        get() = this in arrStackTypes

    val isHomeService: Boolean
        get() = this != UNKNOWN && !isArrStack
}
