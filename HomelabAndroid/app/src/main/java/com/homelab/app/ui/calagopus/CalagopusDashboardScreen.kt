package com.homelab.app.ui.calagopus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Circle
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CalagopusDashboardScreen(
    viewModel: CalagopusViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val actionServerId by viewModel.actionServerId.collectAsStateWithLifecycle()

    val currentInstance = instances.find { it.id == viewModel.instanceId }
    val title = currentInstance?.label?.takeIf { it.isNotBlank() } ?: ServiceType.CALAGOPUS.displayName

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(viewModel) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
            if (instances.size > 1) {
                ServiceInstancePicker(
                    instances = instances,
                    selectedInstanceId = viewModel.instanceId,
                    onInstanceSelected = { onNavigateToInstance(it.id) },
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }

            when (val s = state) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = s.message,
                        onRetry = s.retryAction ?: { viewModel.refresh() }
                    )
                }
                is UiState.Success -> {
                    val servers = s.data
                    if (servers.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.calagopus_no_servers),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        PullToRefreshBox(isRefreshing = isRefreshing, onRefresh = { viewModel.refresh() }) {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                items(servers, key = { it.server.uuidShort }) { item ->
                                    CalagopusServerCard(
                                        item = item,
                                        isActionPending = actionServerId == item.server.uuidShort,
                                        onPowerSignal = { signal ->
                                            viewModel.sendPowerSignal(item.server.uuidShort, signal)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CalagopusServerCard(
    item: CalagopusServerWithResources,
    isActionPending: Boolean,
    onPowerSignal: (String) -> Unit
) {
    val server = item.server
    val res = item.resources
    val state = res?.state ?: "offline"
    val isRunning = state == "running"
    val isStarting = state == "starting"
    val isStopping = state == "stopping"

    val statusColor = when {
        server.isSuspended -> MaterialTheme.colorScheme.error
        isRunning -> Color(0xFF16A34A)
        isStarting || isStopping -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusLabel = when {
        server.isSuspended -> stringResource(R.string.calagopus_status_suspended)
        isRunning -> stringResource(R.string.calagopus_status_running)
        isStarting -> stringResource(R.string.calagopus_status_starting)
        isStopping -> stringResource(R.string.calagopus_status_stopping)
        else -> stringResource(R.string.calagopus_status_offline)
    }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Circle,
                    contentDescription = null,
                    tint = statusColor,
                    modifier = Modifier.size(10.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = server.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = statusLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor
                    )
                }
                if (isActionPending) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }
            }

            if (res != null && !server.isSuspended) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ResourceChip(
                        icon = Icons.Default.Speed,
                        label = stringResource(R.string.calagopus_cpu),
                        value = String.format(Locale.getDefault(), "%.1f%%", res.cpuAbsolute)
                    )
                    val memMb = res.memoryBytes / 1_048_576.0
                    ResourceChip(
                        icon = Icons.Default.Memory,
                        label = stringResource(R.string.calagopus_ram),
                        value = String.format(Locale.getDefault(), "%.0f MB", memMb)
                    )
                    val diskMb = res.diskBytes / 1_048_576.0
                    ResourceChip(
                        icon = Icons.Default.Storage,
                        label = stringResource(R.string.calagopus_disk),
                        value = String.format(Locale.getDefault(), "%.0f MB", diskMb)
                    )
                    if (res.uptime > 0) {
                        ResourceChip(
                            icon = Icons.Default.Timer,
                            label = stringResource(R.string.calagopus_uptime),
                            value = formatUptime(res.uptime / 1000L)
                        )
                    }
                }
            }

            if (!server.isSuspended && !isActionPending) {
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!isRunning && !isStarting) {
                        OutlinedButton(
                            onClick = { onPowerSignal("start") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.calagopus_action_start))
                        }
                    }
                    if (isRunning || isStarting) {
                        OutlinedButton(
                            onClick = { onPowerSignal("restart") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.calagopus_action_restart))
                        }
                        OutlinedButton(
                            onClick = { onPowerSignal("stop") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.calagopus_action_stop))
                        }
                    }
                    if (isRunning) {
                        OutlinedButton(
                            onClick = { onPowerSignal("kill") },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.calagopus_action_kill))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ResourceChip(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    AssistChip(
        onClick = {},
        label = { Text("$label: $value", style = MaterialTheme.typography.labelSmall) },
        leadingIcon = { Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp)) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    )
}

private fun formatUptime(seconds: Long): String {
    val days = seconds / 86400
    val hours = (seconds % 86400) / 3600
    val mins = (seconds % 3600) / 60
    return when {
        days > 0 -> "${days}d ${hours}h"
        hours > 0 -> "${hours}h ${mins}m"
        else -> "${mins}m"
    }
}
