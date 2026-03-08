package com.homelab.app.ui.bookmarks

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import coil3.compose.SubcomposeAsyncImage

internal val selfhstServices: List<String> = listOf(
    "adguard", "adguard-home", "airsonic", "audiobookshelf", "authentik", "beszel",
    "calibre-web", "changedetection", "code-server", "dashy", "ddns-updater",
    "deluge", "dozzle", "duplicati", "filebrowser", "freshrss", "glances",
    "gitea", "grafana", "guacamole", "heimdall", "homeassistant", "immich",
    "jellyfin", "jellyseerr", "kavita", "lidarr", "linkding", "mealie",
    "navidrome", "nextcloud", "ntfy", "paperless-ngx", "pihole", "portainer",
    "prowlarr", "qbittorrent", "radarr", "readarr", "scrutiny", "searxng",
    "sonarr", "speedtest-tracker", "syncthing", "tautulli", "traefik",
    "uptime-kuma", "vaultwarden", "vikunja", "watchtower", "wireguard",
    "wordpress"
)

internal fun normalizeWebUrl(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return trimmed
    return if (trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true)) {
        trimmed
    } else {
        "https://$trimmed"
    }
}

internal fun extractHost(rawUrl: String): String? {
    val normalized = normalizeWebUrl(rawUrl)
    return try {
        Uri.parse(normalized).host?.takeIf { it.isNotBlank() }
    } catch (_: Exception) {
        null
    }
}

internal fun buildFaviconCandidates(rawUrl: String): List<String> {
    val normalized = normalizeWebUrl(rawUrl)
    val host = extractHost(normalized) ?: return emptyList()
    val encodedUrl = Uri.encode(normalized)
    val scheme = try { Uri.parse(normalized).scheme ?: "https" } catch (_: Exception) { "https" }

    return listOf(
        "https://www.google.com/s2/favicons?sz=128&domain_url=$encodedUrl",
        "https://icons.duckduckgo.com/ip3/$host.ico",
        "$scheme://$host/favicon.ico",
        "$scheme://$host/apple-touch-icon.png"
    )
}

internal fun selfhstIconUrl(serviceName: String): String? {
    val service = extractSelfhstService(serviceName)
    if (service.isBlank()) return null
    return "https://raw.githubusercontent.com/selfhst/icons/main/png/$service.png"
}

internal fun normalizeRemoteImageUrl(raw: String): String? {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return null

    return try {
        val parsed = Uri.parse(trimmed)
        val imgUrl = parsed.getQueryParameter("imgurl")
        if (!imgUrl.isNullOrBlank()) {
            val decoded = Uri.decode(imgUrl)
            val decodedUri = Uri.parse(decoded)
            if (decodedUri.scheme == "http" || decodedUri.scheme == "https") return decoded
        }

        when (parsed.scheme) {
            "http", "https" -> trimmed
            null -> {
                val withScheme = "https://$trimmed"
                val withSchemeUri = Uri.parse(withScheme)
                if (!withSchemeUri.host.isNullOrBlank()) withScheme else null
            }
            else -> null
        }
    } catch (_: Exception) {
        null
    }
}

internal fun extractSelfhstService(raw: String): String {
    val trimmed = raw.trim().lowercase()
    if (trimmed.isBlank()) return ""

    val candidate = try {
        val parsed = Uri.parse(trimmed)
        if ((parsed.host ?: "").contains("selfh.st")) {
            parsed.lastPathSegment ?: ""
        } else {
            trimmed
        }
    } catch (_: Exception) {
        trimmed
    }

    val base = candidate.substringAfterLast("/")

    return base
        .removeSuffix(".png")
        .removeSuffix(".svg")
        .removeSuffix(".webp")
        .removeSuffix(".ico")
        .trim()
}

@Composable
internal fun FallbackRemoteIcon(
    urls: List<String>,
    modifier: Modifier = Modifier,
    loading: @Composable () -> Unit,
    fallback: @Composable () -> Unit
) {
    var index by remember(urls) { mutableIntStateOf(0) }
    val current = urls.getOrNull(index)

    if (current == null) {
        fallback()
        return
    }

    SubcomposeAsyncImage(
        model = current,
        contentDescription = null,
        modifier = modifier,
        loading = { loading() },
        error = {
            if (index < urls.lastIndex) {
                index += 1
                loading()
            } else {
                fallback()
            }
        }
    )
}
