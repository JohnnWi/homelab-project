package com.homelab.app.ui.uptimekuma

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.UptimeKumaDashboardData
import com.homelab.app.data.repository.UptimeKumaMonitor
import com.homelab.app.data.repository.UptimeKumaMonitorStatus
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.text.NumberFormat
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UptimeKumaDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: UptimeKumaViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val accent = ServiceType.UPTIME_KUMA.primaryColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_uptime_kuma),
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
                    IconButton(onClick = { viewModel.fetchDashboard(forceLoading = false) }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(uptimeKumaPageBrush(accent))
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                UiState.Loading, UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = state.retryAction ?: { viewModel.fetchDashboard(forceLoading = true) }
                    )
                }
                UiState.Offline -> {
                    ErrorScreen(
                        message = stringResource(R.string.error_network),
                        onRetry = { viewModel.fetchDashboard(forceLoading = true) },
                        isOffline = true
                    )
                }
                is UiState.Success -> {
                    UptimeKumaContent(
                        data = state.data,
                        instances = instances,
                        selectedInstanceId = viewModel.instanceId,
                        onInstanceSelected = {
                            viewModel.setPreferredInstance(it.id)
                            onNavigateToInstance(it.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun UptimeKumaContent(
    data: UptimeKumaDashboardData,
    instances: List<ServiceInstance>,
    selectedInstanceId: String,
    onInstanceSelected: (ServiceInstance) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServiceInstancePicker(
                instances = instances,
                selectedInstanceId = selectedInstanceId,
                onInstanceSelected = onInstanceSelected,
                label = stringResource(R.string.uptime_kuma_instance_label)
            )
        }

        item { UptimeKumaHero(data) }
        item { UptimeKumaStatusCard(data) }

        if (data.monitors.isEmpty()) {
            item { EmptyUptimeKumaCard() }
        } else {
            items(data.monitors, key = { it.id }) { monitor ->
                UptimeKumaMonitorCard(monitor)
            }
        }
    }
}

@Composable
private fun UptimeKumaHero(data: UptimeKumaDashboardData) {
    val accent = ServiceType.UPTIME_KUMA.primaryColor
    UptimeKumaCard(tint = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(type = ServiceType.UPTIME_KUMA, size = 58.dp, iconSize = 38.dp, cornerRadius = 16.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.service_uptime_kuma),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.service_uptime_kuma_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            UptimeKumaMetric(
                icon = Icons.Default.CheckCircle,
                value = "${data.upCount}",
                subValue = "/ ${data.monitors.size}",
                label = stringResource(R.string.uptime_kuma_up),
                tint = StatusGreen,
                modifier = Modifier.weight(1f)
            )
            UptimeKumaMetric(
                icon = Icons.Default.ErrorOutline,
                value = "${data.downCount}",
                subValue = null,
                label = stringResource(R.string.uptime_kuma_down),
                tint = if (data.downCount > 0) StatusRed else accent,
                modifier = Modifier.weight(1f)
            )
            UptimeKumaMetric(
                icon = Icons.Default.Speed,
                value = data.averageLatencyMs?.roundToInt()?.toString() ?: "-",
                subValue = if (data.averageLatencyMs != null) "ms" else null,
                label = stringResource(R.string.uptime_kuma_avg_latency),
                tint = StatusBlue,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun UptimeKumaStatusCard(data: UptimeKumaDashboardData) {
    UptimeKumaCard {
        Text(
            text = stringResource(R.string.uptime_kuma_monitors),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatusChip(stringResource(R.string.uptime_kuma_up), data.upCount, StatusGreen, Modifier.weight(1f))
            StatusChip(stringResource(R.string.uptime_kuma_down), data.downCount, StatusRed, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            StatusChip(stringResource(R.string.uptime_kuma_pending), data.pendingCount, StatusOrange, Modifier.weight(1f))
            StatusChip(stringResource(R.string.uptime_kuma_maintenance), data.maintenanceCount, StatusBlue, Modifier.weight(1f))
        }
        if (data.expiringCertificates > 0 || data.unknownCount > 0) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                StatusChip(stringResource(R.string.uptime_kuma_cert_expiring), data.expiringCertificates, StatusOrange, Modifier.weight(1f))
                StatusChip(stringResource(R.string.uptime_kuma_unknown), data.unknownCount, MaterialTheme.colorScheme.onSurfaceVariant, Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun UptimeKumaMonitorCard(monitor: UptimeKumaMonitor) {
    val tint = statusColor(monitor.status)
    UptimeKumaCard(tint = tint) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = tint.copy(alpha = 0.1f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = statusIcon(monitor.status),
                    contentDescription = statusLabel(monitor.status),
                    tint = tint,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
                Text(
                    text = monitor.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                monitor.url?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    UptimeKumaPill(statusLabel(monitor.status), tint)
                    monitor.type?.takeIf { it.isNotBlank() }?.let {
                        UptimeKumaPill(it.uppercase(), MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    DetailValue(stringResource(R.string.uptime_kuma_response_time), monitor.responseTimeMs?.roundToInt()?.let { "${formatNumber(it)} ms" } ?: "-")
                    DetailValue(stringResource(R.string.uptime_kuma_cert_days), monitor.certDaysRemaining?.roundToInt()?.let { "${formatNumber(it)} d" } ?: "-")
                }
            }
        }
    }
}

@Composable
private fun UptimeKumaMetric(
    icon: ImageVector,
    value: String,
    subValue: String?,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.heightIn(min = 82.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = tint)
            if (subValue != null) {
                Spacer(modifier = Modifier.width(4.dp))
                Text(subValue, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun StatusChip(label: String, value: Int, tint: Color, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(14.dp),
        color = tint.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.10f else 0.07f),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(formatNumber(value), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = tint)
            Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun DetailValue(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun EmptyUptimeKumaCard() {
    UptimeKumaCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Visibility, contentDescription = stringResource(R.string.uptime_kuma_no_monitors), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Text(stringResource(R.string.uptime_kuma_no_monitors), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun UptimeKumaCard(
    modifier: Modifier = Modifier,
    tint: Color? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val base = MaterialTheme.colorScheme.surfaceContainerLow
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = tint?.copy(alpha = if (MaterialTheme.colorScheme.background.luminance() < 0.45f) 0.06f else 0.04f)?.compositeOver(base) ?: base,
        border = BorderStroke(1.dp, (tint ?: MaterialTheme.colorScheme.outlineVariant).copy(alpha = 0.18f))
    ) {
        Column(modifier = Modifier.padding(16.dp), content = content)
    }
}

@Composable
private fun UptimeKumaPill(text: String, tint: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f))
    )
}

@Composable
private fun statusLabel(status: UptimeKumaMonitorStatus): String {
    return when (status) {
        UptimeKumaMonitorStatus.UP -> stringResource(R.string.uptime_kuma_up)
        UptimeKumaMonitorStatus.DOWN -> stringResource(R.string.uptime_kuma_down)
        UptimeKumaMonitorStatus.PENDING -> stringResource(R.string.uptime_kuma_pending)
        UptimeKumaMonitorStatus.MAINTENANCE -> stringResource(R.string.uptime_kuma_maintenance)
        UptimeKumaMonitorStatus.UNKNOWN -> stringResource(R.string.uptime_kuma_unknown)
    }
}

@Composable
private fun statusColor(status: UptimeKumaMonitorStatus): Color {
    return when (status) {
        UptimeKumaMonitorStatus.UP -> StatusGreen
        UptimeKumaMonitorStatus.DOWN -> StatusRed
        UptimeKumaMonitorStatus.PENDING -> StatusOrange
        UptimeKumaMonitorStatus.MAINTENANCE -> StatusBlue
        UptimeKumaMonitorStatus.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun statusIcon(status: UptimeKumaMonitorStatus): ImageVector {
    return when (status) {
        UptimeKumaMonitorStatus.UP -> Icons.Default.CheckCircle
        UptimeKumaMonitorStatus.DOWN -> Icons.Default.ErrorOutline
        UptimeKumaMonitorStatus.PENDING -> Icons.Default.HourglassTop
        UptimeKumaMonitorStatus.MAINTENANCE -> Icons.Default.Tune
        UptimeKumaMonitorStatus.UNKNOWN -> Icons.Default.Security
    }
}

@Composable
private fun uptimeKumaPageBrush(accent: Color): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    return if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF07110B), Color(0xFF0B1410), accent.copy(alpha = 0.035f), Color(0xFF080D0A)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF8FFF9), Color(0xFFF1FBF4), accent.copy(alpha = 0.025f), Color(0xFFFCFEFC)))
    }
}

private fun formatNumber(value: Int): String {
    return NumberFormat.getIntegerInstance().format(value)
}
