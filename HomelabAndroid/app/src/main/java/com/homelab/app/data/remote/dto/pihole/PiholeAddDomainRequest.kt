package com.homelab.app.data.remote.dto.pihole

import kotlinx.serialization.Serializable

@Serializable
data class PiholeAddDomainRequest(
    val domain: String
)
