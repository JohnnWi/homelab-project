package com.homelab.app.ui.komodo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.KomodoContainerSummary
import com.homelab.app.data.repository.KomodoDashboardData
import com.homelab.app.data.repository.KomodoResourceSummary
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomodoDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: KomodoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val accent = ServiceType.KOMODO.primaryColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_komodo),
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
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
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
                .background(komodoPageBrush(accent))
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
                    KomodoContent(
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
private fun KomodoContent(
    data: KomodoDashboardData,
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
                label = stringResource(R.string.komodo_instance_label)
            )
        }

        item { KomodoHero(data) }

        item {
            KomodoMetricRow(
                left = MetricSpec(
                    label = stringResource(R.string.komodo_servers),
                    value = data.servers.total.toString(),
                    detail = statusDetail(data.servers),
                    icon = Icons.Default.Dns,
                    color = StatusGreen
                ),
                right = MetricSpec(
                    label = stringResource(R.string.komodo_deployments),
                    value = data.deployments.total.toString(),
                    detail = statusDetail(data.deployments),
                    icon = Icons.Default.CloudQueue,
                    color = ServiceType.KOMODO.primaryColor
                )
            )
        }

        item {
            KomodoMetricRow(
                left = MetricSpec(
                    label = stringResource(R.string.komodo_stacks),
                    value = data.stacks.total.toString(),
                    detail = statusDetail(data.stacks),
                    icon = Icons.Default.Inventory2,
                    color = StatusOrange
                ),
                right = MetricSpec(
                    label = stringResource(R.string.komodo_containers),
                    value = "${data.containers.running}/${data.containers.total}",
                    detail = stringResource(R.string.komodo_running),
                    icon = Icons.Default.Widgets,
                    color = StatusGreen
                )
            )
        }

        item { KomodoContainerStatesCard(data.containers) }
    }
}

@Composable
private fun KomodoHero(data: KomodoDashboardData) {
    val accent = ServiceType.KOMODO.primaryColor
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = accent.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f)),
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceIcon(type = ServiceType.KOMODO, size = 58.dp, iconSize = 38.dp, cornerRadius = 16.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.service_komodo),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = stringResource(R.string.service_komodo_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
                KomodoMiniStat(
                    label = stringResource(R.string.komodo_version),
                    value = data.version?.removePrefix("v") ?: "-",
                    modifier = Modifier.weight(1f)
                )
                KomodoMiniStat(
                    label = stringResource(R.string.komodo_healthy),
                    value = (data.servers.healthy + data.deployments.healthy + data.stacks.healthy).toString(),
                    modifier = Modifier.weight(1f)
                )
                KomodoMiniStat(
                    label = stringResource(R.string.komodo_unhealthy),
                    value = (data.servers.unhealthy + data.deployments.unhealthy + data.stacks.unhealthy).toString(),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun KomodoMiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 72.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KomodoMetricRow(left: MetricSpec, right: MetricSpec) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth()) {
        KomodoMetricCard(spec = left, modifier = Modifier.weight(1f))
        KomodoMetricCard(spec = right, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun KomodoMetricCard(spec: MetricSpec, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier.heightIn(min = 136.dp),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = spec.color.copy(alpha = 0.14f)
                ) {
                    Icon(
                        imageVector = spec.icon,
                        contentDescription = null,
                        tint = spec.color,
                        modifier = Modifier.padding(9.dp).size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = spec.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = spec.value,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = spec.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun KomodoContainerStatesCard(summary: KomodoContainerSummary) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Widgets, contentDescription = null, tint = ServiceType.KOMODO.primaryColor)
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.komodo_container_states),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            StateBar(stringResource(R.string.komodo_running), summary.running, summary.total, StatusGreen)
            StateBar(stringResource(R.string.komodo_stopped), summary.stopped + summary.exited, summary.total, StatusRed)
            StateBar(stringResource(R.string.komodo_paused), summary.paused, summary.total, StatusOrange)
            StateBar(stringResource(R.string.komodo_unhealthy), summary.unhealthy + summary.restarting, summary.total, StatusRed)
            StateBar(stringResource(R.string.komodo_unknown), summary.unknown, summary.total, MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StateBar(label: String, value: Int, total: Int, color: Color) {
    val progress = if (total > 0) (value.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier.fillMaxWidth().height(7.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
        )
    }
}

@Composable
private fun statusDetail(summary: KomodoResourceSummary): String {
    return if (summary.unhealthy > 0) {
        "${summary.unhealthy} ${stringResource(R.string.komodo_unhealthy)}"
    } else if (summary.healthy > 0) {
        "${summary.healthy} ${stringResource(R.string.komodo_healthy)}"
    } else if (summary.running > 0) {
        "${summary.running} ${stringResource(R.string.komodo_running)}"
    } else {
        "${summary.unknown} ${stringResource(R.string.komodo_unknown)}"
    }
}

@Composable
private fun komodoPageBrush(accent: Color): Brush {
    val background = MaterialTheme.colorScheme.background
    return Brush.verticalGradient(
        colors = listOf(
            accent.copy(alpha = 0.08f).compositeOver(background),
            background,
            background
        )
    )
}

private data class MetricSpec(
    val label: String,
    val value: String,
    val detail: String,
    val icon: ImageVector,
    val color: Color
)
