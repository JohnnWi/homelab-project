package com.homelab.app.ui.beszel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.homelab.app.R
import com.homelab.app.data.remote.dto.beszel.BeszelRecordStats
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusPurple
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ExtraMetricDetailsSheet(
    metric: ExtraMetricType,
    history: List<BeszelRecordStats>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val title = when (metric) {
            ExtraMetricType.TEMPERATURE -> stringResource(R.string.beszel_temps)
            ExtraMetricType.LOAD -> stringResource(R.string.beszel_load_avg)
            ExtraMetricType.NETWORK -> stringResource(R.string.beszel_network_io)
            ExtraMetricType.DISK -> stringResource(R.string.beszel_disk_io)
            ExtraMetricType.BATTERY -> stringResource(R.string.beszel_battery)
        }

        val data: List<Double>
        val secondaryData: List<Double>?

        when (metric) {
            ExtraMetricType.TEMPERATURE -> {
                data = history.mapNotNull { it.maxTempCelsius }
                secondaryData = null
            }
            ExtraMetricType.LOAD -> {
                data = history.mapNotNull { it.loadAvgValues.firstOrNull() }
                secondaryData = null
            }
            ExtraMetricType.NETWORK -> {
                // ns/nr are Beszel's precomputed network send/receive rates (MB/s).
                // Convert to bytes/s to reuse the generic formatter.
                val rxSeries = history.mapNotNull { it.nr }.map { it * 1024 * 1024 }
                val txSeries = history.mapNotNull { it.ns }.map { it * 1024 * 1024 }
                data = rxSeries
                secondaryData = if (rxSeries.size == txSeries.size) txSeries else null
            }
            ExtraMetricType.DISK -> {
                data = history.mapNotNull { it.diskReadIO }
                secondaryData = null
            }
            ExtraMetricType.BATTERY -> {
                data = history.mapNotNull { it.batteryLevel?.toDouble() }
                secondaryData = null
            }
        }

        val accent = when (metric) {
            ExtraMetricType.TEMPERATURE -> StatusOrange
            ExtraMetricType.LOAD -> ServiceType.BESZEL.primaryColor
            ExtraMetricType.NETWORK -> StatusPurple
            ExtraMetricType.DISK -> StatusOrange
            ExtraMetricType.BATTERY -> StatusGreen
        }

        val unitFormatter: (Double) -> String = when (metric) {
            ExtraMetricType.TEMPERATURE -> { v -> String.format("%.1f°C", v) }
            ExtraMetricType.LOAD -> { v -> String.format("%.2f", v) }
            ExtraMetricType.NETWORK -> { v -> formatNetRateBytesPerSec(v) }
            ExtraMetricType.DISK -> { v -> String.format("%.0f ops", v) }
            ExtraMetricType.BATTERY -> { v -> String.format("%.0f%%", v) }
        }

        val valueLabel: String = when (metric) {
            ExtraMetricType.TEMPERATURE -> stringResource(R.string.beszel_temps)
            ExtraMetricType.LOAD -> stringResource(R.string.beszel_load_avg)
            ExtraMetricType.NETWORK -> stringResource(R.string.beszel_network_io)
            ExtraMetricType.DISK -> stringResource(R.string.beszel_disk_io)
            ExtraMetricType.BATTERY -> stringResource(R.string.beszel_battery)
        }

        val min = data.minOrNull()
        val avg = if (data.isNotEmpty()) data.sum() / data.size else null
        val selectedIndex = remember { mutableStateOf<Int?>(null) }

        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            if (data.size >= 2) {
                Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftParts = buildList {
                        min?.let { add("Min: ${unitFormatter(it)}") }
                        avg?.let { add("Avg: ${unitFormatter(it)}") }
                    }
                    if (leftParts.isNotEmpty()) {
                        Text(
                            text = leftParts.joinToString("   "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val primaryFormatterForGraph: (Double) -> String
                val secondaryFormatterForGraph: ((Double) -> String)?

                if (metric == ExtraMetricType.NETWORK && secondaryData != null) {
                    primaryFormatterForGraph = { v -> "RX: ${unitFormatter(v)}" }
                    secondaryFormatterForGraph = { v -> "TX: ${unitFormatter(v)}" }
                } else {
                    primaryFormatterForGraph = { v -> "$valueLabel: ${unitFormatter(v)}" }
                    secondaryFormatterForGraph = null
                }

                SmoothLineGraph(
                    data = data,
                    graphColor = accent,
                    secondaryData = secondaryData,
                    secondaryColor = StatusOrange,
                    enableScrub = true,
                    selectedIndex = selectedIndex.value,
                    onSelectedIndexChange = { selectedIndex.value = it },
                    labelFormatter = primaryFormatterForGraph,
                    secondaryLabelFormatter = secondaryFormatterForGraph
                )

                if (metric == ExtraMetricType.NETWORK && secondaryData != null) {
                    Row(
                        modifier = androidx.compose.ui.Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(accent, CircleShape)
                            )
                            Text(
                                text = "Download",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(StatusOrange, CircleShape)
                            )
                            Text(
                                text = "Upload",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Text(
                    text = stringResource(R.string.beszel_time_axis_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.beszel_background_update_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DiskFsDetailsSheet(
    drive: DiskFsUsage,
    history: List<BeszelRecordStats>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedIndex = remember { mutableStateOf<Int?>(null) }

    // Build usage history for this filesystem (percentage used)
    val data = if (drive.label == "root") {
        history.mapNotNull { stats ->
            val total = stats.dValue
            val used = stats.duValue
            if (total <= 0.0) return@mapNotNull null
            (used / total * 100.0).coerceIn(0.0, 100.0)
        }
    } else {
        history.mapNotNull { stats ->
            val entry = stats.efs?.get(drive.label) ?: return@mapNotNull null
            val total = entry.d ?: return@mapNotNull null
            val used = entry.du ?: return@mapNotNull null
            if (total <= 0.0) return@mapNotNull null
            (used / total * 100.0).coerceIn(0.0, 100.0)
        }
    }

    val min = data.minOrNull()
    val avg = if (data.isNotEmpty()) data.sum() / data.size else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.beszel_disk) + " • ${drive.label}",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${formatGB(drive.usedGb)} / ${formatGB(drive.totalGb)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (data.size >= 2) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftParts = buildList {
                        min?.let { add("Min: ${String.format("%.1f%%", it)}") }
                        avg?.let { add("Avg: ${String.format("%.1f%%", it)}") }
                    }
                    if (leftParts.isNotEmpty()) {
                        Text(
                            text = leftParts.joinToString("   "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SmoothLineGraph(
                    data = data,
                    graphColor = StatusOrange,
                    enableScrub = true,
                    selectedIndex = selectedIndex.value,
                    onSelectedIndexChange = { selectedIndex.value = it },
                    labelFormatter = { v -> String.format("%s: %.1f%%", drive.label, v) }
                )

                Text(
                    text = stringResource(R.string.beszel_time_axis_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.beszel_background_update_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GpuDetailsSheet(
    metric: GpuMetricType,
    history: List<BeszelRecordStats>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        val latestGpu = history.lastOrNull()?.primaryGpu

        val title: String
        val series: List<Double>
        val accent: Color
        val formatter: (Double) -> String

        when (metric) {
            GpuMetricType.USAGE -> {
                title = stringResource(R.string.beszel_gpu_usage_label_full)
                series = history.mapNotNull { it.gpuUsagePercent }.takeLast(240)
                accent = ServiceType.BESZEL.primaryColor
                formatter = { v: Double -> String.format("%.0f%%", v) }
            }
            GpuMetricType.POWER -> {
                title = stringResource(R.string.beszel_gpu_power_label_full)
                series = history.mapNotNull { it.gpuPowerWatts }.takeLast(240)
                accent = StatusPurple
                formatter = { v: Double -> String.format("%.1f W", v) }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            latestGpu?.let { gpu ->
                Text(
                    text = gpu.n,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (series.size >= 2) {
                val min = series.minOrNull()
                val avg = if (series.isNotEmpty()) series.sum() / series.size else null
                val selectedIndex = remember { mutableStateOf<Int?>(null) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftParts = buildList {
                        min?.let { add("Min: ${formatter(it)}") }
                        avg?.let { add("Avg: ${formatter(it)}") }
                    }
                    if (leftParts.isNotEmpty()) {
                        Text(
                            text = leftParts.joinToString("   "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                SmoothLineGraph(
                    data = series,
                    graphColor = accent,
                    enableScrub = true,
                    selectedIndex = selectedIndex.value,
                    onSelectedIndexChange = { selectedIndex.value = it },
                    labelFormatter = formatter
                )

                Text(
                    text = stringResource(R.string.beszel_time_axis_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.beszel_background_update_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SmartDetailsSheet(
    device: BeszelSmartDevice,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.beszel_smart_title_device, device.device ?: device.model ?: ""),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                device.model?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    device.capacityBytes?.let {
                        Text(
                            text = formatBytes(it.toDouble()),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    device.type?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    device.temperatureCelsius?.let {
                        Text(
                            text = String.format("%.0f°C", it),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.beszel_smart_attributes_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )

            if (device.attributes.isEmpty()) {
                Text(
                    text = stringResource(R.string.beszel_background_update_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    device.attributes.forEach { attr ->
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = (attr.id?.let { "$it " } ?: "") + attr.name,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                val summaryText = if (attr.value == null && attr.worst == null && attr.threshold == null) {
                                    // NVMe-style attributes: show raw string or raw value
                                    attr.rawString ?: attr.rawValue?.toString().orEmpty()
                                } else {
                                    val valueText = attr.value?.let { "Value $it" } ?: ""
                                    val worstText = attr.worst?.let { "Worst $it" } ?: ""
                                    val thresholdText = attr.threshold?.let { "Th $it" } ?: ""
                                    listOf(valueText, worstText, thresholdText)
                                        .filter { it.isNotEmpty() }
                                        .joinToString(" • ")
                                }

                                if (summaryText.isNotEmpty()) {
                                    Text(
                                        text = summaryText,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            attr.rawString?.let {
                                // For SATA-style attributes this adds more context; for NVMe
                                // many entries won't have a rawString so this is a no-op.
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ResourceMetricDetailsSheet(
    title: String,
    data: List<Double>,
    accent: Color,
    unitFormatter: (Double) -> String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedIndex = remember { mutableStateOf<Int?>(null) }
    val min = data.minOrNull()
    val avg = if (data.isNotEmpty()) data.sum() / data.size else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = androidx.compose.ui.Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            if (data.size >= 2) {
                Row(
                    modifier = androidx.compose.ui.Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val leftParts = buildList {
                        min?.let { add("Min: ${unitFormatter(it)}") }
                        avg?.let { add("Avg: ${unitFormatter(it)}") }
                    }
                    if (leftParts.isNotEmpty()) {
                        Text(
                            text = leftParts.joinToString("   "),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val valueLabel = title

                SmoothLineGraph(
                    data = data,
                    graphColor = accent,
                    enableScrub = true,
                    selectedIndex = selectedIndex.value,
                    onSelectedIndexChange = { selectedIndex.value = it },
                    labelFormatter = { v -> "$valueLabel: ${unitFormatter(v)}" }
                )

                Text(
                    text = stringResource(R.string.beszel_time_axis_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = stringResource(R.string.beszel_background_update_info),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

