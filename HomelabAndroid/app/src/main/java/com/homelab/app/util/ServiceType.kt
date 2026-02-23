package com.homelab.app.util

import kotlinx.serialization.Serializable

@Serializable
enum class ServiceType {
    PORTAINER,
    PIHOLE,
    BESZEL,
    GITEA,
    UNKNOWN
}
