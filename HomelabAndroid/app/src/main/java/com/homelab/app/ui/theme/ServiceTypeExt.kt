package com.homelab.app.ui.theme

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.luminance
import com.homelab.app.util.ServiceType

@Composable
fun isThemeDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

val ServiceType.primaryColor: Color
    @Composable
    get() = when (this) {
        ServiceType.PORTAINER -> Color(0xFF13B5EA)
        ServiceType.PIHOLE -> Color(0xFFCD2326)
        ServiceType.ADGUARD_HOME -> Color(0xFF34C759)
        ServiceType.BESZEL -> Color(0xFF8B5CF6)
        ServiceType.GITEA -> Color(0xFF609926)
        ServiceType.NGINX_PROXY_MANAGER -> Color(0xFFF15B2A)
        ServiceType.UNKNOWN -> if (isThemeDark()) Color.LightGray else Color.Gray
    }

val ServiceType.backgroundColor: Color
    @Composable
    get() = when (this) {
        ServiceType.PORTAINER -> Color(0xFF13B5EA).copy(alpha = 0.12f)
        ServiceType.PIHOLE -> Color(0xFFCD2326).copy(alpha = 0.12f)
        ServiceType.ADGUARD_HOME -> Color(0xFF34C759).copy(alpha = 0.12f)
        ServiceType.BESZEL -> Color(0xFF8B5CF6).copy(alpha = 0.12f)
        ServiceType.GITEA -> Color(0xFF609926).copy(alpha = 0.12f)
        ServiceType.NGINX_PROXY_MANAGER -> Color(0xFFF15B2A).copy(alpha = 0.12f)
        ServiceType.UNKNOWN -> if (isThemeDark()) Color(0xFF334155) else Color(0xFFF1F5F9)
    }

val ServiceType.iconUrl: String
    get() = when (this) {
        ServiceType.PORTAINER -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/portainer.png"
        ServiceType.PIHOLE -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/pi-hole.png"
        ServiceType.ADGUARD_HOME -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/adguard-home.png"
        ServiceType.BESZEL -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/beszel.png"
        ServiceType.GITEA -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/gitea.png"
        ServiceType.NGINX_PROXY_MANAGER -> "https://cdn.jsdelivr.net/gh/selfhst/icons/png/nginx-proxy-manager.png"
        ServiceType.UNKNOWN -> ""
    }

val ServiceType.fallbackIcon: ImageVector
    get() = when (this) {
        ServiceType.PORTAINER -> Icons.Default.Widgets
        ServiceType.PIHOLE -> Icons.Default.Security
        ServiceType.ADGUARD_HOME -> Icons.Default.Security
        ServiceType.BESZEL -> Icons.Default.Storage
        ServiceType.GITEA -> Icons.Default.Source
        ServiceType.NGINX_PROXY_MANAGER -> Icons.Default.Widgets
        ServiceType.UNKNOWN -> Icons.Default.Widgets
    }
