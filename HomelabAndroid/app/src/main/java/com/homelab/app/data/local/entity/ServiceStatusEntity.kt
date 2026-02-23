package com.homelab.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_status")
data class ServiceStatusEntity(
    @PrimaryKey
    val serviceId: String, // "portainer", "pihole", "beszel" ecc..
    val isRunning: Boolean,
    val lastUpdated: Long,
    val rawJsonData: String // Fallback storage rapido prima della serializzazione avanzata sui service view
)
