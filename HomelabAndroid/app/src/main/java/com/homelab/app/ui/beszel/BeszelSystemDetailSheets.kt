package com.homelab.app.ui.beszel

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
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
import com.homelab.app.data.remote.dto.beszel.BeszelNetworkInterface
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
            ExtraMetricType.SWAP -> stringResource(R.string.beszel_swap_usage)
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
                val load5 = history.mapNotNull { it.loadAvgValues.getOrNull(1) }
                secondaryData = if (load5.size == data.size) load5 else null
            }
            ExtraMetricType.NETWORK -> {
                val rxSeries = history.mapNotNull { it.bandwidthDownBytesPerSec }
                val txSeries = history.mapNotNull { it.bandwidthUpBytesPerSec }
                data = rxSeries
                secondaryData = if (rxSeries.size == txSeries.size) txSeries else null
            }
            ExtraMetricType.DISK -> {
                val readSeries = history.mapNotNull { it.diskReadBytesPerSec }
                val writeSeries = history.mapNotNull { it.diskWriteBytesPerSec }
                data = readSeries
                secondaryData = if (readSeries.size == writeSeries.size) writeSeries else null
            }
            ExtraMetricType.BATTERY -> {
                data = history.mapNotNull { it.batteryLevel?.toDouble() }
                secondaryData = null
            }
            ExtraMetricType.SWAP -> {
                data = history.mapNotNull { it.swapUsedGb }
                secondaryData = null
            }
        }

        val accent = when (metric) {
            ExtraMetricType.TEMPERATURE -> StatusOrange
            ExtraMetricType.LOAD -> ServiceType.BESZEL.primaryColor
            ExtraMetricType.NETWORK -> StatusPurple
            ExtraMetricType.DISK -> StatusOrange
            ExtraMetricType.BATTERY -> StatusGreen
            ExtraMetricType.SWAP -> StatusOrange
        }

        val unitFormatter: (Double) -> String = when (metric) {
            ExtraMetricType.TEMPERATURE -> { v -> String.format("%.1f°C", v) }
            ExtraMetricType.LOAD -> { v -> String.format("%.2f", v) }
            ExtraMetricType.NETWORK -> { v -> formatNetRateBytesPerSec(v) }
            ExtraMetricType.DISK -> { v -> formatNetRateBytesPerSec(v) }
            ExtraMetricType.BATTERY -> { v -> String.format("%.0f%%", v) }
            ExtraMetricType.SWAP -> { v -> formatGB(v) }
        }
        val secondaryColor = when (metric) {
            ExtraMetricType.NETWORK -> StatusOrange
            ExtraMetricType.DISK -> StatusPurple
            ExtraMetricType.LOAD -> StatusOrange
            else -> StatusOrange
        }

        val valueLabel: String = when (metric) {
            ExtraMetricType.TEMPERATURE -> stringResource(R.string.beszel_temps)
            ExtraMetricType.LOAD -> stringResource(R.string.beszel_load_avg)
            ExtraMetricType.NETWORK -> stringResource(R.string.beszel_network_io)
            ExtraMetricType.DISK -> stringResource(R.string.beszel_disk_io)
            ExtraMetricType.BATTERY -> stringResource(R.string.beszel_battery)
            ExtraMetricType.SWAP -> stringResource(R.string.beszel_swap)
        }

        val min = data.minOrNull()
        val avg = if (data.isNotEmpty()) data.sum() / data.size else null
        val selectedIndex = remember { mutableStateOf<Int?>(null) }
        val latest = history.lastOrNull()
        val primaryLegend = when (metric) {
            ExtraMetricType.NETWORK -> stringResource(R.string.beszel_download)
            ExtraMetricType.DISK -> stringResource(R.string.beszel_read)
            ExtraMetricType.LOAD -> "1 min"
            else -> null
        }
        val secondaryLegend = when (metric) {
            ExtraMetricType.NETWORK -> stringResource(R.string.beszel_upload)
            ExtraMetricType.DISK -> stringResource(R.string.beszel_write)
            ExtraMetricType.LOAD -> "5 min"
            else -> null
        }

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

                if (secondaryData != null && primaryLegend != null && secondaryLegend != null) {
                    primaryFormatterForGraph = { v -> "$primaryLegend: ${unitFormatter(v)}" }
                    secondaryFormatterForGraph = { v -> "$secondaryLegend: ${unitFormatter(v)}" }
                } else {
                    primaryFormatterForGraph = { v -> "$valueLabel: ${unitFormatter(v)}" }
                    secondaryFormatterForGraph = null
                }

                SmoothLineGraph(
                    data = data,
                    graphColor = accent,
                    secondaryData = secondaryData,
                    secondaryColor = secondaryColor,
                    enableScrub = true,
                    selectedIndex = selectedIndex.value,
                    onSelectedIndexChange = { selectedIndex.value = it },
                    labelFormatter = primaryFormatterForGraph,
                    secondaryLabelFormatter = secondaryFormatterForGraph
                )

                if (secondaryData != null && primaryLegend != null && secondaryLegend != null) {
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
                                text = primaryLegend,
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
                                    .background(secondaryColor, CircleShape)
                            )
                            Text(
                                text = secondaryLegend,
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

                when (metric) {
                    ExtraMetricType.TEMPERATURE -> {
                        val sensors = latest?.temperatureSensors.orEmpty().entries.sortedByDescending { it.value }
                        if (sensors.isNotEmpty()) {
                            DetailSectionTitle(stringResource(R.string.beszel_temp_sensors))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                sensors.forEach { (name, value) ->
                                    DetailStatRow(name, String.format("%.1f°C", value))
                                }
                            }
                        }
                    }

                    ExtraMetricType.LOAD -> {
                        val loadValues = latest?.loadAvgValues.orEmpty()
                        if (loadValues.isNotEmpty()) {
                            DetailSectionTitle(stringResource(R.string.beszel_load_avg))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                loadValues.getOrNull(0)?.let { DetailStatRow("1 min", String.format("%.2f", it)) }
                                loadValues.getOrNull(1)?.let { DetailStatRow("5 min", String.format("%.2f", it)) }
                                loadValues.getOrNull(2)?.let { DetailStatRow("15 min", String.format("%.2f", it)) }
                            }
                        }
                    }

                    ExtraMetricType.NETWORK -> {
                        val interfaces = latest?.networkInterfaces.orEmpty().toList().sortedBy { it.first }
                        if (interfaces.isNotEmpty()) {
                            DetailSectionTitle(stringResource(R.string.beszel_network_interfaces))
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                interfaces.forEach { (name, snapshot) ->
                                    NetworkInterfaceRow(name = name, snapshot = snapshot)
                                }
                            }
                        }
                    }

                    else -> Unit
                }
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
internal fun CpuDetailsSheet(
    history: List<BeszelRecordStats>,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val series = history.mapNotNull { it.cpu }
    val latest = history.lastOrNull()
    val selectedIndex = remember { mutableStateOf<Int?>(null) }
    val min = series.minOrNull()
    val avg = if (series.isNotEmpty()) series.sum() / series.size else null
    val breakdownLabels = listOf(
        stringResource(R.string.beszel_cpu_user),
        stringResource(R.string.beszel_cpu_system),
        stringResource(R.string.beszel_cpu_nice),
        stringResource(R.string.beszel_cpu_wait),
        stringResource(R.string.beszel_cpu_idle)
    )

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.beszel_cpu),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            if (series.size >= 2) {
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
                    data = series,
                    graphColor = ServiceType.BESZEL.primaryColor,
                    enableScrub = true,
                    selectedIndex = selectedIndex.value,
                    onSelectedIndexChange = { selectedIndex.value = it },
                    labelFormatter = { value -> "CPU: ${String.format("%.1f%%", value)}" }
                )

                Text(
                    text = stringResource(R.string.beszel_time_axis_hint),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val breakdown = latest?.cpuBreakdownValues.orEmpty()
            if (breakdown.isNotEmpty()) {
                DetailSectionTitle(stringResource(R.string.beszel_cpu_breakdown))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    breakdown.forEachIndexed { index, value ->
                        MetricProgressRow(
                            label = breakdownLabels.getOrElse(index) { "${stringResource(R.string.beszel_cpu)} ${index + 1}" },
                            value = value,
                            accent = if (index == breakdown.lastIndex) StatusPurple else ServiceType.BESZEL.primaryColor
                        )
                    }
                }
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
    val readData = if (drive.label == "root") {
        history.mapNotNull { stats -> stats.diskReadBytesPerSec }
    } else {
        history.mapNotNull { stats ->
            stats.efs?.get(drive.label)?.readBytesPerSec
        }
    }
    val writeData = if (drive.label == "root") {
        history.mapNotNull { stats -> stats.diskWriteBytesPerSec }
    } else {
        history.mapNotNull { stats ->
            stats.efs?.get(drive.label)?.writeBytesPerSec
        }
    }

    val min = data.minOrNull()
    val avg = if (data.isNotEmpty()) data.sum() / data.size else null
    val readLabel = stringResource(R.string.beszel_read)
    val writeLabel = stringResource(R.string.beszel_write)

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

                if (readData.isNotEmpty() && readData.size == writeData.size) {
                    Text(
                        text = stringResource(R.string.beszel_disk_io),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    SmoothLineGraph(
                        data = readData,
                        graphColor = StatusOrange,
                        secondaryData = writeData,
                        secondaryColor = StatusPurple,
                        enableScrub = true,
                        selectedIndex = selectedIndex.value,
                        onSelectedIndexChange = { selectedIndex.value = it },
                        labelFormatter = { v -> "$readLabel: ${formatNetRateBytesPerSec(v)}" },
                        secondaryLabelFormatter = { v -> "$writeLabel: ${formatNetRateBytesPerSec(v)}" }
                    )
                }
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
            GpuMetricType.VRAM -> {
                title = stringResource(R.string.beszel_gpu_vram_label_full)
                series = history.mapNotNull { it.gpuVramPercent }.takeLast(240)
                accent = StatusOrange
                formatter = { v: Double -> String.format("%.1f%%", v) }
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

@Composable
private fun DetailSectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun DetailStatRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
private fun MetricProgressRow(label: String, value: Double, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = String.format("%.1f%%", value),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(3.dp))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((value / 100.0).coerceIn(0.0, 1.0).toFloat())
                    .height(6.dp)
                    .background(accent, RoundedCornerShape(3.dp))
            )
        }
    }
}

@Composable
private fun NetworkInterfaceRow(name: String, snapshot: BeszelNetworkInterface) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold
            )
            DetailStatRow(
                label = stringResource(R.string.beszel_download),
                value = formatNetRateBytesPerSec(snapshot.downloadRateBytesPerSec)
            )
            DetailStatRow(
                label = stringResource(R.string.beszel_upload),
                value = formatNetRateBytesPerSec(snapshot.uploadRateBytesPerSec)
            )
            snapshot.downloadTotalBytes?.let {
                DetailStatRow(
                    label = stringResource(R.string.beszel_total_download),
                    value = formatBytes(it)
                )
            }
            snapshot.uploadTotalBytes?.let {
                DetailStatRow(
                    label = stringResource(R.string.beszel_total_upload),
                    value = formatBytes(it)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DualMetricDetailsSheet(
    title: String,
    data: List<Double>,
    secondaryData: List<Double>,
    accent: Color,
    secondaryColor: Color,
    unitFormatter: (Double) -> String,
    primaryLegend: String,
    secondaryLegend: String,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val selectedIndex = remember { mutableStateOf<Int?>(null) }
    val min = data.minOrNull()
    val avg = if (data.isNotEmpty()) data.sum() / data.size else null

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)

            if (data.size >= 2 && data.size == secondaryData.size) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
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

                SmoothLineGraph(
                    data = data,
                    graphColor = accent,
                    secondaryData = secondaryData,
                    secondaryColor = secondaryColor,
                    enableScrub = true,
                    selectedIndex = selectedIndex.value,
                    onSelectedIndexChange = { selectedIndex.value = it },
                    labelFormatter = { v -> "$primaryLegend: ${unitFormatter(v)}" },
                    secondaryLabelFormatter = { v -> "$secondaryLegend: ${unitFormatter(v)}" }
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(accent, CircleShape))
                        Text(
                            text = primaryLegend,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(secondaryColor, CircleShape))
                        Text(
                            text = secondaryLegend,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
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

