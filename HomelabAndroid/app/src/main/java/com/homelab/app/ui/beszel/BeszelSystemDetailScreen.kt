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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.beszel.BeszelRecordStats
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
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
    val detailUiModel by viewModel.systemDetailUiModel.collectAsStateWithLifecycle()

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
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchSystemDetail(systemId) }) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
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
                detailUiModel?.let { model ->
                    BeszelSystemDetailContent(model = model, paddingValues = paddingValues)
                }
            }
        }
    }
}

@Composable
private fun BeszelSystemDetailContent(
    model: BeszelSystemDetailUiModel,
    paddingValues: PaddingValues
) {
    val info = model.system.info
    val expandedMetric = remember { mutableStateOf<ExtraMetricType?>(null) }
    val expandedResourceMetric = remember { mutableStateOf<ResourceMetricType?>(null) }
    val expandedDockerMetric = remember { mutableStateOf<DockerMetricType?>(null) }
    val expandedDiskFs = remember { mutableStateOf<DiskFsUsage?>(null) }
    val expandedSmartDevice = remember { mutableStateOf<BeszelSmartDevice?>(null) }
    val gpuDetailsMetric = remember { mutableStateOf<GpuMetricType?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            BeszelHeaderCard(model.system)
        }

        if (info != null || model.systemDetails != null) {
            item {
                SystemInfoSection(info = info, details = model.systemDetails)
            }
        }

        if (info != null) {
            item {
                ResourcesSection(
                    cpu = info.cpuValue,
                    mp = info.mpValue,
                    dp = info.dpValue,
                    memoryUsedGb = model.memoryUsedGb,
                    memoryTotalGb = model.memoryTotalGb,
                    memoryUsedHistory = model.memoryUsedHistoryGb,
                    diskUsed = model.diskUsedGb,
                    diskTotal = model.diskTotalGb,
                    externalFileSystems = model.externalFileSystems,
                    cpuHistory = model.cpuHistoryPercent,
                    memHistory = model.memoryHistoryPercent,
                    onCpuClick = { expandedResourceMetric.value = ResourceMetricType.CPU },
                    onMemClick = { expandedResourceMetric.value = ResourceMetricType.MEMORY },
                    onDiskFsClick = { fs -> expandedDiskFs.value = fs }
                )
            }

            if (model.smartDevices.isNotEmpty()) {
                item {
                    SmartDevicesSection(
                        devices = model.smartDevices,
                        onDeviceClick = { device -> expandedSmartDevice.value = device }
                    )
                }
            }

            model.dockerSummary?.let { summary ->
                item {
                    DockerMetricsSection(
                        summary = summary,
                        hasNetwork = model.hasDockerNetwork,
                        onCpuClick = { expandedDockerMetric.value = DockerMetricType.CPU },
                        onMemoryClick = { expandedDockerMetric.value = DockerMetricType.MEMORY },
                        onNetworkClick = { expandedDockerMetric.value = DockerMetricType.NETWORK }
                    )
                }
            }

            model.perCoreCpuPercent.takeIf { it.isNotEmpty() }?.let { cores ->
                item {
                    PerCoreCpuSection(cores = cores)
                }
            }

            if (model.containers.isNotEmpty()) {
                item {
                    ContainersSection(containers = model.containers)
                }
            }

            model.latestStats?.let { latestStats ->
                item {
                    GpuMetricsSection(
                        latest = latestStats,
                        history = model.statsHistory,
                        onUsageClick = { gpuDetailsMetric.value = GpuMetricType.USAGE },
                        onPowerClick = { gpuDetailsMetric.value = GpuMetricType.POWER },
                        onVramClick = { gpuDetailsMetric.value = GpuMetricType.VRAM }
                    )
                }
                item {
                    ExtraMetricsSectionHost(
                        latest = latestStats,
                        history = model.statsHistory,
                        onMetricClick = { expandedMetric.value = it }
                    )
                }
            }
        }
    }

    expandedMetric.value?.let { metric ->
        ExtraMetricDetailsSheet(
            metric = metric,
            history = model.statsHistory,
            onDismiss = { expandedMetric.value = null }
        )
    }

    expandedResourceMetric.value?.let { metric ->
        when (metric) {
            ResourceMetricType.CPU -> {
                CpuDetailsSheet(
                    history = model.statsHistory,
                    onDismiss = { expandedResourceMetric.value = null }
                )
            }

            ResourceMetricType.MEMORY -> {
                ResourceMetricDetailsSheet(
                    title = stringResource(R.string.beszel_memory_usage),
                    data = model.memoryUsedHistoryGb,
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
                    data = model.dockerCpuHistoryPercent,
                    accent = ServiceType.BESZEL.primaryColor,
                    unitFormatter = { value -> String.format("%.1f%%", value) },
                    onDismiss = { expandedDockerMetric.value = null }
                )
            }

            DockerMetricType.MEMORY -> {
                ResourceMetricDetailsSheet(
                    title = stringResource(R.string.beszel_docker_memory_usage),
                    data = model.dockerMemoryUsedHistoryMb,
                    accent = StatusPurple,
                    unitFormatter = { value -> formatMB(value) },
                    onDismiss = { expandedDockerMetric.value = null }
                )
            }

            DockerMetricType.NETWORK -> {
                DualMetricDetailsSheet(
                    title = stringResource(R.string.beszel_docker_network_io),
                    data = model.dockerDownloadRateHistoryBytesPerSec,
                    secondaryData = model.dockerUploadRateHistoryBytesPerSec,
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
            history = model.statsHistory,
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
            history = model.statsHistory,
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
