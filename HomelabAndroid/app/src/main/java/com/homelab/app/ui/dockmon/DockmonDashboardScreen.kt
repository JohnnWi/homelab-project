package com.homelab.app.ui.dockmon

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.DockmonContainer
import com.homelab.app.data.repository.DockmonDashboardData
import com.homelab.app.data.repository.DockmonHost
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import kotlinx.coroutines.flow.collectLatest

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockmonDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: DockmonViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedHostId by viewModel.selectedHostId.collectAsStateWithLifecycle()
    val containers by viewModel.visibleContainers.collectAsStateWithLifecycle()
    val selectedContainer by viewModel.selectedContainer.collectAsStateWithLifecycle()
    val selectedContainerId by viewModel.selectedContainerId.collectAsStateWithLifecycle()
    val logsState by viewModel.logsState.collectAsStateWithLifecycle()
    val imageDraft by viewModel.imageDraft.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRunningAction by viewModel.isRunningAction.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val accent = ServiceType.DOCKMON.primaryColor

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_dockmon),
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(dockmonPageBrush(accent))
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
                    DockmonContent(
                        data = state.data,
                        instances = instances,
                        selectedInstanceId = viewModel.instanceId,
                        selectedHostId = selectedHostId,
                        containers = containers,
                        onInstanceSelected = {
                            viewModel.setPreferredInstance(it.id)
                            onNavigateToInstance(it.id)
                        },
                        onHostSelected = viewModel::selectHost,
                        onContainerSelected = viewModel::openContainer
                    )
                }
            }
        }
    }

    if (selectedContainerId != null) {
        ModalBottomSheet(onDismissRequest = viewModel::closeContainer) {
            DockmonContainerSheet(
                container = selectedContainer,
                logsState = logsState,
                imageDraft = imageDraft,
                isRunningAction = isRunningAction,
                onImageDraftChanged = viewModel::updateImageDraft,
                onRefreshLogs = { viewModel.refreshLogs(forceLoading = true) },
                onRestart = viewModel::restartSelectedContainer,
                onUpdate = viewModel::updateSelectedContainer,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun DockmonContent(
    data: DockmonDashboardData,
    instances: List<com.homelab.app.domain.model.ServiceInstance>,
    selectedInstanceId: String,
    selectedHostId: String?,
    containers: List<DockmonContainer>,
    onInstanceSelected: (com.homelab.app.domain.model.ServiceInstance) -> Unit,
    onHostSelected: (String?) -> Unit,
    onContainerSelected: (String) -> Unit
) {
    val accent = ServiceType.DOCKMON.primaryColor
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
                label = stringResource(R.string.dockmon_instance_label)
            )
        }

        item {
            DockmonHero(data = data)
        }

        item {
            DockmonHostStrip(
                hosts = data.hosts,
                selectedHostId = selectedHostId,
                onHostSelected = onHostSelected
            )
        }

        if (containers.isEmpty()) {
            item {
                EmptyDockmonCard()
            }
        } else {
            items(containers, key = { it.id }) { container ->
                DockmonContainerCard(
                    container = container,
                    host = data.hosts.firstOrNull { it.id == container.hostId },
                    accent = accent,
                    onClick = { onContainerSelected(container.id) }
                )
            }
        }
    }
}

@Composable
private fun DockmonHero(data: DockmonDashboardData) {
    val accent = ServiceType.DOCKMON.primaryColor
    DockmonCard(tint = accent) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            ServiceIcon(type = ServiceType.DOCKMON, size = 56.dp, iconSize = 36.dp, cornerRadius = 16.dp)
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.service_dockmon),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.service_dockmon_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            DockmonMetric(Icons.Default.Widgets, "${data.runningContainers}", "/ ${data.containers.size}", stringResource(R.string.dockmon_containers), StatusGreen, Modifier.weight(1f))
            DockmonMetric(Icons.Default.Update, "${data.updateCount}", null, stringResource(R.string.dockmon_updates), if (data.updateCount > 0) StatusOrange else accent, Modifier.weight(1f))
            DockmonMetric(Icons.Default.AutoFixHigh, "${data.autoRestartCount}", null, stringResource(R.string.dockmon_auto_restart), accent, Modifier.weight(1f))
        }
    }
}

@Composable
private fun DockmonMetric(
    icon: ImageVector,
    value: String,
    subValue: String?,
    label: String,
    tint: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .heightIn(min = 82.dp)
            .padding(vertical = 4.dp),
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
private fun DockmonHostStrip(
    hosts: List<DockmonHost>,
    selectedHostId: String?,
    onHostSelected: (String?) -> Unit
) {
    DockmonCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = stringResource(R.string.dockmon_hosts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text("${hosts.count { it.isOnline }}/${hosts.size}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(modifier = Modifier.height(12.dp))
        if (hosts.isEmpty()) {
            Text(stringResource(R.string.dockmon_no_hosts), color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = selectedHostId == null,
                        onClick = { onHostSelected(null) },
                        label = { Text(stringResource(R.string.dockmon_all_hosts)) }
                    )
                }
                items(hosts, key = { it.id }) { host ->
                    FilterChip(
                        selected = selectedHostId == host.id,
                        onClick = { onHostSelected(host.id) },
                        label = { Text(host.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                        leadingIcon = {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(if (host.isOnline) StatusGreen else StatusRed, CircleShape)
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun DockmonContainerCard(
    container: DockmonContainer,
    host: DockmonHost?,
    accent: Color,
    onClick: () -> Unit
) {
    DockmonCard(
        modifier = Modifier.clickable(onClick = onClick),
        tint = if (container.updateAvailable) StatusOrange else if (container.isRunning) StatusGreen else null
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = (if (container.isRunning) StatusGreen else StatusRed).copy(alpha = 0.1f),
                modifier = Modifier.size(44.dp)
            ) {
                Icon(
                    imageVector = if (container.isRunning) Icons.Default.CheckCircle else Icons.Default.ErrorOutline,
                    contentDescription = container.status,
                    tint = if (container.isRunning) StatusGreen else StatusRed,
                    modifier = Modifier.padding(10.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = container.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (container.updateAvailable) {
                        Icon(Icons.Default.Update, contentDescription = stringResource(R.string.dockmon_update_available), tint = StatusOrange, modifier = Modifier.size(18.dp))
                    }
                }
                Text(
                    text = container.image,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DockmonPill(text = if (container.isRunning) stringResource(R.string.dockmon_running) else stringResource(R.string.dockmon_stopped), tint = if (container.isRunning) StatusGreen else StatusRed)
                    if (container.autoRestart) DockmonPill(text = stringResource(R.string.dockmon_auto_restart), tint = accent)
                    if (host != null) DockmonPill(text = host.name, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DockmonContainerSheet(
    container: DockmonContainer?,
    logsState: UiState<String>,
    imageDraft: String,
    isRunningAction: Boolean,
    onImageDraftChanged: (String) -> Unit,
    onRefreshLogs: () -> Unit,
    onRestart: () -> Unit,
    onUpdate: () -> Unit,
    modifier: Modifier = Modifier
) {
    val accent = ServiceType.DOCKMON.primaryColor
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Text(
                text = container?.name ?: stringResource(R.string.dockmon_containers),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (container != null) {
                Spacer(modifier = Modifier.height(6.dp))
                DetailLine(stringResource(R.string.dockmon_current_image), container.image)
                container.latestImage?.let { DetailLine(stringResource(R.string.dockmon_latest_image), it) }
                container.portsSummary?.let { DetailLine(stringResource(R.string.dockmon_ports), it) }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = onRestart,
                    enabled = !isRunningAction && container != null,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.RestartAlt, contentDescription = stringResource(R.string.dockmon_restart_container), modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dockmon_restart_container), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Button(
                    onClick = onUpdate,
                    enabled = !isRunningAction && container != null,
                    modifier = Modifier.weight(1f)
                ) {
                    if (isRunningAction) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Default.Update, contentDescription = stringResource(R.string.dockmon_update_container), modifier = Modifier.size(18.dp))
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.dockmon_update_container), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }

        item {
            OutlinedTextField(
                value = imageDraft,
                onValueChange = onImageDraftChanged,
                label = { Text(stringResource(R.string.dockmon_image_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = stringResource(R.string.dockmon_image_placeholder)) },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.dockmon_logs),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRefreshLogs) {
                    Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), tint = accent)
                }
            }
        }

        item {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = dockmonTerminalColor(),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 220.dp, max = 360.dp)
            ) {
                when (logsState) {
                    UiState.Idle, UiState.Loading -> Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                    is UiState.Error -> Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.Terminal, contentDescription = stringResource(R.string.error), tint = MaterialTheme.colorScheme.error)
                        Text(logsState.message, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    is UiState.Success -> SelectionContainer {
                        Text(
                            text = logsState.data.ifBlank { stringResource(R.string.no_data) },
                            modifier = Modifier.padding(14.dp),
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    UiState.Offline -> Text(stringResource(R.string.error_network), modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}

@Composable
private fun EmptyDockmonCard() {
    DockmonCard {
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Dns, contentDescription = stringResource(R.string.dockmon_no_containers), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(10.dp))
            Text(stringResource(R.string.dockmon_no_containers), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DockmonCard(
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
private fun DockmonPill(text: String, tint: Color) {
    AssistChip(
        onClick = {},
        label = { Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        border = BorderStroke(1.dp, tint.copy(alpha = 0.22f))
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(112.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
    }
}

@Composable
private fun dockmonPageBrush(accent: Color): Brush {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.45f
    return if (dark) {
        Brush.verticalGradient(listOf(Color(0xFF071016), Color(0xFF0C1319), accent.copy(alpha = 0.035f), Color(0xFF080C11)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF7FBFF), Color(0xFFF0F8FF), accent.copy(alpha = 0.025f), Color(0xFFFBFDFF)))
    }
}

@Composable
private fun dockmonTerminalColor(): Color {
    return if (MaterialTheme.colorScheme.background.luminance() < 0.45f) {
        Color(0xFF0A0F13)
    } else {
        Color(0xFFF8FAFC)
    }
}
