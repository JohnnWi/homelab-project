package com.homelab.app.util

import androidx.annotation.Keep
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.Serializable

@Keep
@Serializable
enum class ServiceType(val displayName: String) {
    PORTAINER("Portainer"),
    PIHOLE("Pi-hole"),
    ADGUARD_HOME("AdGuard Home"),
    BESZEL("Beszel"),
    GITEA("Gitea"),
    NGINX_PROXY_MANAGER("Nginx Proxy Manager"),
    UNKNOWN("Unknown")
}
