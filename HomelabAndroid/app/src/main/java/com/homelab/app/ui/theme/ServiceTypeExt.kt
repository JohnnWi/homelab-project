package com.homelab.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import com.homelab.app.util.ServiceType

@Composable
fun isThemeDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

val ServiceType.primaryColor: Color
    @Composable
    get() = when (this) {
        ServiceType.PORTAINER -> Color(0xFF13B5EA)
        ServiceType.PIHOLE -> Color(0xFFCD2326)
        ServiceType.ADGUARD_HOME -> Color(0xFF34C759)
        ServiceType.TECHNITIUM -> Color(0xFF2D9CDB)
        ServiceType.PLEX -> Color(0xFFE5A00D)
        ServiceType.PROXMOX -> if (isThemeDark()) Color(0xFFF59E0B) else Color(0xFFB45309)
        ServiceType.JELLYSTAT -> Color(0xFFC93DF6)
        ServiceType.BESZEL -> Color(0xFF8B5CF6)
        ServiceType.GITEA -> Color(0xFF609926)
        ServiceType.NGINX_PROXY_MANAGER -> Color(0xFFF15B2A)
        ServiceType.PANGOLIN -> Color(0xFFFF8A3D)
        ServiceType.HEALTHCHECKS -> Color(0xFF16A34A)
        ServiceType.LINUX_UPDATE -> Color(0xFF14B8A6)
        ServiceType.DOCKHAND -> Color(0xFF4A90A4)
        ServiceType.DOCKMON -> Color(0xFF0EA5E9)
        ServiceType.KOMODO -> if (isThemeDark()) Color(0xFFF97316) else Color(0xFFC2410C)
        ServiceType.MALTRAIL -> Color(0xFFDC2626)
        ServiceType.UPTIME_KUMA -> if (isThemeDark()) Color(0xFF22C55E) else Color(0xFF15803D)
        ServiceType.UNIFI_NETWORK -> Color(0xFF007AFF)
        ServiceType.CRAFTY_CONTROLLER -> Color(0xFF2E86FF)
        ServiceType.PATCHMON -> Color(0xFF0EA5E9)
        ServiceType.RADARR -> Color(0xFFFFC230)
        ServiceType.SONARR -> Color(0xFF89C5CF)
        ServiceType.LIDARR -> Color(0xFF006B3E)
        ServiceType.QBITTORRENT -> Color(0xFF2C86C1)
        ServiceType.JELLYSEERR -> Color(0xFF6C63FF)
        ServiceType.PROWLARR -> Color(0xFFF97316)
        ServiceType.BAZARR -> Color(0xFF2563EB)
        ServiceType.GLUETUN -> Color(0xFF06B6D4)
        ServiceType.FLARESOLVERR -> Color(0xFFFF4500)
        ServiceType.WAKAPI -> Color(0xFF2563EB)
        ServiceType.UNKNOWN -> if (isThemeDark()) Color.LightGray else Color.Gray
    }

val ServiceType.backgroundColor: Color
    @Composable
    get() = when (this) {
        ServiceType.PORTAINER -> Color(0xFF13B5EA).copy(alpha = 0.12f)
        ServiceType.PIHOLE -> Color(0xFFCD2326).copy(alpha = 0.12f)
        ServiceType.ADGUARD_HOME -> Color(0xFF34C759).copy(alpha = 0.12f)
        ServiceType.TECHNITIUM -> Color(0xFF2D9CDB).copy(alpha = 0.12f)
        ServiceType.PROXMOX -> (if (isThemeDark()) Color(0xFFF59E0B) else Color(0xFFB45309)).copy(alpha = 0.07f)
        ServiceType.PLEX -> Color(0xFFE5A00D).copy(alpha = 0.12f)
        ServiceType.JELLYSTAT -> Color(0xFFC93DF6).copy(alpha = 0.12f)
        ServiceType.BESZEL -> Color(0xFF8B5CF6).copy(alpha = 0.12f)
        ServiceType.GITEA -> Color(0xFF609926).copy(alpha = 0.12f)
        ServiceType.NGINX_PROXY_MANAGER -> Color(0xFFF15B2A).copy(alpha = 0.12f)
        ServiceType.PANGOLIN -> Color(0xFFFF8A3D).copy(alpha = 0.12f)
        ServiceType.HEALTHCHECKS -> Color(0xFF16A34A).copy(alpha = 0.12f)
        ServiceType.LINUX_UPDATE -> Color(0xFF14B8A6).copy(alpha = 0.12f)
        ServiceType.DOCKHAND -> Color(0xFF4A90A4).copy(alpha = 0.10f)
        ServiceType.DOCKMON -> Color(0xFF0EA5E9).copy(alpha = 0.12f)
        ServiceType.KOMODO -> Color(0xFFF97316).copy(alpha = 0.12f)
        ServiceType.MALTRAIL -> Color(0xFFDC2626).copy(alpha = 0.12f)
        ServiceType.UPTIME_KUMA -> Color(0xFF22C55E).copy(alpha = 0.12f)
        ServiceType.UNIFI_NETWORK -> Color(0xFF007AFF).copy(alpha = 0.12f)
        ServiceType.CRAFTY_CONTROLLER -> Color(0xFF2E86FF).copy(alpha = 0.12f)
        ServiceType.PATCHMON -> Color(0xFF0EA5E9).copy(alpha = 0.12f)
        ServiceType.RADARR -> Color(0xFFFFC230).copy(alpha = 0.12f)
        ServiceType.SONARR -> Color(0xFF89C5CF).copy(alpha = 0.12f)
        ServiceType.LIDARR -> Color(0xFF006B3E).copy(alpha = 0.12f)
        ServiceType.QBITTORRENT -> Color(0xFF2C86C1).copy(alpha = 0.12f)
        ServiceType.JELLYSEERR -> Color(0xFF6C63FF).copy(alpha = 0.12f)
        ServiceType.PROWLARR -> Color(0xFFF97316).copy(alpha = 0.12f)
        ServiceType.BAZARR -> Color(0xFF2563EB).copy(alpha = 0.12f)
        ServiceType.GLUETUN -> Color(0xFF06B6D4).copy(alpha = 0.12f)
        ServiceType.FLARESOLVERR -> Color(0xFFFF4500).copy(alpha = 0.12f)
        ServiceType.WAKAPI -> Color(0xFF2563EB).copy(alpha = 0.12f)
        ServiceType.UNKNOWN -> if (isThemeDark()) Color(0xFF334155) else Color(0xFFF1F5F9)
    }

val ServiceType.iconUrl: String
    get() = when (this) {
        ServiceType.PORTAINER -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/portainer.png"
        ServiceType.PIHOLE -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pi-hole.png"
        ServiceType.ADGUARD_HOME -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/adguard-home.png"
        ServiceType.TECHNITIUM -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/technitium.png"
        ServiceType.PROXMOX -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/proxmox.png"
        ServiceType.PLEX -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/plex.png"
        ServiceType.JELLYSTAT -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellystat.png"
        ServiceType.BESZEL -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/beszel.png"
        ServiceType.GITEA -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/gitea.png"
        ServiceType.NGINX_PROXY_MANAGER -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/nginx-proxy-manager.png"
        ServiceType.PANGOLIN -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pangolin.png"
        ServiceType.HEALTHCHECKS -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/healthchecks.png"
        ServiceType.LINUX_UPDATE -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/linux-update-dashboard.png"
        ServiceType.DOCKHAND -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/dockhand.png"
        ServiceType.DOCKMON -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/dockmon.png"
        ServiceType.KOMODO -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/komodo.png"
        ServiceType.MALTRAIL -> "https://raw.githubusercontent.com/stamparm/maltrail/master/html/images/mlogo.png"
        ServiceType.UPTIME_KUMA -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/uptime-kuma.png"
        ServiceType.UNIFI_NETWORK -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/ubiquiti-unifi.png"
        ServiceType.CRAFTY_CONTROLLER -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/crafty-controller.png"
        ServiceType.PATCHMON -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/patchmon.png"
        ServiceType.RADARR -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/radarr.png"
        ServiceType.SONARR -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/sonarr.png"
        ServiceType.LIDARR -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/lidarr.png"
        ServiceType.QBITTORRENT -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/qbittorrent.png"
        ServiceType.JELLYSEERR -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/jellyseerr.png"
        ServiceType.PROWLARR -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/prowlarr.png"
        ServiceType.BAZARR -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/bazarr.png"
        ServiceType.GLUETUN -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/gluetun.png"
        ServiceType.FLARESOLVERR -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/flaresolverr.png"
        ServiceType.WAKAPI -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/wakapi.png"
        ServiceType.UNKNOWN -> ""
    }

val ServiceType.iconCandidates: List<String>
    get() {
        val candidates = LinkedHashSet<String>()
        val primary = iconUrl.trim()
        if (primary.isNotEmpty()) {
            candidates += primary
        }

        if (this == ServiceType.TECHNITIUM) {
            candidates += "https://cdn.jsdelivr.net/gh/selfhst/icons/png/technitium-dns-server.png"
            candidates += "https://raw.githubusercontent.com/selfhst/icons/main/png/technitium.png"
            candidates += "https://raw.githubusercontent.com/selfhst/icons/main/png/technitium-dns-server.png"
            return candidates.toList()
        }

        if (this == ServiceType.DOCKHAND) {
            candidates += "https://raw.githubusercontent.com/selfhst/icons/main/png/dockhand.png"
            candidates += "https://dockhand.pro/favicon-32x32.png"
            candidates += "https://dockhand.pro/favicon.ico"
            return candidates.toList()
        }

        if (this == ServiceType.DOCKMON) {
            candidates += "https://raw.githubusercontent.com/selfhst/icons/main/png/dockmon.png"
            candidates += "https://www.docker.com/wp-content/uploads/2022/03/Moby-logo.png"
            return candidates.toList()
        }

        if (this == ServiceType.KOMODO) {
            candidates += "https://raw.githubusercontent.com/selfhst/icons/main/png/komodo.png"
            candidates += "https://komo.do/favicon.ico"
            return candidates.toList()
        }

        if (this == ServiceType.MALTRAIL) {
            candidates += "https://raw.githubusercontent.com/stamparm/maltrail/master/html/images/mlogo.png"
            candidates += "https://raw.githubusercontent.com/stamparm/maltrail/master/html/images/favicon.png"
            return candidates.toList()
        }

        if (this == ServiceType.UPTIME_KUMA) {
            candidates += "https://raw.githubusercontent.com/selfhst/icons/main/png/uptime-kuma.png"
            return candidates.toList()
        }

        if (this == ServiceType.UNIFI_NETWORK) {
            return candidates.toList()
        }

        if (primary.isEmpty()) return emptyList()

        val jsDelivrPrefix = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/"
        if (primary.startsWith(jsDelivrPrefix)) {
            val slug = primary.removePrefix(jsDelivrPrefix)
            candidates += "https://raw.githubusercontent.com/selfhst/icons/main/png/$slug"
        }

        return candidates.toList()
    }

val ServiceType.fallbackIcon: ImageVector
    get() = when (this) {
        ServiceType.PORTAINER -> Icons.Default.Widgets
        ServiceType.PIHOLE -> Icons.Default.Security
        ServiceType.ADGUARD_HOME -> Icons.Default.Security
        ServiceType.TECHNITIUM -> Icons.Default.Dns
        ServiceType.PLEX -> Icons.Default.Storage
        ServiceType.JELLYSTAT -> Icons.Default.Storage
        ServiceType.BESZEL -> Icons.Default.Storage
        ServiceType.GITEA -> Icons.Default.Source
        ServiceType.NGINX_PROXY_MANAGER -> Icons.Default.Widgets
        ServiceType.PANGOLIN -> Icons.Default.VpnLock
        ServiceType.HEALTHCHECKS -> Icons.Default.CheckCircle
        ServiceType.LINUX_UPDATE -> Icons.Default.Source
        ServiceType.DOCKHAND -> Icons.Default.Hub
        ServiceType.DOCKMON -> Icons.Default.Hub
        ServiceType.KOMODO -> Icons.Default.Widgets
        ServiceType.MALTRAIL -> Icons.Default.Security
        ServiceType.UPTIME_KUMA -> Icons.Default.CheckCircle
        ServiceType.UNIFI_NETWORK -> Icons.Default.Router
        ServiceType.CRAFTY_CONTROLLER -> Icons.Default.Dns
        ServiceType.PATCHMON -> Icons.Default.Storage
        ServiceType.RADARR -> Icons.Default.Movie
        ServiceType.SONARR -> Icons.Default.Tv
        ServiceType.LIDARR -> Icons.Default.MusicNote
        ServiceType.QBITTORRENT -> Icons.Default.Download
        ServiceType.JELLYSEERR -> Icons.Default.Star
        ServiceType.PROWLARR -> Icons.Default.Search
        ServiceType.BAZARR -> Icons.Default.Subtitles
        ServiceType.GLUETUN -> Icons.Default.VpnLock
        ServiceType.FLARESOLVERR -> Icons.Default.LocalFireDepartment
        ServiceType.WAKAPI -> Icons.Default.CheckCircle
        ServiceType.PROXMOX -> Icons.Default.Dns
        ServiceType.UNKNOWN -> Icons.Default.Widgets
    }
