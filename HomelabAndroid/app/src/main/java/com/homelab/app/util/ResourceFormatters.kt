package com.homelab.app.util

import android.content.Context
import com.homelab.app.R

object ResourceFormatters {
    fun formatMB(valMB: Double, context: Context): String {
        if (valMB == 0.0) return "0 MB"
        val gb = valMB / 1024.0
        if (gb >= 1.0) return String.format("%.2f GB", gb)
        return String.format("%.0f MB", valMB)
    }

    fun formatGB(valGB: Double, context: Context): String {
        if (valGB == 0.0) return "0 GB"
        if (valGB >= 1000.0) return String.format("%.2f TB", valGB / 1000.0)
        if (valGB >= 1.0) return String.format("%.1f GB", valGB)
        return String.format("%.0f MB", valGB * 1024)
    }

    fun formatBytes(bytes: Double, context: Context): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes) / Math.log10(1024.0)).toInt()
        return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    fun formatNetRate(valBytesPerSec: Double, context: Context): String {
        if (valBytesPerSec == 0.0) return "0 B/s"
        return "${formatBytes(valBytesPerSec, context)}/s"
    }

    fun formatUptimeHours(seconds: Double, context: Context): String {
        val d = (seconds / 86400).toInt()
        val h = (seconds % 86400 / 3600).toInt()
        val dUnit = context.getString(R.string.unit_days)
        val hUnit = context.getString(R.string.unit_hours)
        val mUnit = context.getString(R.string.unit_minutes)
        
        if (d > 0) return "${d}$dUnit ${h}$hUnit"
        val m = (seconds % 3600 / 60).toInt()
        if (h > 0) return "${h}$hUnit ${m}$mUnit"
        return "${m}$mUnit"
    }

    fun formatUnixDate(unixTime: Long): String {
        val date = java.util.Date(unixTime * 1000)
        val formatter = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
        return formatter.format(date)
    }

    fun formatDate(dateString: String?): String {
        val safeDate = dateString ?: return ""
        try {
            val formatter = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", java.util.Locale.getDefault())
            val date = formatter.parse(safeDate.take(19)) ?: return safeDate
            val outFormatter = java.text.SimpleDateFormat("dd MMM yyyy", java.util.Locale.getDefault())
            return outFormatter.format(date)
        } catch (e: Exception) {
            return safeDate
        }
    }
}
