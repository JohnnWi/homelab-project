package com.homelab.app.data.remote.dto.wakapi

import androidx.annotation.Keep
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Keep
@Serializable
data class WakapiSummaryResponse(
    @SerialName("user_id") val userId: String? = null,
    val from: String? = null,
    val to: String? = null,
    @SerialName("grand_total") val grandTotal: WakapiGrandTotal? = null,
    val projects: List<WakapiStatItem>? = null,
    val languages: List<WakapiStatItem>? = null,
    val machines: List<WakapiStatItem>? = null,
    @SerialName("operating_systems") val operatingSystems: List<WakapiStatItem>? = null,
    val editors: List<WakapiStatItem>? = null,
    val labels: List<WakapiStatItem>? = null,
    val categories: List<WakapiStatItem>? = null,
    val branches: List<WakapiStatItem>? = null
) {
    fun effectiveGrandTotal(): WakapiGrandTotal =
        grandTotal ?: WakapiGrandTotal.fromTotalSeconds(inferredTotalSeconds())

    fun inferredTotalSeconds(): Double =
        listOfNotNull(
            projects,
            languages,
            machines,
            operatingSystems,
            editors,
            labels,
            categories,
            branches
        )
            .map { items -> items.sumOf { it.resolvedTotalSeconds } }
            .maxOrNull() ?: 0.0
}

@Keep
@Serializable
data class WakapiGrandTotal(
    val digital: String? = null,
    val hours: Int? = null,
    val minutes: Int? = null,
    val text: String? = null,
    @SerialName("total_seconds") val totalSeconds: Double? = null
) {
    val resolvedHours: Int
        get() = hours ?: floorSeconds(totalSeconds) / 3600

    val resolvedMinutes: Int
        get() = minutes ?: (floorSeconds(totalSeconds) % 3600) / 60

    val resolvedText: String
        get() = text?.takeIf { it.isNotBlank() } ?: buildDurationLabel(resolvedHours, resolvedMinutes)

    companion object {
        fun fromTotalSeconds(totalSeconds: Double): WakapiGrandTotal {
            val seconds = floorSeconds(totalSeconds)
            return WakapiGrandTotal(
                digital = String.format("%d:%02d", seconds / 3600, (seconds % 3600) / 60),
                hours = seconds / 3600,
                minutes = (seconds % 3600) / 60,
                totalSeconds = totalSeconds
            )
        }
    }
}

@Keep
@Serializable
data class WakapiStatItem(
    val name: String? = null,
    val key: String? = null,
    @SerialName("total_seconds") val totalSeconds: Double? = null,
    val total: Long? = null,
    val percent: Double? = null,
    val digital: String? = null,
    val text: String? = null,
    val hours: Int? = null,
    val minutes: Int? = null
) {
    val displayName: String?
        get() = name ?: key

    val resolvedTotalSeconds: Double
        get() = totalSeconds ?: total?.toDouble() ?: 0.0

    val resolvedHours: Int
        get() = hours ?: floorSeconds(resolvedTotalSeconds) / 3600

    val resolvedMinutes: Int
        get() = minutes ?: (floorSeconds(resolvedTotalSeconds) % 3600) / 60

    val resolvedText: String
        get() = text?.takeIf { it.isNotBlank() } ?: buildDurationLabel(resolvedHours, resolvedMinutes)

    fun resolvedPercent(sectionTotalSeconds: Double): Double =
        percent ?: if (sectionTotalSeconds > 0) {
            (resolvedTotalSeconds / sectionTotalSeconds) * 100
        } else {
            0.0
        }
}

private fun floorSeconds(value: Double?): Int = kotlin.math.floor(value ?: 0.0).toInt().coerceAtLeast(0)

private fun buildDurationLabel(hours: Int, minutes: Int): String =
    if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"

@Keep
@Serializable
data class WakapiDailySummariesResponse(
    val data: List<WakapiDailySummary> = emptyList(),
    val end: String? = null,
    val start: String? = null,
    @SerialName("cumulative_total") val cumulativeTotal: WakapiCumulativeTotal? = null,
    @SerialName("daily_average") val dailyAverage: WakapiDailyAverage? = null
)

@Keep
@Serializable
data class WakapiDailySummary(
    val categories: List<WakapiStatItem>? = null,
    val dependencies: List<WakapiStatItem>? = null,
    val editors: List<WakapiStatItem>? = null,
    val languages: List<WakapiStatItem>? = null,
    val machines: List<WakapiStatItem>? = null,
    @SerialName("operating_systems") val operatingSystems: List<WakapiStatItem>? = null,
    val projects: List<WakapiStatItem>? = null,
    val branches: List<WakapiStatItem>? = null,
    val entities: List<WakapiStatItem>? = null,
    @SerialName("grand_total") val grandTotal: WakapiGrandTotal? = null,
    val range: WakapiDailyRange? = null
) {
    val totalSeconds: Double
        get() = grandTotal?.totalSeconds
            ?: ((grandTotal?.hours ?: 0) * 3600 + (grandTotal?.minutes ?: 0) * 60).toDouble()
}

@Keep
@Serializable
data class WakapiDailyRange(
    val date: String? = null,
    val end: String? = null,
    val start: String? = null,
    val text: String? = null,
    val timezone: String? = null
)

@Keep
@Serializable
data class WakapiCumulativeTotal(
    val decimal: String? = null,
    val digital: String? = null,
    val seconds: Double? = null,
    val text: String? = null
)

@Keep
@Serializable
data class WakapiDailyAverage(
    @SerialName("days_including_holidays") val daysIncludingHolidays: Int? = null,
    @SerialName("days_minus_holidays") val daysMinusHolidays: Int? = null,
    val holidays: Int? = null,
    val seconds: Int? = null,
    @SerialName("seconds_including_other_language") val secondsIncludingOtherLanguage: Int? = null,
    val text: String? = null,
    @SerialName("text_including_other_language") val textIncludingOtherLanguage: String? = null
)
