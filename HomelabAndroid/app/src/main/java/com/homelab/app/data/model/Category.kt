package com.homelab.app.data.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
data class Category(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val icon: String = "",
    val color: String? = null,
    val sortOrder: Int = 0
)
