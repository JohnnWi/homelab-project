package com.homelab.app.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "service_instances")
data class ServiceInstanceEntity(
    @PrimaryKey val id: String,
    val type: String,
    val label: String,
    val url: String,
    val token: String,
    val proxmoxCsrfToken: String? = null,
    val proxmoxOtp: String? = null,
    val username: String?,
    val apiKey: String?,
    val piholePassword: String?,
    val piholeAuthMode: String?,
    val fallbackUrl: String?,
    @ColumnInfo(defaultValue = "0")
    val allowSelfSigned: Boolean,
    val password: String? = null
)
