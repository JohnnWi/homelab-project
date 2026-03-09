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
    val latestStats: BeszelRecordStats? = statsHistory.lastOrNull()

    val cpuHistory = remember(records) { statsHistory.takeLast(30).map { it.cpuValue } }
    val memHistory = remember(records) { statsHistory.takeLast(30).map { it.mpValue } }

    val diskUsed = (latestStats?.duValue ?: info?.duValue)?.takeIf { it > 0.0 }
    val diskTotal = (latestStats?.dValue ?: info?.dValue)?.takeIf { it > 0.0 }

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
        val title: String
        val data: List<Double>
        val accent: Color
        val formatter: (Double) -> String

        when (metric) {
            ResourceMetricType.CPU -> {
                title = stringResource(R.string.beszel_cpu)
                data = cpuHistory
                accent = ServiceType.BESZEL.primaryColor
                formatter = { value: Double -> String.format("%.1f%%", value) }
            }

            ResourceMetricType.MEMORY -> {
                title = stringResource(R.string.beszel_memory)
                data = memHistory
                accent = StatusPurple
                formatter = { value: Double -> String.format("%.1f%%", value) }
            }
        }

        ResourceMetricDetailsSheet(
            title = title,
            data = data,
            accent = accent,
            unitFormatter = formatter,
            onDismiss = { expandedResourceMetric.value = null }
        )
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
