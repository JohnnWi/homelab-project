package com.homelab.app.data.model

import java.util.UUID
import kotlinx.serialization.Serializable

@Serializable
enum class IconType {
    FAVICON, SYSTEM_SYMBOL, SELFHST
}

@Serializable
data class Bookmark(
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val title: String,
    val description: String = "",
    val url: String,
    val iconType: IconType = IconType.FAVICON,
    val iconValue: String = "",
    val tags: List<String> = emptyList(),
    val sortOrder: Int = 0
)
