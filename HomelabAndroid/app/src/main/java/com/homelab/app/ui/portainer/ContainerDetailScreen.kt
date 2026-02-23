package com.homelab.app.ui.portainer

import android.util.Log

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.stringResource
import com.homelab.app.R
import com.homelab.app.ui.components.M3ExpressiveButtonCard
import com.homelab.app.data.remote.dto.portainer.*
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.util.ServiceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContainerDetailScreen(
    onNavigateBack: () -> Unit,
    viewModel: ContainerDetailViewModel = hiltViewModel()
) {
    val container by viewModel.container.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val logs by viewModel.logs.collectAsStateWithLifecycle()
    val composeFile by viewModel.composeFile.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val tabs = remember(composeFile) {
        val list = mutableListOf<Int>(R.string.portainer_details, R.string.portainer_stats, R.string.portainer_logs)
        if (composeFile != null) list.add(R.string.portainer_compose)
        list
    }
    
    var selectedTabIndex by remember(tabs) { mutableIntStateOf(0) }

    LaunchedEffect(selectedTabIndex, tabs) {
        val tabResId = tabs.getOrNull(selectedTabIndex)
        when (tabResId) {
            R.string.portainer_stats -> viewModel.fetchStats()
            R.string.portainer_logs -> viewModel.fetchLogs()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(container?.displayName ?: stringResource(R.string.portainer_details), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && container == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ServiceType.PORTAINER.primaryColor)
            }
        } else if (error != null && container == null) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = error ?: stringResource(R.string.error_unknown),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                // Tabs
                val haptic = LocalHapticFeedback.current
                SecondaryScrollableTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    contentColor = ServiceType.PORTAINER.primaryColor,
                    edgePadding = 0.dp
                ) {
                    tabs.forEachIndexed { index, resId ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = {
                                haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                                selectedTabIndex = index
                            },
                            text = { 
                                Text(
                                    stringResource(resId), 
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = androidx.compose.ui.text.style.TextOverflow.Visible
                                ) 
                            },
                            unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Content
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    val tabResId = tabs.getOrNull(selectedTabIndex)
                    when (tabResId) {
                        R.string.portainer_details -> DetailTabContent(detail = container, onAction = { viewModel.executeAction(it) })
                        R.string.portainer_stats -> StatsTabContent(stats = stats)
                        R.string.portainer_logs -> LogsTabContent(logs = logs)
                        R.string.portainer_compose -> ComposeTabContent(composeFile = composeFile)
                    }
                }
            }
        }
    }
}

@Composable
private fun DetailTabContent(
    detail: com.homelab.app.data.remote.dto.portainer.ContainerDetail?,
    onAction: (ContainerAction) -> Unit
) {
    if (detail == null) return

    // Quick Actions matching Swift iOS
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (detail.state.running) {
            M3ExpressiveButtonCard(
                text = stringResource(R.string.portainer_stop),
                icon = Icons.Default.Stop,
                color = StatusRed,
                modifier = Modifier.weight(1f),
                onClick = { onAction(ContainerAction.stop) }
            )
            M3ExpressiveButtonCard(
                text = stringResource(R.string.portainer_restart),
                icon = Icons.Default.Refresh,
                color = StatusOrange,
                modifier = Modifier.weight(1f),
                onClick = { onAction(ContainerAction.restart) }
            )
        } else {
            M3ExpressiveButtonCard(
                text = stringResource(R.string.portainer_start),
                icon = Icons.Default.PlayArrow,
                color = StatusGreen,
                modifier = Modifier.weight(1f),
                onClick = { onAction(ContainerAction.start) }
            )
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // Info Card
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.beszel_info_title), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(8.dp))
            InfoRow(stringResource(R.string.portainer_image), detail.config.image)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(stringResource(R.string.portainer_status), detail.state.status.uppercase())
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(stringResource(R.string.portainer_created), detail.created.take(10))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(stringResource(R.string.portainer_network_ip), detail.networkSettings.networks.values.firstOrNull()?.ipAddress ?: stringResource(R.string.not_available))
        }
    }
}

@Composable
private fun StatsTabContent(stats: com.homelab.app.data.remote.dto.portainer.ContainerStats?) {
    if (stats == null) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ServiceType.PORTAINER.primaryColor)
        }
        return
    }

    // CPU Calculation (simplified)
    val cpuDelta = stats.cpu_stats.cpu_usage.total_usage - stats.precpu_stats.cpu_usage.total_usage
    val systemDelta = (stats.cpu_stats.system_cpu_usage ?: 0) - (stats.precpu_stats.system_cpu_usage ?: 0)
    var cpuPercent = 0.0
    if (systemDelta > 0.0 && cpuDelta > 0.0) {
        cpuPercent = (cpuDelta.toDouble() / systemDelta.toDouble()) * (stats.cpu_stats.online_cpus ?: 1) * 100.0
    }

    // Mem Calculation (simplified)
    val memUsage = stats.memory_stats.usage - (stats.memory_stats.stats?.cache ?: 0)
    val memLimit = stats.memory_stats.limit
    val memPercent = if (memLimit > 0) (memUsage.toDouble() / memLimit.toDouble()) * 100 else 0.0

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(stringResource(R.string.portainer_cpu_ram), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(16.dp))
            InfoRow(stringResource(R.string.portainer_cpu_usage), String.format("%.2f%%", cpuPercent))
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(stringResource(R.string.beszel_memory), "${memUsage / (1024 * 1024)} MB / ${memLimit / (1024 * 1024)} MB")
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            InfoRow(stringResource(R.string.portainer_memory_percent), String.format("%.2f%%", memPercent))
        }
    }
}

@Composable
private fun LogsTabContent(logs: String?) {
    if (logs == null) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = ServiceType.PORTAINER.primaryColor)
        }
        return
    }

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E), // Dark terminal background
        modifier = Modifier.fillMaxWidth().heightIn(min = 300.dp, max = 500.dp)
    ) {
        SelectionContainer {
            Text(
                text = logs.takeLast(5000), // Prevent massive text layout lag
                color = Color(0xFF00FF00), // Terminal green
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text = value, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1)
    }
}

@Composable
private fun ComposeTabContent(composeFile: String?) {
    if (composeFile == null) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1E1E1E), // Dark editor background
        modifier = Modifier.fillMaxWidth().heightIn(min = 400.dp)
    ) {
        SelectionContainer {
            Text(
                text = composeFile,
                color = Color(0xFFF8F8F2), // Light yaml text
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(16.dp)
            )
        }
    }
}
