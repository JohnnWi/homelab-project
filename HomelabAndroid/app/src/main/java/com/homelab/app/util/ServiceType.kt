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
    DOCKMON("DockMon"),
    KOMODO("Komodo"),
    MALTRAIL("Maltrail"),
    UPTIME_KUMA("Uptime Kuma"),
    UNIFI_NETWORK("Ubiquiti Network"),
    CRAFTY_CONTROLLER("Crafty Controller"),
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
    WAKAPI("Wakapi"),
    PROXMOX("Proxmox VE"),
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
            FLARESOLVERR
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
                "DOCKMON" -> DOCKMON
                "KOMODO" -> KOMODO
                "MALTRAIL" -> MALTRAIL
                "UPTIMEKUMA",
                "UPTIME_KUMA" -> UPTIME_KUMA
                "UBIQUITI",
                "UBIQUITI_NETWORK",
                "UNIFI",
                "UNIFI_NETWORK" -> UNIFI_NETWORK
                "CRAFTY",
                "CRAFTY_CONTROLLER" -> CRAFTY_CONTROLLER
                else -> entries.firstOrNull { it.name == normalized } ?: UNKNOWN
            }
        }
    }

    val isArrStack: Boolean
        get() = this in arrStackTypes

    val isHomeService: Boolean
        get() = this != UNKNOWN && !isArrStack
}
