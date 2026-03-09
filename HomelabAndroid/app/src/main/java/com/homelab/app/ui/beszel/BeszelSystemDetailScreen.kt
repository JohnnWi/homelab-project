package com.homelab.app.ui.beszel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.beszel.BeszelContainer
import com.homelab.app.data.remote.dto.beszel.BeszelRecordStats
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetails
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusPurple
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeszelSystemDetailScreen(
    systemId: String,
    onNavigateBack: () -> Unit,
    viewModel: BeszelViewModel = hiltViewModel()
) {
    val systemDetailState by viewModel.systemDetailState.collectAsStateWithLifecycle()
    val systemDetails by viewModel.systemDetails.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val smartDevices by viewModel.smartDevices.collectAsStateWithLifecycle()

    LaunchedEffect(systemId) {
        viewModel.fetchSystemDetail(systemId)
    }

    val systemName = when (val state = systemDetailState) {
        is UiState.Success -> state.data.name
        else -> stringResource(R.string.beszel_system_details)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(systemName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchSystemDetail(systemId) }) {
                        Icon(
                            Icons.Filled.Refresh,
                            contentDescription = stringResource(R.string.refresh)
                        )
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = systemDetailState) {
            is UiState.Idle,
            is UiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ServiceType.BESZEL.primaryColor)
                }
            }

            is UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { state.retryAction?.invoke() ?: viewModel.fetchSystemDetail(systemId) },
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is UiState.Offline -> {
                ErrorScreen(
                    message = "",
                    onRetry = { viewModel.fetchSystemDetail(systemId) },
                    isOffline = true,
                    modifier = Modifier.padding(paddingValues)
                )
            }

            is UiState.Success -> {
                BeszelSystemDetailContent(
                    system = state.data,
                    systemDetails = systemDetails,
                    records = records,
                    smartDevices = smartDevices,
                    paddingValues = paddingValues
                )
            }
        }
    }
}

@Composable
private fun BeszelSystemDetailContent(
    system: BeszelSystem,
    systemDetails: BeszelSystemDetails?,
    records: List<BeszelSystemRecord>,
    smartDevices: List<BeszelSmartDevice>,
    paddingValues: PaddingValues
) {
    val info = system.info
    val statsHistory = remember(records) { records.map(BeszelSystemRecord::stats) }
    val recentStats = remember(records) { statsHistory.takeLast(30) }
    val latestStats: BeszelRecordStats? = statsHistory.lastOrNull()

    val cpuHistory = remember(recentStats) { recentStats.map(BeszelRecordStats::cpuValue) }
    val memHistory = remember(recentStats) { recentStats.map(BeszelRecordStats::mpValue) }
    val memoryUsedHistory = remember(recentStats) { recentStats.mapNotNull(BeszelRecordStats::memoryUsedGb) }

    val diskUsed = (latestStats?.duValue ?: info?.duValue)?.takeIf { it > 0.0 }
    val diskTotal = (latestStats?.dValue ?: info?.dValue)?.takeIf { it > 0.0 }
    val memoryUsed = latestStats?.memoryUsedGb
    val memoryTotal = latestStats?.memoryTotalGb ?: info?.mValue?.takeIf { it > 0.0 }

    val externalFileSystems = remember(latestStats) {
        latestStats?.efs
            ?.mapNotNull { (key, entry) ->
                val total = entry.d ?: return@mapNotNull null
                val used = entry.du ?: return@mapNotNull null
                if (total <= 0.0 || used < 0.0) return@mapNotNull null
                DiskFsUsage(label = key, usedGb = used, totalGb = total)
            }
            .orEmpty()
    }

    val expandedMetric = remember { mutableStateOf<ExtraMetricType?>(null) }
    val expandedResourceMetric = remember { mutableStateOf<ResourceMetricType?>(null) }
    val expandedDockerMetric = remember { mutableStateOf<DockerMetricType?>(null) }
    val expandedDiskFs = remember { mutableStateOf<DiskFsUsage?>(null) }
    val expandedSmartDevice = remember { mutableStateOf<BeszelSmartDevice?>(null) }
    val gpuDetailsMetric = remember { mutableStateOf<GpuMetricType?>(null) }
    val latestDockerSummary = remember(latestStats) { latestStats?.dockerSummary }
    val dockerCpuHistory = remember(recentStats) { recentStats.containerSeries { it.cpuValue } }
    val dockerMemoryHistory = remember(recentStats) { recentStats.containerSeries { it.mValue } }
    val dockerNetworkUpHistory = remember(recentStats) { recentStats.containerSeries(BeszelContainer::bandwidthUpBytesPerSec) }
    val dockerNetworkDownHistory = remember(recentStats) { recentStats.containerSeries(BeszelContainer::bandwidthDownBytesPerSec) }
    val hasDockerNetwork = latestDockerSummary?.let { summary ->
        summary.bandwidthUpBytesPerSec != null &&
            summary.bandwidthDownBytesPerSec != null &&
            dockerNetworkUpHistory.isNotEmpty() &&
            dockerNetworkDownHistory.size == dockerNetworkUpHistory.size
    } == true

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BeszelHeaderCard(system)
        }

        if (info != null || systemDetails != null) {
            item {
                SystemInfoSection(info = info, details = systemDetails)
            }
        }

        if (info != null) {
            item {
                ResourcesSection(
                    cpu = info.cpuValue,
                    mp = info.mpValue,
                    dp = info.dpValue,
                    memoryUsedGb = memoryUsed,
                    memoryTotalGb = memoryTotal,
                    memoryUsedHistory = memoryUsedHistory,
                    diskUsed = diskUsed,
                    diskTotal = diskTotal,
                    externalFileSystems = externalFileSystems,
                    cpuHistory = cpuHistory,
                    memHistory = memHistory,
                    onCpuClick = { expandedResourceMetric.value = ResourceMetricType.CPU },
                    onMemClick = { expandedResourceMetric.value = ResourceMetricType.MEMORY },
                    onDiskFsClick = { fs -> expandedDiskFs.value = fs }
                )
            }

            if (smartDevices.isNotEmpty()) {
                item {
                    SmartDevicesSection(
                        devices = smartDevices,
                        onDeviceClick = { device -> expandedSmartDevice.value = device }
                    )
                }
            }

            val containers: List<BeszelContainer> = latestStats?.dc ?: emptyList()
            latestDockerSummary?.let { summary ->
                item {
                    DockerMetricsSection(
                        summary = summary,
                        hasNetwork = hasDockerNetwork,
                        onCpuClick = { expandedDockerMetric.value = DockerMetricType.CPU },
                        onMemoryClick = { expandedDockerMetric.value = DockerMetricType.MEMORY },
                        onNetworkClick = { expandedDockerMetric.value = DockerMetricType.NETWORK }
                    )
                }
            }
            latestStats?.cpuCoreUsageValues?.takeIf { it.isNotEmpty() }?.let { cores ->
                item {
                    PerCoreCpuSection(cores = cores)
                }
            }
            if (containers.isNotEmpty()) {
                item {
                    ContainersSection(containers = containers)
                }
            }

            if (latestStats != null) {
                item {
                    GpuMetricsSection(
                        latest = latestStats,
                        history = statsHistory,
                        onUsageClick = { gpuDetailsMetric.value = GpuMetricType.USAGE },
                        onPowerClick = { gpuDetailsMetric.value = GpuMetricType.POWER },
                        onVramClick = { gpuDetailsMetric.value = GpuMetricType.VRAM }
                    )
                }
                item {
                    ExtraMetricsSectionHost(
                        latest = latestStats,
                        history = statsHistory,
                        onMetricClick = { expandedMetric.value = it }
                    )
                }
            }
        }
    }

    expandedMetric.value?.let { metric ->
        ExtraMetricDetailsSheet(
            metric = metric,
            history = statsHistory,
            onDismiss = { expandedMetric.value = null }
        )
    }

    expandedResourceMetric.value?.let { metric ->
        when (metric) {
            ResourceMetricType.CPU -> {
                CpuDetailsSheet(
                    history = statsHistory,
                    onDismiss = { expandedResourceMetric.value = null }
                )
            }

            ResourceMetricType.MEMORY -> {
                ResourceMetricDetailsSheet(
                    title = stringResource(R.string.beszel_memory_usage),
                    data = memoryUsedHistory.ifEmpty { memHistory },
                    accent = StatusPurple,
                    unitFormatter = { value -> formatGB(value) },
                    onDismiss = { expandedResourceMetric.value = null }
                )
            }
        }
    }

    expandedDockerMetric.value?.let { metric ->
        when (metric) {
            DockerMetricType.CPU -> {
                ResourceMetricDetailsSheet(
                    title = stringResource(R.string.beszel_docker_cpu_usage),
                    data = dockerCpuHistory,
                    accent = ServiceType.BESZEL.primaryColor,
                    unitFormatter = { value -> String.format("%.1f%%", value) },
                    onDismiss = { expandedDockerMetric.value = null }
                )
            }

            DockerMetricType.MEMORY -> {
                ResourceMetricDetailsSheet(
                    title = stringResource(R.string.beszel_docker_memory_usage),
                    data = dockerMemoryHistory,
                    accent = StatusPurple,
                    unitFormatter = { value -> formatMB(value) },
                    onDismiss = { expandedDockerMetric.value = null }
                )
            }

            DockerMetricType.NETWORK -> {
                DualMetricDetailsSheet(
                    title = stringResource(R.string.beszel_docker_network_io),
                    data = dockerNetworkDownHistory,
                    secondaryData = dockerNetworkUpHistory,
                    accent = StatusOrange,
                    secondaryColor = StatusPurple,
                    unitFormatter = { value -> formatNetRateBytesPerSec(value) },
                    primaryLegend = stringResource(R.string.beszel_download),
                    secondaryLegend = stringResource(R.string.beszel_upload),
                    onDismiss = { expandedDockerMetric.value = null }
                )
            }
        }
    }

    expandedDiskFs.value?.let { fs ->
        DiskFsDetailsSheet(
            drive = fs,
            history = statsHistory,
            onDismiss = { expandedDiskFs.value = null }
        )
    }

    expandedSmartDevice.value?.let { device ->
        SmartDetailsSheet(
            device = device,
            onDismiss = { expandedSmartDevice.value = null }
        )
    }

    gpuDetailsMetric.value?.let { metric ->
        GpuDetailsSheet(
            metric = metric,
            history = statsHistory,
            onDismiss = { gpuDetailsMetric.value = null }
        )
    }
}

@Composable
private fun ExtraMetricsSectionHost(
    latest: BeszelRecordStats,
    history: List<BeszelRecordStats>,
    onMetricClick: (ExtraMetricType) -> Unit
) {
    ExtraMetricsSection(
        latest = latest,
        history = history,
        onMetricClick = onMetricClick
    )
}

private val BeszelRecordStats.dockerSummary: DockerMetricSummary?
    get() = dc?.takeIf { it.isNotEmpty() }?.toDockerMetricSummary()

private fun List<BeszelRecordStats>.containerSeries(
    selector: (BeszelContainer) -> Double?
): List<Double> = mapNotNull { stats ->
    stats.dc?.sumNullable(selector)
}

private fun List<BeszelContainer>.toDockerMetricSummary(): DockerMetricSummary = DockerMetricSummary(
    cpuPercent = sumOf(BeszelContainer::cpuValue),
    memoryMb = sumOf(BeszelContainer::mValue),
    bandwidthUpBytesPerSec = sumNullable(BeszelContainer::bandwidthUpBytesPerSec),
    bandwidthDownBytesPerSec = sumNullable(BeszelContainer::bandwidthDownBytesPerSec)
)

private fun List<BeszelContainer>.sumNullable(
    selector: (BeszelContainer) -> Double?
): Double? = mapNotNull(selector).takeIf { it.isNotEmpty() }?.sum()
