package com.homelab.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.homelab.app.util.ServiceType

@Composable
fun isThemeDark(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

val ServiceType.primaryColor: Color
    @Composable
    get() = if (isThemeDark()) {
        when (this) {
            ServiceType.PORTAINER -> Color(0xFF13B9DA)
            ServiceType.PIHOLE -> Color(0xFFE57373)
            ServiceType.BESZEL -> Color(0xFFCBD5E1) // Slate 300 - Brighter for better contrast
            ServiceType.GITEA -> Color(0xFF34D399)
            ServiceType.UNKNOWN -> Color.LightGray
        }
    } else {
        when (this) {
            ServiceType.PORTAINER -> Color(0xFF13B9DA)
            ServiceType.PIHOLE -> Color(0xFFF44336)
            ServiceType.BESZEL -> Color(0xFF0F172A)
            ServiceType.GITEA -> Color(0xFF34D399)
            ServiceType.UNKNOWN -> Color.Gray
        }
    }

val ServiceType.backgroundColor: Color
    @Composable
    get() = if (isThemeDark()) {
        when (this) {
            ServiceType.PORTAINER -> Color(0xFF0D323A)
            ServiceType.PIHOLE -> Color(0xFF3B1E1E)
            ServiceType.BESZEL -> Color(0xFF1E293B)
            ServiceType.GITEA -> Color(0xFF1B3D2F)
            ServiceType.UNKNOWN -> Color.DarkGray
        }
    } else {
        when (this) {
            ServiceType.PORTAINER -> Color(0xFFE8F8FB)
            ServiceType.PIHOLE -> Color(0xFFFDECED)
            ServiceType.BESZEL -> Color(0xFFF1F5F9)
            ServiceType.GITEA -> Color(0xFFF0FDF4)
            ServiceType.UNKNOWN -> Color.LightGray
        }
    }
