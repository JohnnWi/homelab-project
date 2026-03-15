package com.homelab.app.ui.beszel

import kotlin.math.pow

internal fun formatMB(valMB: Double): String {
    if (valMB == 0.0) return "0 MB"
    if (valMB > 10_000_000) return formatBytes(valMB)
    val gb = valMB / 1024.0
    if (gb >= 1.0) return String.format("%.2f GB", gb)
    return String.format("%.0f MB", valMB)
}

internal fun formatGB(valGB: Double): String {
    if (valGB == 0.0) return "0 GB"
    if (valGB > 10_000_000) return formatBytes(valGB)
    if (valGB >= 1000.0) return String.format("%.2f TB", valGB / 1000.0)
    if (valGB >= 1.0) return String.format("%.1f GB", valGB)
    return String.format("%.0f MB", valGB * 1024)
}

internal fun formatBytes(bytes: Double): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (kotlin.math.log10(bytes) / kotlin.math.log10(1024.0)).toInt().coerceIn(0, units.lastIndex)
    val scaled = bytes / 1024.0.pow(digitGroups.toDouble())
    return String.format("%.1f %s", scaled, units[digitGroups])
}

internal fun formatNetRateBytesPerSec(valBytesPerSec: Double): String {
    if (valBytesPerSec == 0.0) return "0 B/s"
    return "${formatBytes(valBytesPerSec)}/s"
}

internal fun formatUptimeShort(seconds: Double): String {
    val d = (seconds / 86400).toInt()
    val h = (seconds % 86400 / 3600).toInt()
    if (d > 0) return "${d}d ${h}h"
    val m = (seconds % 3600 / 60).toInt()
    if (h > 0) return "${h}h ${m}m"
    return "${m}m"
}

