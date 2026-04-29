package com.homelab.app.ui.komodo

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
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
import com.homelab.app.data.repository.KomodoStackAction
import com.homelab.app.data.repository.KomodoStackDetail
import com.homelab.app.data.repository.KomodoStackItem
import com.homelab.app.data.repository.KomodoStackService
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
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KomodoDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: KomodoViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val stacksState by viewModel.stacksState.collectAsStateWithLifecycle()
    val stackDetailState by viewModel.stackDetailState.collectAsStateWithLifecycle()
    val isRunningStackAction by viewModel.isRunningStackAction.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val accent = ServiceType.KOMODO.primaryColor
    val snackbarHostState = remember { SnackbarHostState() }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var showStacksSheet by remember { mutableStateOf(false) }
    val actionCompleted = stringResource(R.string.komodo_action_completed)

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is KomodoUiEvent.StackActionSucceeded -> snackbarHostState.showSnackbar(actionCompleted)
                is KomodoUiEvent.StackActionFailed -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
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
                        },
                        onStacksClicked = {
                            showStacksSheet = true
                            viewModel.loadStacks()
                        }
                    )
                }
            }
        }

        if (showStacksSheet) {
            ModalBottomSheet(
                onDismissRequest = {
                    showStacksSheet = false
                    viewModel.clearStackDetail()
                },
                sheetState = sheetState,
                containerColor = MaterialTheme.colorScheme.surface
            ) {
                KomodoStacksSheet(
                    stacksState = stacksState,
                    detailState = stackDetailState,
                    isRunningAction = isRunningStackAction,
                    onBackToList = { viewModel.clearStackDetail() },
                    onRefreshList = { viewModel.loadStacks() },
                    onStackSelected = { viewModel.loadStackDetail(it.id) },
                    onAction = { stackId, action -> viewModel.runStackAction(stackId, action) },
                    onDismiss = {
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            showStacksSheet = false
                            viewModel.clearStackDetail()
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun KomodoContent(
    data: KomodoDashboardData,
    instances: List<ServiceInstance>,
    selectedInstanceId: String,
    onInstanceSelected: (ServiceInstance) -> Unit,
    onStacksClicked: () -> Unit
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
                    color = StatusOrange,
                    onClick = onStacksClicked
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
    val cardModifier = if (spec.onClick != null) {
        modifier.heightIn(min = 136.dp).clickable(onClick = spec.onClick)
    } else {
        modifier.heightIn(min = 136.dp)
    }
    Surface(
        modifier = cardModifier,
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
            if (spec.onClick != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(R.string.komodo_open_stacks),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = spec.color,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = spec.color,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
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
private fun KomodoStacksSheet(
    stacksState: UiState<List<KomodoStackItem>>,
    detailState: UiState<KomodoStackDetail>,
    isRunningAction: Boolean,
    onBackToList: () -> Unit,
    onRefreshList: () -> Unit,
    onStackSelected: (KomodoStackItem) -> Unit,
    onAction: (String, KomodoStackAction) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 360.dp, max = 720.dp)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        when (detailState) {
            is UiState.Success -> {
                KomodoStackDetailHeader(
                    detail = detailState.data,
                    onBackToList = onBackToList,
                    onDismiss = onDismiss
                )
                KomodoStackActions(
                    stackId = detailState.data.stack.id,
                    isRunningAction = isRunningAction,
                    onAction = onAction
                )
                KomodoServicesList(services = detailState.data.services)
            }
            UiState.Loading -> {
                KomodoStackSheetTitle(onRefreshList, onDismiss)
                Box(modifier = Modifier.fillMaxWidth().height(240.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.KOMODO.primaryColor)
                }
            }
            is UiState.Error -> {
                KomodoStackSheetTitle(onRefreshList, onDismiss)
                ErrorScreen(
                    message = detailState.message,
                    onRetry = detailState.retryAction ?: onRefreshList
                )
            }
            UiState.Idle, UiState.Offline -> {
                KomodoStackSheetTitle(onRefreshList, onDismiss)
                when (stacksState) {
                    is UiState.Success -> KomodoStackList(
                        stacks = stacksState.data,
                        onStackSelected = onStackSelected
                    )
                    UiState.Loading, UiState.Idle -> Box(
                        modifier = Modifier.fillMaxWidth().height(240.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = ServiceType.KOMODO.primaryColor)
                    }
                    is UiState.Error -> ErrorScreen(
                        message = stacksState.message,
                        onRetry = stacksState.retryAction ?: onRefreshList
                    )
                    UiState.Offline -> ErrorScreen(
                        message = stringResource(R.string.error_network),
                        onRetry = onRefreshList,
                        isOffline = true
                    )
                }
            }
        }
    }
}

@Composable
private fun KomodoStackSheetTitle(onRefreshList: () -> Unit, onDismiss: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stringResource(R.string.komodo_stack_management),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = stringResource(R.string.komodo_stack_management_subtitle),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
        IconButton(onClick = onRefreshList) {
            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
        }
        IconButton(onClick = onDismiss) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
    }
}

@Composable
private fun KomodoStackList(stacks: List<KomodoStackItem>, onStackSelected: (KomodoStackItem) -> Unit) {
    if (stacks.isEmpty()) {
        Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
            Text(
                text = stringResource(R.string.komodo_no_stacks),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        return
    }
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        items(stacks, key = { it.id }) { stack ->
            KomodoStackRow(stack = stack, onClick = { onStackSelected(stack) })
        }
    }
}

@Composable
private fun KomodoStackRow(stack: KomodoStackItem, onClick: () -> Unit) {
    val statusColor = komodoStatusColor(stack.status)
    Surface(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.42f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.42f))
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = statusColor.copy(alpha = 0.14f)) {
                Icon(Icons.Default.Inventory2, contentDescription = null, tint = statusColor, modifier = Modifier.padding(8.dp).size(20.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = stack.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = listOfNotNull(stack.server, stack.project).joinToString(" · ").ifBlank { stack.id },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            KomodoStatusChip(label = stack.status, color = statusColor)
        }
    }
}

@Composable
private fun KomodoStackDetailHeader(
    detail: KomodoStackDetail,
    onBackToList: () -> Unit,
    onDismiss: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBackToList) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = detail.stack.name,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(detail.stack.server, detail.stack.project).joinToString(" · ").ifBlank {
                    stringResource(R.string.komodo_stack_services_count, detail.services.size)
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        KomodoStatusChip(label = detail.stack.status, color = komodoStatusColor(detail.stack.status))
        IconButton(onClick = onDismiss) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
        }
    }
}

@Composable
private fun KomodoStackActions(
    stackId: String,
    isRunningAction: Boolean,
    onAction: (String, KomodoStackAction) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.komodo_stack_actions),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KomodoActionButton(
                label = stringResource(R.string.komodo_deploy),
                icon = Icons.Default.CloudQueue,
                enabled = !isRunningAction,
                primary = true,
                modifier = Modifier.weight(1f),
                onClick = { onAction(stackId, KomodoStackAction.DEPLOY) }
            )
            KomodoActionButton(
                label = stringResource(R.string.komodo_start),
                icon = Icons.Default.PlayArrow,
                enabled = !isRunningAction,
                modifier = Modifier.weight(1f),
                onClick = { onAction(stackId, KomodoStackAction.START) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            KomodoActionButton(
                label = stringResource(R.string.komodo_stop),
                icon = Icons.Default.Stop,
                enabled = !isRunningAction,
                modifier = Modifier.weight(1f),
                onClick = { onAction(stackId, KomodoStackAction.STOP) }
            )
            KomodoActionButton(
                label = stringResource(R.string.komodo_restart),
                icon = Icons.Default.RestartAlt,
                enabled = !isRunningAction,
                modifier = Modifier.weight(1f),
                onClick = { onAction(stackId, KomodoStackAction.RESTART) }
            )
        }
    }
}

@Composable
private fun KomodoActionButton(
    label: String,
    icon: ImageVector,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    if (primary) {
        Button(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 44.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier.heightIn(min = 44.dp)) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(17.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun KomodoServicesList(services: List<KomodoStackService>) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.komodo_stack_services),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (services.isEmpty()) {
            Text(
                text = stringResource(R.string.komodo_no_stack_services),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 24.dp)
            )
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(bottom = 28.dp),
                modifier = Modifier.heightIn(max = 320.dp)
            ) {
                items(services, key = { it.name }) { service ->
                    KomodoServiceRow(service)
                }
            }
        }
    }
}

@Composable
private fun KomodoServiceRow(service: KomodoStackService) {
    val statusColor = komodoStatusColor(service.status)
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.36f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.36f))
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Box(
                modifier = Modifier.size(10.dp).background(statusColor, RoundedCornerShape(5.dp))
            )
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = service.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = service.image ?: service.containerName ?: "-",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (service.updateAvailable) {
                AssistChip(
                    onClick = {},
                    label = {
                        Text(
                            text = stringResource(R.string.komodo_update_available),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                )
            } else {
                KomodoStatusChip(label = service.status, color = statusColor)
            }
        }
    }
}

@Composable
private fun KomodoStatusChip(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = color.copy(alpha = 0.13f),
        border = BorderStroke(1.dp, color.copy(alpha = 0.36f))
    ) {
        Text(
            text = label.replaceFirstChar { it.uppercase() },
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun komodoStatusColor(status: String): Color {
    val normalized = status.lowercase()
    return when {
        "running" in normalized || "healthy" in normalized -> StatusGreen
        "paused" in normalized || "restarting" in normalized || "deploying" in normalized || "created" in normalized -> StatusOrange
        "stopped" in normalized || "down" in normalized || "dead" in normalized || "unhealthy" in normalized -> StatusRed
        else -> MaterialTheme.colorScheme.onSurfaceVariant
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
    val color: Color,
    val onClick: (() -> Unit)? = null
)
