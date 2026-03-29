package com.homelab.app.ui.dockhand

import android.content.ClipData
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.DockhandContainer
import com.homelab.app.data.repository.DockhandContainerAction
import com.homelab.app.data.repository.DockhandContainerDetail
import com.homelab.app.data.repository.DockhandContainerFilter
import com.homelab.app.data.repository.DockhandDashboardData
import com.homelab.app.data.repository.DockhandEnvironment
import com.homelab.app.data.repository.DockhandStack
import com.homelab.app.data.repository.DockhandStackAction
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DockhandRunningColor = Color(0xFF16A34A)
private val DockhandInfoColor = Color(0xFF4A90A4)
private val DockhandWarningColor = Color(0xFFE8A317)

private fun dockhandPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF0A0F13),
            Color(0xFF0E1418),
            accent.copy(alpha = 0.025f),
            Color(0xFF090D11)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFF4F8FE),
            Color(0xFFEDF5FE),
            accent.copy(alpha = 0.025f),
            Color(0xFFF8FBFF)
        )
    )
}

private fun dockhandCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF14181C) else Color(0xFFFFFFFF)

private fun dockhandRaisedCardColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF181D22) else Color(0xFFF8FAFC)

private fun dockhandSelectedCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.05f else 0.04f)
        .compositeOver(if (isDarkTheme) Color(0xFF181D22) else Color(0xFFF7FAFC))

private fun dockhandBorderColor(isDarkTheme: Boolean): Color =
    if (isDarkTheme) Color(0xFF2A3138) else Color(0xFFD8E2EC)

private fun dockhandSubtleAccentSurface(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.035f else 0.03f)
        .compositeOver(if (isDarkTheme) Color(0xFF161B21) else Color(0xFFF8FBFD))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DockhandDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: DockhandViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedEnvironmentId by viewModel.selectedEnvironmentId.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.containerFilter.collectAsStateWithLifecycle()
    val filteredContainers by viewModel.filteredContainers.collectAsStateWithLifecycle()
    val selectedContainerId by viewModel.selectedContainerId.collectAsStateWithLifecycle()
    val containerDetailState by viewModel.containerDetailState.collectAsStateWithLifecycle()
    val selectedStack by viewModel.selectedStack.collectAsStateWithLifecycle()
    val stackDetailState by viewModel.stackDetailState.collectAsStateWithLifecycle()
    val selectedScheduleId by viewModel.selectedScheduleId.collectAsStateWithLifecycle()
    val selectedSchedule by viewModel.selectedSchedule.collectAsStateWithLifecycle()
    val scheduleDetailState by viewModel.scheduleDetailState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRunningAction by viewModel.isRunningAction.collectAsStateWithLifecycle()
    val isSavingCompose by viewModel.isSavingCompose.collectAsStateWithLifecycle()
    val settings by viewModel.settingsUiState.collectAsStateWithLifecycle()

    var selectedTab by rememberSaveable { mutableStateOf(DockhandDashboardTab.OVERVIEW) }
    var showSettings by rememberSaveable { mutableStateOf(false) }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val accent = ServiceType.DOCKHAND.primaryColor
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pageBrush = remember(isDarkTheme) { dockhandPageBackground(isDarkTheme, accent) }
    val cardColor = remember(isDarkTheme) { dockhandCardColor(isDarkTheme) }
    val raisedCardColor = remember(isDarkTheme) { dockhandRaisedCardColor(isDarkTheme) }
    val selectedCardColor = remember(isDarkTheme) { dockhandSelectedCardColor(isDarkTheme, accent) }
    val borderColor = remember(isDarkTheme) { dockhandBorderColor(isDarkTheme) }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> viewModel.setScreenActive(true)
                Lifecycle.Event.ON_STOP -> viewModel.setScreenActive(false)
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        viewModel.setScreenActive(
            lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
        )
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.setScreenActive(false)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_dockhand),
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
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.dockhand_settings_title))
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
                .background(pageBrush)
        ) {
            when (val state = uiState) {
                UiState.Loading, UiState.Idle -> {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = accent)
                    }
                }

                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { state.retryAction?.invoke() ?: viewModel.fetchDashboard(forceLoading = true) },
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                UiState.Offline -> {
                    ErrorScreen(
                        message = "",
                        onRetry = { viewModel.fetchDashboard(forceLoading = true) },
                        isOffline = true,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                is UiState.Success -> {
                    DockhandDashboardContent(
                        data = state.data,
                        accent = accent,
                        instances = instances,
                        selectedInstanceId = viewModel.instanceId,
                        selectedEnvironmentId = selectedEnvironmentId,
                        selectedFilter = selectedFilter,
                        selectedTab = selectedTab,
                        settings = settings,
                        filteredContainers = filteredContainers,
                        cardColor = cardColor,
                        raisedCardColor = raisedCardColor,
                        selectedCardColor = selectedCardColor,
                        subtleAccentCardColor = remember(isDarkTheme) { dockhandSubtleAccentSurface(isDarkTheme, accent) },
                        borderColor = borderColor,
                        onSelectInstance = { instanceId ->
                            viewModel.setPreferredInstance(instanceId)
                            onNavigateToInstance(instanceId)
                        },
                        onSelectEnvironment = viewModel::selectEnvironment,
                        onSelectFilter = viewModel::selectFilter,
                        onSelectTab = { selectedTab = it },
                        onOpenContainer = viewModel::openContainerDetail,
                        onOpenStack = viewModel::openStackDetail,
                        onOpenSchedule = viewModel::openScheduleDetail,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }

            if (selectedContainerId != null) {
                ModalBottomSheet(onDismissRequest = viewModel::closeContainerDetail) {
                    DockhandContainerDetailSheet(
                        state = containerDetailState,
                        isRunningAction = isRunningAction,
                        onRefresh = { viewModel.refreshContainerDetail(forceLoading = false) },
                        onAction = viewModel::runContainerAction
                    )
                }
            }

            if (selectedStack != null) {
                ModalBottomSheet(onDismissRequest = viewModel::closeStackDetail) {
                    DockhandStackDetailSheet(
                        stack = selectedStack,
                        detailState = stackDetailState,
                        relatedContainers = (uiState as? UiState.Success)?.data?.containers.orEmpty(),
                        isRunningAction = isRunningAction,
                        isSavingCompose = isSavingCompose,
                        onRefresh = { viewModel.refreshStackDetail(forceLoading = false) },
                        onAction = viewModel::runStackAction,
                        onOpenContainer = { containerId ->
                            viewModel.closeStackDetail()
                            viewModel.openContainerDetail(containerId)
                        },
                        onSaveCompose = viewModel::saveStackCompose
                    )
                }
            }

            if (selectedScheduleId != null) {
                ModalBottomSheet(onDismissRequest = viewModel::closeScheduleDetail) {
                    DockhandScheduleDetailSheet(
                        schedule = selectedSchedule,
                        detailState = scheduleDetailState,
                        activityItems = (uiState as? UiState.Success)?.data?.activity.orEmpty(),
                        onRefresh = { viewModel.refreshScheduleDetail(forceLoading = false) }
                    )
                }
            }

            if (showSettings) {
                ModalBottomSheet(onDismissRequest = { showSettings = false }) {
                    DockhandSettingsSheet(
                        settings = settings,
                        cardColor = cardColor,
                        borderColor = borderColor,
                        onUpdateSettings = viewModel::updateSettings
                    )
                }
            }
        }
    }
}

@Composable
private fun DockhandDashboardContent(
    data: DockhandDashboardData,
    accent: Color,
    instances: List<com.homelab.app.domain.model.ServiceInstance>,
    selectedInstanceId: String,
    selectedEnvironmentId: String?,
    selectedFilter: DockhandContainerFilter,
    selectedTab: DockhandDashboardTab,
    settings: DockhandViewModel.DockhandSettingsUiState,
    filteredContainers: List<DockhandContainer>,
    cardColor: Color,
    raisedCardColor: Color,
    selectedCardColor: Color,
    subtleAccentCardColor: Color,
    borderColor: Color,
    onSelectInstance: (String) -> Unit,
    onSelectEnvironment: (String?) -> Unit,
    onSelectFilter: (DockhandContainerFilter) -> Unit,
    onSelectTab: (DockhandDashboardTab) -> Unit,
    onOpenContainer: (String) -> Unit,
    onOpenStack: (String) -> Unit,
    onOpenSchedule: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (instances.isNotEmpty()) {
            item {
                ServiceInstancePicker(
                    instances = instances,
                    selectedInstanceId = selectedInstanceId,
                    onInstanceSelected = { onSelectInstance(it.id) },
                    label = stringResource(R.string.dockhand_instance_label)
                )
            }
        }

        item {
            DockhandEnvironmentRow(
                environments = data.environments,
                selectedEnvironmentId = selectedEnvironmentId,
                cardColor = cardColor,
                borderColor = borderColor,
                onSelectEnvironment = onSelectEnvironment
            )
        }

        item {
            DockhandOverviewSection(
                data = data,
                accent = accent,
                selectedFilter = selectedFilter,
                cardColor = cardColor,
                selectedCardColor = selectedCardColor,
                borderColor = borderColor,
                onSelectFilter = {
                    onSelectFilter(it)
                    onSelectTab(DockhandDashboardTab.CONTAINERS)
                }
            )
        }

        item {
            DockhandTabSelector(
                selectedTab = selectedTab,
                filteredContainersCount = filteredContainers.size,
                stacksCount = data.stacks.size,
                activityCount = data.activity.size,
                schedulesCount = data.schedules.size,
                accent = accent,
                cardColor = cardColor,
                raisedCardColor = raisedCardColor,
                borderColor = borderColor,
                subtleAccentCardColor = subtleAccentCardColor,
                onSelectTab = onSelectTab
            )
        }

        when (selectedTab) {
            DockhandDashboardTab.OVERVIEW -> {
                item {
                    DockhandResourcesCard(
                        data = data,
                        cardColor = cardColor,
                        raisedCardColor = raisedCardColor,
                        borderColor = borderColor
                    )
                }

                item {
                    SectionTitle(title = stringResource(R.string.dockhand_containers), trailing = filteredContainers.size.toString())
                }

                val previewContainers = filteredContainers.take(3)
                if (previewContainers.isEmpty()) {
                    item { PlaceholderCard(text = stringResource(R.string.dockhand_no_containers), cardColor = cardColor, borderColor = borderColor) }
                } else {
                    items(previewContainers, key = { it.id }) { container ->
                        DockhandContainerCard(
                            container = container,
                            cardColor = cardColor,
                            defaultBorderColor = borderColor,
                            onClick = { onOpenContainer(container.id) }
                        )
                    }
                }

                item {
                    SectionTitle(title = stringResource(R.string.dockhand_stacks), trailing = data.stacks.size.toString())
                }

                val previewStacks = data.stacks.take(3)
                if (previewStacks.isEmpty()) {
                    item { PlaceholderCard(text = stringResource(R.string.dockhand_no_stacks), cardColor = cardColor, borderColor = borderColor) }
                } else {
                    items(previewStacks, key = { it.id }) { stack ->
                        DockhandStackCard(
                            stack = stack,
                            cardColor = cardColor,
                            defaultBorderColor = borderColor,
                            onClick = { onOpenStack(stack.name) }
                        )
                    }
                }
            }

            DockhandDashboardTab.CONTAINERS -> {
                item {
                    SectionTitle(title = stringResource(R.string.dockhand_containers), trailing = filteredContainers.size.toString())
                }

                if (filteredContainers.isEmpty()) {
                    item { PlaceholderCard(text = stringResource(R.string.dockhand_no_containers), cardColor = cardColor, borderColor = borderColor) }
                } else {
                    items(filteredContainers, key = { it.id }) { container ->
                        DockhandContainerCard(
                            container = container,
                            cardColor = cardColor,
                            defaultBorderColor = borderColor,
                            onClick = { onOpenContainer(container.id) }
                        )
                    }
                }
            }

            DockhandDashboardTab.STACKS -> {
                item {
                    SectionTitle(title = stringResource(R.string.dockhand_stacks), trailing = data.stacks.size.toString())
                }

                if (data.stacks.isEmpty()) {
                    item { PlaceholderCard(text = stringResource(R.string.dockhand_no_stacks), cardColor = cardColor, borderColor = borderColor) }
                } else {
                    items(data.stacks, key = { it.id }) { stack ->
                        DockhandStackCard(
                            stack = stack,
                            cardColor = cardColor,
                            defaultBorderColor = borderColor,
                            onClick = { onOpenStack(stack.name) }
                        )
                    }
                }
            }

            DockhandDashboardTab.ACTIVITY -> {
                val activityItems = data.activity.take(settings.activityLimit.coerceIn(5, 100))
                item {
                    SectionTitle(title = stringResource(R.string.dockhand_activity), trailing = data.activity.size.toString())
                }
                if (activityItems.isEmpty()) {
                    item { PlaceholderCard(text = stringResource(R.string.no_data), cardColor = cardColor, borderColor = borderColor) }
                } else {
                    items(activityItems, key = { it.id }) { item ->
                        DockhandActivityCard(
                            item = item,
                            showRaw = settings.showRawActivity,
                            cardColor = cardColor,
                            borderColor = borderColor
                        )
                    }
                }
            }

            DockhandDashboardTab.SCHEDULES -> {
                val schedules = data.schedules.take(20)
                item {
                    SectionTitle(title = stringResource(R.string.dockhand_schedules), trailing = data.schedules.size.toString())
                }
                if (schedules.isEmpty()) {
                    item { PlaceholderCard(text = stringResource(R.string.no_data), cardColor = cardColor, borderColor = borderColor) }
                } else {
                    items(schedules, key = { it.id }) { item ->
                        DockhandScheduleCard(
                            item = item,
                            cardColor = cardColor,
                            borderColor = borderColor,
                            onClick = { onOpenSchedule(item.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockhandOverviewSection(
    data: DockhandDashboardData,
    accent: Color,
    selectedFilter: DockhandContainerFilter,
    cardColor: Color,
    selectedCardColor: Color,
    borderColor: Color,
    onSelectFilter: (DockhandContainerFilter) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceIcon(
                    type = ServiceType.DOCKHAND,
                    size = 46.dp,
                    iconSize = 28.dp,
                    cornerRadius = 12.dp
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.home_summary_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DockhandStatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.dockhand_summary_total),
                    value = data.stats.totalContainers.toString(),
                    icon = Icons.Default.Widgets,
                    selected = selectedFilter == DockhandContainerFilter.ALL,
                    tint = accent,
                    cardColor = cardColor,
                    selectedCardColor = selectedCardColor,
                    defaultBorderColor = borderColor,
                    onClick = { onSelectFilter(DockhandContainerFilter.ALL) }
                )
                DockhandStatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.dockhand_summary_running),
                    value = data.stats.runningContainers.toString(),
                    icon = Icons.Default.PlayArrow,
                    selected = selectedFilter == DockhandContainerFilter.RUNNING,
                    tint = DockhandRunningColor,
                    cardColor = cardColor,
                    selectedCardColor = selectedCardColor,
                    defaultBorderColor = borderColor,
                    onClick = { onSelectFilter(DockhandContainerFilter.RUNNING) }
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                DockhandStatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.dockhand_summary_stopped),
                    value = data.stats.stoppedContainers.toString(),
                    icon = Icons.Default.Stop,
                    selected = selectedFilter == DockhandContainerFilter.STOPPED,
                    tint = DockhandWarningColor,
                    cardColor = cardColor,
                    selectedCardColor = selectedCardColor,
                    defaultBorderColor = borderColor,
                    onClick = { onSelectFilter(DockhandContainerFilter.STOPPED) }
                )
                DockhandStatCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.dockhand_summary_issues),
                    value = data.stats.issueContainers.toString(),
                    icon = Icons.Default.ErrorOutline,
                    selected = selectedFilter == DockhandContainerFilter.ISSUES,
                    tint = MaterialTheme.colorScheme.error,
                    cardColor = cardColor,
                    selectedCardColor = selectedCardColor,
                    defaultBorderColor = borderColor,
                    onClick = { onSelectFilter(DockhandContainerFilter.ISSUES) }
                )
            }
        }
    }
}

@Composable
private fun DockhandStatCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    icon: ImageVector,
    selected: Boolean,
    tint: Color,
    cardColor: Color,
    selectedCardColor: Color,
    defaultBorderColor: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = modifier
            .heightIn(min = 88.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (selected) selectedCardColor else cardColor,
        border = BorderStroke(1.dp, if (selected) tint.copy(alpha = 0.42f) else defaultBorderColor.copy(alpha = 0.88f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.16f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                    }
                }

                Text(
                    text = title,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DockhandTabSelector(
    selectedTab: DockhandDashboardTab,
    filteredContainersCount: Int,
    stacksCount: Int,
    activityCount: Int,
    schedulesCount: Int,
    accent: Color,
    cardColor: Color,
    raisedCardColor: Color,
    borderColor: Color,
    subtleAccentCardColor: Color,
    onSelectTab: (DockhandDashboardTab) -> Unit
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        maxItemsInEachRow = 3,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DockhandTabChip(
            modifier = Modifier.weight(1f),
            tab = DockhandDashboardTab.OVERVIEW,
            selected = selectedTab == DockhandDashboardTab.OVERVIEW,
            accent = accent,
            cardColor = raisedCardColor,
            selectedCardColor = subtleAccentCardColor,
            borderColor = borderColor,
            badge = null,
            onClick = { onSelectTab(DockhandDashboardTab.OVERVIEW) }
        )
        DockhandTabChip(
            modifier = Modifier.weight(1f),
            tab = DockhandDashboardTab.CONTAINERS,
            selected = selectedTab == DockhandDashboardTab.CONTAINERS,
            accent = accent,
            cardColor = raisedCardColor,
            selectedCardColor = subtleAccentCardColor,
            borderColor = borderColor,
            badge = filteredContainersCount.toString(),
            onClick = { onSelectTab(DockhandDashboardTab.CONTAINERS) }
        )
        DockhandTabChip(
            modifier = Modifier.weight(1f),
            tab = DockhandDashboardTab.STACKS,
            selected = selectedTab == DockhandDashboardTab.STACKS,
            accent = accent,
            cardColor = raisedCardColor,
            selectedCardColor = subtleAccentCardColor,
            borderColor = borderColor,
            badge = stacksCount.toString(),
            onClick = { onSelectTab(DockhandDashboardTab.STACKS) }
        )
        DockhandTabChip(
            modifier = Modifier.weight(1f),
            tab = DockhandDashboardTab.ACTIVITY,
            selected = selectedTab == DockhandDashboardTab.ACTIVITY,
            accent = accent,
            cardColor = raisedCardColor,
            selectedCardColor = subtleAccentCardColor,
            borderColor = borderColor,
            badge = activityCount.toString(),
            onClick = { onSelectTab(DockhandDashboardTab.ACTIVITY) }
        )
        DockhandTabChip(
            modifier = Modifier.weight(1f),
            tab = DockhandDashboardTab.SCHEDULES,
            selected = selectedTab == DockhandDashboardTab.SCHEDULES,
            accent = accent,
            cardColor = raisedCardColor,
            selectedCardColor = subtleAccentCardColor,
            borderColor = borderColor,
            badge = schedulesCount.toString(),
            onClick = { onSelectTab(DockhandDashboardTab.SCHEDULES) }
        )
    }
}

@Composable
private fun DockhandTabChip(
    tab: DockhandDashboardTab,
    selected: Boolean,
    accent: Color,
    cardColor: Color,
    selectedCardColor: Color,
    borderColor: Color,
    badge: String? = null,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val tint = when (tab) {
        DockhandDashboardTab.OVERVIEW -> accent
        DockhandDashboardTab.CONTAINERS -> DockhandRunningColor
        DockhandDashboardTab.STACKS -> DockhandInfoColor
        DockhandDashboardTab.ACTIVITY -> DockhandWarningColor
        DockhandDashboardTab.SCHEDULES -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) selectedCardColor else cardColor,
        border = BorderStroke(1.dp, if (selected) accent.copy(alpha = 0.28f) else borderColor.copy(alpha = 0.82f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 64.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = tab.icon(),
                    contentDescription = null,
                    tint = if (selected) accent else tint,
                    modifier = Modifier.size(16.dp)
                )

                Spacer(modifier = Modifier.weight(1f))

                if (badge != null) {
                    MiniPill(label = badge, tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Text(
                text = stringResource(tab.labelRes()),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

private enum class DockhandDashboardTab {
    OVERVIEW,
    CONTAINERS,
    STACKS,
    ACTIVITY,
    SCHEDULES;

    fun labelRes(): Int = when (this) {
        OVERVIEW -> R.string.home_summary_title
        CONTAINERS -> R.string.dockhand_containers
        STACKS -> R.string.dockhand_stacks
        ACTIVITY -> R.string.dockhand_activity
        SCHEDULES -> R.string.dockhand_schedules
    }

    fun icon(): ImageVector = when (this) {
        OVERVIEW -> Icons.Default.Widgets
        CONTAINERS -> Icons.Default.PlayArrow
        STACKS -> Icons.Default.Layers
        ACTIVITY -> Icons.Default.ErrorOutline
        SCHEDULES -> Icons.Default.Refresh
    }
}

@Composable
private fun DockhandEnvironmentRow(
    environments: List<DockhandEnvironment>,
    selectedEnvironmentId: String?,
    cardColor: Color,
    borderColor: Color,
    onSelectEnvironment: (String?) -> Unit
) {
    if (environments.isEmpty()) return

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = stringResource(R.string.dockhand_environments),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )

            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = { onSelectEnvironment(null) },
                    label = { Text(text = stringResource(R.string.all), maxLines = 1) },
                    enabled = selectedEnvironmentId != null
                )

                environments.take(4).forEach { env ->
                    AssistChip(
                        onClick = { onSelectEnvironment(env.id) },
                        label = {
                            Text(
                                text = env.name,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = {
                            if (env.isDefault) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            }
                        },
                        enabled = selectedEnvironmentId != env.id
                    )
                }
            }
        }
    }
}

@Composable
private fun DockhandContainerCard(
    container: DockhandContainer,
    cardColor: Color,
    defaultBorderColor: Color,
    onClick: () -> Unit
) {
    val issueColor = MaterialTheme.colorScheme.error
    val statusColor = when {
        container.isIssue -> issueColor
        container.isRunning -> DockhandRunningColor
        else -> DockhandWarningColor
    }
    val cardBorderColor = if (container.isIssue) {
        statusColor.copy(alpha = 0.35f)
    } else {
        defaultBorderColor
    }
    val healthColor = when {
        container.health.isNullOrBlank() -> null
        container.health.contains("unhealthy", true) || container.health.contains("fail", true) -> issueColor
        container.health.contains("start", true) -> DockhandWarningColor
        container.health.contains("healthy", true) -> DockhandRunningColor
        else -> MaterialTheme.colorScheme.tertiary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        border = BorderStroke(1.dp, cardBorderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = container.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Text(
                text = container.image,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniPill(label = container.state, tint = statusColor)
                if (!container.health.isNullOrBlank() && healthColor != null) {
                    MiniPill(label = container.health, tint = healthColor)
                }
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = container.portsSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun DockhandStackCard(
    stack: DockhandStack,
    cardColor: Color,
    defaultBorderColor: Color,
    onClick: () -> Unit
) {
    val running = stack.status.lowercase().contains("running") || stack.status.lowercase().contains("up")
    val tint = if (running) DockhandRunningColor else DockhandWarningColor
    val borderColor = if (running) {
        defaultBorderColor
    } else {
        tint.copy(alpha = 0.34f)
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Surface(
                shape = CircleShape,
                color = tint.copy(alpha = 0.15f),
                modifier = Modifier.size(28.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(text = stack.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(
                    text = stack.source?.takeIf { it.isNotBlank() } ?: stack.status,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            MiniPill(label = stack.status, tint = tint)
            Text(
                text = stack.services.toString(),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun DockhandResourcesCard(
    data: DockhandDashboardData,
    cardColor: Color,
    raisedCardColor: Color,
    borderColor: Color
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            SectionTitle(title = stringResource(R.string.dockhand_resources), trailing = null)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResourceTile(
                    label = stringResource(R.string.dockhand_images),
                    value = data.stats.images,
                    tint = ServiceType.DOCKHAND.primaryColor,
                    raisedCardColor = raisedCardColor,
                    borderColor = borderColor,
                    modifier = Modifier.weight(1f)
                )
                ResourceTile(
                    label = stringResource(R.string.dockhand_volumes),
                    value = data.stats.volumes,
                    tint = MaterialTheme.colorScheme.tertiary,
                    raisedCardColor = raisedCardColor,
                    borderColor = borderColor,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ResourceTile(
                    label = stringResource(R.string.dockhand_networks),
                    value = data.stats.networks,
                    tint = DockhandRunningColor,
                    raisedCardColor = raisedCardColor,
                    borderColor = borderColor,
                    modifier = Modifier.weight(1f)
                )
                ResourceTile(
                    label = stringResource(R.string.dockhand_stacks),
                    value = data.stats.stacks,
                    tint = DockhandWarningColor,
                    raisedCardColor = raisedCardColor,
                    borderColor = borderColor,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun ResourceTile(
    label: String,
    value: Int,
    tint: Color,
    raisedCardColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = raisedCardColor,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.88f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 72.dp)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = tint
            )
        }
    }
}

@Composable
private fun DockhandActivityCard(
    item: com.homelab.app.data.repository.DockhandActivityItem,
    showRaw: Boolean,
    cardColor: Color,
    borderColor: Color
) {
    val tint = when {
        item.status.contains("fail", true) || item.status.contains("error", true) || item.status.contains("die", true) || item.status.contains("kill", true) -> MaterialTheme.colorScheme.error
        item.status.contains("stop", true) || item.status.contains("created", true) || item.status.contains("pending", true) -> DockhandWarningColor
        else -> DockhandRunningColor
    }
    val categoryLabel = when {
        "${item.action} ${item.target}".contains("schedule", true) ||
            "${item.action} ${item.target}".contains("cron", true) ||
            "${item.action} ${item.target}".contains("cleanup", true) -> stringResource(R.string.dockhand_schedules)
        "${item.action} ${item.target}".contains("stack", true) ||
            "${item.action} ${item.target}".contains("compose", true) -> stringResource(R.string.dockhand_stacks)
        else -> stringResource(R.string.dockhand_containers)
    }
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.84f))
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = CircleShape,
                    color = tint.copy(alpha = 0.14f),
                    modifier = Modifier.size(28.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = when {
                                item.status.contains("fail", true) || item.status.contains("error", true) -> Icons.Default.ErrorOutline
                                item.action.contains("start", true) -> Icons.Default.PlayArrow
                                else -> Icons.Default.CheckCircle
                            },
                            contentDescription = null,
                            tint = tint,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = activityTitle(item, showRaw),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = activitySubtitle(item, showRaw),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                MiniPill(
                    label = formatActivityText(item.status, showRaw),
                    tint = tint
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                MiniPill(label = categoryLabel, tint = ServiceType.DOCKHAND.primaryColor)
                formatDockhandDate(item.createdAt)?.let { timestamp ->
                    Text(
                        text = timestamp,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun DockhandScheduleCard(
    item: com.homelab.app.data.repository.DockhandScheduleItem,
    cardColor: Color,
    borderColor: Color,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.84f)),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = item.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                MiniPill(
                    label = if (item.enabled) stringResource(R.string.home_status_online) else stringResource(R.string.dockhand_schedule_disabled),
                    tint = if (item.enabled) DockhandRunningColor else DockhandWarningColor
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!item.schedule.isNullOrBlank()) {
                Text(text = item.schedule, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!item.nextRun.isNullOrBlank()) {
                Text(
                    text = stringResource(
                        R.string.dockhand_label_next_at,
                        formatDockhandDate(item.nextRun) ?: item.nextRun
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DockhandSettingsSheet(
    settings: DockhandViewModel.DockhandSettingsUiState,
    cardColor: Color,
    borderColor: Color,
    onUpdateSettings: (update: (DockhandViewModel.DockhandSettingsUiState) -> DockhandViewModel.DockhandSettingsUiState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text(
            text = stringResource(R.string.dockhand_settings_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.dockhand_settings_refresh_section), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.dockhand_settings_auto_refresh),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.autoRefreshEnabled,
                        onCheckedChange = { value ->
                            onUpdateSettings { current ->
                                current.copy(autoRefreshEnabled = value)
                            }
                        }
                    )
                }

                Text(
                    text = stringResource(R.string.dockhand_settings_refresh_interval),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(15, 30, 45, 60, 120).forEach { seconds ->
                        FilterChip(
                            selected = settings.refreshIntervalSeconds == seconds,
                            onClick = {
                                onUpdateSettings { current ->
                                    current.copy(refreshIntervalSeconds = seconds)
                                }
                            },
                            enabled = settings.autoRefreshEnabled,
                            label = { Text("${seconds}s", maxLines = 1) }
                        )
                    }
                }
            }
        }

        Surface(
            shape = RoundedCornerShape(12.dp),
            color = cardColor,
            border = BorderStroke(1.dp, borderColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.dockhand_settings_data_section), style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)

                Text(
                    text = stringResource(R.string.dockhand_settings_activity_limit),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(10, 20, 50, 100).forEach { limit ->
                        FilterChip(
                            selected = settings.activityLimit == limit,
                            onClick = {
                                onUpdateSettings { current ->
                                    current.copy(activityLimit = limit)
                                }
                            },
                            label = { Text(limit.toString()) }
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        stringResource(R.string.dockhand_settings_show_raw_activity),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Switch(
                        checked = settings.showRawActivity,
                        onCheckedChange = { value ->
                            onUpdateSettings { current ->
                                current.copy(showRawActivity = value)
                            }
                        }
                    )
                }
            }
        }
    }
}

private fun formatActivityText(raw: String, showRaw: Boolean): String {
    if (showRaw) return raw
    val cleaned = raw
        .replace('_', ' ')
        .replace(':', ' ')
        .replace('-', ' ')
        .trim()
    if (cleaned.isBlank()) return raw
    return cleaned
        .split(Regex("\\s+"))
        .joinToString(" ") { token ->
            token.lowercase().replaceFirstChar { it.uppercase() }
        }
}

private fun dockhandDetailMap(details: List<Pair<String, String>>): Map<String, String> =
    buildMap {
        details.forEach { (key, value) ->
            putIfAbsent(key.lowercase(), value)
        }
    }

private fun compactDockhandValue(raw: String, limit: Int = 180): String {
    val normalized = raw
        .replace('\n', ' ')
        .trim()
    return if (normalized.length <= limit) normalized else normalized.take(limit) + "..."
}

private fun compactDockhandHeadlineValue(raw: String, limit: Int = 44): String {
    val normalized = raw.trim()
    if (normalized.isBlank()) return "-"
    if (normalized.length <= limit) return normalized
    return normalized.take(limit - 1) + "…"
}

private fun dockhandLabel(context: Context, key: String): String = when (key.lowercase()) {
    "state" -> context.getString(R.string.dockhand_state)
    "status" -> context.getString(R.string.dockhand_status)
    "ports" -> context.getString(R.string.dockhand_ports)
    "health" -> context.getString(R.string.dockhand_health)
    "created", "createdat" -> context.getString(R.string.dockhand_label_created)
    "platform" -> context.getString(R.string.dockhand_label_platform)
    "runtime" -> context.getString(R.string.dockhand_label_runtime)
    "driver" -> context.getString(R.string.dockhand_label_driver)
    "restartpolicy" -> context.getString(R.string.dockhand_label_restart_policy)
    "networkmode" -> context.getString(R.string.dockhand_label_network_mode)
    "command" -> context.getString(R.string.dockhand_label_command)
    "entrypoint" -> context.getString(R.string.dockhand_label_entrypoint)
    "services" -> context.getString(R.string.dockhand_label_services)
    "source" -> context.getString(R.string.dockhand_label_source)
    "sourcetype" -> context.getString(R.string.dockhand_label_source_type)
    "environment", "environmentid" -> context.getString(R.string.dockhand_label_environment)
    "schedule", "cronexpression" -> context.getString(R.string.dockhand_label_schedule)
    "nextrun" -> context.getString(R.string.dockhand_label_next_run)
    "lastrun", "lastexecution" -> context.getString(R.string.dockhand_label_last_run)
    "description" -> context.getString(R.string.dockhand_label_description)
    "entityname" -> context.getString(R.string.dockhand_label_entity)
    "issystem" -> context.getString(R.string.dockhand_label_system)
    "enabled" -> context.getString(R.string.dockhand_label_enabled)
    "recentexecutions" -> context.getString(R.string.dockhand_label_recent_runs)
    "id" -> context.getString(R.string.dockhand_label_id)
    else -> formatActivityText(key, showRaw = false)
}

private fun formatDockhandDate(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    if (value.startsWith("{") || value.startsWith("[")) return null
    return runCatching {
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm")
            .withZone(ZoneId.systemDefault())
            .format(Instant.parse(value))
    }.getOrElse {
        if (value.contains('T')) value else null
    }
}

private fun dockhandBoolLabel(raw: String?, onlineLabel: String, disabledLabel: String): String? {
    return when (raw?.trim()?.lowercase()) {
        "1", "true", "yes" -> onlineLabel
        "0", "false", "no" -> disabledLabel
        else -> null
    }
}

private fun dockhandExecutionSummary(raw: String?): String? {
    val value = raw?.trim().orEmpty()
    if (value.isBlank()) return null
    val completedCount = "completedAt".toRegex().findAll(value).count()
    return if (completedCount > 0) completedCount.toString() else compactDockhandValue(value, limit = 80)
}

private fun activityTitle(item: com.homelab.app.data.repository.DockhandActivityItem, showRaw: Boolean): String {
    val target = formatActivityText(item.target, showRaw)
    return if (target.isNotBlank() && target != "-") target else formatActivityText(item.action, showRaw)
}

private fun activitySubtitle(item: com.homelab.app.data.repository.DockhandActivityItem, showRaw: Boolean): String {
    val action = formatActivityText(item.action, showRaw)
    val status = formatActivityText(item.status, showRaw)
    return if (action.equals(status, ignoreCase = true)) action else "$action • $status"
}

@Composable
private fun DockhandContainerDetailSheet(
    state: UiState<DockhandContainerDetail>,
    isRunningAction: Boolean,
    onRefresh: () -> Unit,
    onAction: (DockhandContainerAction) -> Unit
) {
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()
    var showFullLogs by remember(state) { mutableStateOf(false) }
    var showAdvancedDetails by remember(state) { mutableStateOf(false) }
    val contentScroll = rememberScrollState()
    when (state) {
        UiState.Loading, UiState.Idle -> {
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 260.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = state.message, style = MaterialTheme.typography.bodyMedium)
                Button(onClick = { state.retryAction?.invoke() ?: onRefresh() }) {
                    Text(stringResource(R.string.retry))
                }
            }
        }
        UiState.Offline -> {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = stringResource(R.string.error_server_unreachable))
                Button(onClick = onRefresh) { Text(stringResource(R.string.retry)) }
            }
        }
        is UiState.Success -> {
            val detail = state.data
            val stateLabel = dockhandLabel(context, "state")
            val statusLabel = dockhandLabel(context, "status")
            val portsLabel = dockhandLabel(context, "ports")
            val healthLabel = dockhandLabel(context, "health")
            val detailMap = dockhandDetailMap(detail.rawDetails)
            val overviewRows = buildList {
                add(stateLabel to detail.container.state)
                add(statusLabel to detail.container.status)
                add(portsLabel to detail.container.portsSummary)
                detail.container.health?.takeIf { it.isNotBlank() }?.let {
                    add(healthLabel to it)
                }
                detailMap["created"]?.takeIf { it.isNotBlank() }?.let {
                    add(dockhandLabel(context, "created") to (formatDockhandDate(it) ?: compactDockhandValue(it)))
                }
                detailMap["platform"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "platform") to compactDockhandValue(it)) }
                detailMap["runtime"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "runtime") to compactDockhandValue(it)) }
                detailMap["driver"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "driver") to compactDockhandValue(it)) }
                detailMap["restartpolicy"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "restartpolicy") to compactDockhandValue(it)) }
                detailMap["networkmode"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "networkmode") to compactDockhandValue(it)) }
                detailMap["command"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "command") to compactDockhandValue(it)) }
                detailMap["entrypoint"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "entrypoint") to compactDockhandValue(it)) }
            }
            val primaryRows = overviewRows.take(6)
            val secondaryRows = overviewRows.drop(6)
            val logLines = detail.logs.lines()
            val displayLogs = if (showFullLogs || logLines.size <= 18) {
                detail.logs
            } else {
                logLines.take(18).joinToString("\n")
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 740.dp)
                    .verticalScroll(contentScroll)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(detail.container.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        compactDockhandHeadlineValue(detail.container.image),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                DockhandActionButtons(
                    isBusy = isRunningAction,
                    onRefresh = onRefresh,
                    onStart = { onAction(DockhandContainerAction.START) },
                    onStop = { onAction(DockhandContainerAction.STOP) },
                    onRestart = { onAction(DockhandContainerAction.RESTART) }
                )

                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(title = stringResource(R.string.dockhand_details), trailing = overviewRows.size.toString())

                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniPill(
                        label = detail.container.state,
                        tint = if (detail.container.isIssue) MaterialTheme.colorScheme.error else DockhandRunningColor
                    )
                    MiniPill(label = detail.container.status, tint = DockhandWarningColor)
                    if (!detail.container.health.isNullOrBlank()) {
                        MiniPill(label = detail.container.health, tint = DockhandRunningColor)
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                DockhandDetailGrid(rows = primaryRows)

                if (secondaryRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showAdvancedDetails = !showAdvancedDetails }) {
                        Text(
                            if (showAdvancedDetails) {
                                stringResource(R.string.dockhand_hide_extra_details)
                            } else {
                                stringResource(R.string.dockhand_show_more_details)
                            }
                        )
                    }
                }

                if (showAdvancedDetails && secondaryRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DockhandDetailGrid(rows = secondaryRows)
                }

                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(
                    title = stringResource(R.string.dockhand_logs),
                    trailing = if (detail.logs.isNotBlank()) logLines.size.toString() else null
                )

                Spacer(modifier = Modifier.height(8.dp))
                DockhandLogViewer(
                    logs = detail.logs,
                    displayLogs = displayLogs,
                    lineCount = logLines.size,
                    onCopy = {
                        clipboardScope.launch {
                            clipboard.setClipEntry(
                                ClipEntry(
                                    ClipData.newPlainText(
                                        context.getString(R.string.copy),
                                        detail.logs
                                    )
                                )
                            )
                        }
                        Toast.makeText(context, context.getString(R.string.copy), Toast.LENGTH_SHORT).show()
                    }
                )

                if (logLines.size > 18) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showFullLogs = !showFullLogs }) {
                        Text(
                            if (showFullLogs) {
                                stringResource(R.string.dockhand_show_less_logs)
                            } else {
                                stringResource(R.string.dockhand_show_full_logs)
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DockhandStackDetailSheet(
    stack: DockhandStack?,
    detailState: UiState<com.homelab.app.data.repository.DockhandStackDetail>,
    relatedContainers: List<DockhandContainer>,
    isRunningAction: Boolean,
    isSavingCompose: Boolean,
    onRefresh: () -> Unit,
    onAction: (DockhandStackAction) -> Unit,
    onOpenContainer: (String) -> Unit,
    onSaveCompose: (String) -> Unit
) {
    if (stack == null) return
    val clipboard = LocalClipboard.current
    val context = LocalContext.current
    val clipboardScope = rememberCoroutineScope()
    var isEditingCompose by rememberSaveable(stack.name) { mutableStateOf(false) }
    var composeDraft by remember(stack.name) { mutableStateOf("") }
    var showAdvancedDetails by rememberSaveable(stack.name) { mutableStateOf(false) }
    val contentScroll = rememberScrollState()
    val stackContainers = remember(stack.name, stack.environmentId, relatedContainers) {
        val stackNeedle = stack.name.lowercase()
        relatedContainers
            .filter { container ->
                val envMatches = stack.environmentId.isNullOrBlank() ||
                    container.environmentId.isNullOrBlank() ||
                    container.environmentId == stack.environmentId
                val loweredName = container.name.lowercase()
                envMatches && (loweredName.startsWith("${stackNeedle}_") || loweredName.contains(stackNeedle))
            }
            .sortedWith(compareByDescending<DockhandContainer> { it.isRunning }.thenBy { it.name.lowercase() })
            .take(8)
    }

    LaunchedEffect(detailState, isEditingCompose) {
        if (!isEditingCompose && detailState is UiState.Success) {
            composeDraft = detailState.data.compose
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 760.dp)
            .verticalScroll(contentScroll)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(text = stack.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = compactDockhandHeadlineValue(stack.source?.takeIf { it.isNotBlank() } ?: stack.status),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))
        DockhandActionButtons(
            isBusy = isRunningAction || isSavingCompose,
            onRefresh = onRefresh,
            onStart = { onAction(DockhandStackAction.START) },
            onStop = { onAction(DockhandStackAction.STOP) },
            onRestart = { onAction(DockhandStackAction.RESTART) }
        )

        when (detailState) {
            UiState.Loading, UiState.Idle -> {
                Spacer(modifier = Modifier.height(12.dp))
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = detailState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            UiState.Offline -> {
                Spacer(modifier = Modifier.height(12.dp))
                Text(text = stringResource(R.string.error_server_unreachable))
            }
            is UiState.Success -> {
                val statusLabel = dockhandLabel(context, "status")
                val detailMap = dockhandDetailMap(detailState.data.rawDetails)
                val detailRows = buildList {
                    add(statusLabel to detailState.data.stack.status)
                    add(dockhandLabel(context, "services") to detailState.data.stack.services.toString())
                    detailState.data.stack.source?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "source") to compactDockhandValue(it)) }
                    detailMap["sourcetype"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "sourcetype") to compactDockhandValue(it)) }
                    detailState.data.stack.environmentId?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "environment") to it) }
                    detailMap["id"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "id") to compactDockhandValue(it)) }
                }
                val primaryRows = detailRows.take(4)
                val secondaryRows = detailRows.drop(4)
                val canEditCompose = composeIsEditable(detailState.data.compose)
                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(title = stringResource(R.string.dockhand_details), trailing = detailRows.size.toString())
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    MiniPill(label = detailState.data.stack.status, tint = if (detailState.data.stack.status.contains("run", true) || detailState.data.stack.status.contains("up", true)) DockhandRunningColor else DockhandWarningColor)
                    MiniPill(
                        label = "${detailState.data.stack.services} ${stringResource(R.string.dockhand_containers)}",
                        tint = ServiceType.DOCKHAND.primaryColor
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                DockhandDetailGrid(rows = primaryRows)
                if (secondaryRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedButton(onClick = { showAdvancedDetails = !showAdvancedDetails }) {
                        Text(
                            if (showAdvancedDetails) {
                                stringResource(R.string.dockhand_hide_extra_details)
                            } else {
                                stringResource(R.string.dockhand_show_more_details)
                            }
                        )
                    }
                }
                if (showAdvancedDetails && secondaryRows.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    DockhandDetailGrid(rows = secondaryRows)
                }

                if (stackContainers.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    SectionTitle(
                        title = stringResource(R.string.portainer_containers),
                        trailing = stackContainers.size.toString()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    stackContainers.forEach { container ->
                        val tint = when {
                            container.isIssue -> MaterialTheme.colorScheme.error
                            container.isRunning -> DockhandRunningColor
                            else -> DockhandWarningColor
                        }
                        Surface(
                            onClick = { onOpenContainer(container.id) },
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            border = BorderStroke(1.dp, tint.copy(alpha = if (container.isIssue) 0.35f else 0.2f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = container.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = container.image,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                MiniPill(label = container.state, tint = tint)
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                SectionTitle(title = stringResource(R.string.portainer_compose), trailing = null)
                if (isEditingCompose) {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = composeDraft,
                        onValueChange = { composeDraft = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 180.dp),
                        maxLines = 18
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = {
                                isEditingCompose = false
                                composeDraft = detailState.data.compose
                            },
                            enabled = !isSavingCompose,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.cancel))
                        }
                        Button(
                            onClick = {
                                onSaveCompose(composeDraft)
                                isEditingCompose = false
                            },
                            enabled = !isSavingCompose && composeDraft.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.save))
                        }
                    }
                } else {
                    Spacer(modifier = Modifier.height(8.dp))
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (detailState.data.compose.isNotBlank()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    MiniPill(label = "YAML", tint = ServiceType.DOCKHAND.primaryColor)
                                    OutlinedButton(
                                        onClick = {
                                            clipboardScope.launch {
                                                clipboard.setClipEntry(
                                                    ClipEntry(
                                                        ClipData.newPlainText(
                                                            context.getString(R.string.copy),
                                                            detailState.data.compose
                                                        )
                                                    )
                                                )
                                            }
                                            Toast.makeText(context, context.getString(R.string.copy), Toast.LENGTH_SHORT).show()
                                        }
                                    ) {
                                        Text(stringResource(R.string.copy))
                                    }
                                }
                            }
                            val composeScroll = rememberScrollState()
                            SelectionContainer {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 160.dp, max = 280.dp)
                                        .verticalScroll(composeScroll)
                                ) {
                                    Text(
                                        text = detailState.data.compose.ifBlank { stringResource(R.string.not_available) },
                                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                    if (canEditCompose) {
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedButton(onClick = { isEditingCompose = true }, enabled = !isSavingCompose) {
                            Text(stringResource(R.string.edit))
                        }
                    }
                }
            }
        }
    }
}

private fun composeIsEditable(compose: String): Boolean {
    val normalized = compose.trim()
    if (normalized.isEmpty()) return false
    val lowered = normalized.lowercase()
    if (lowered == "-" || lowered == "n/a") return false
    if (lowered.startsWith("compose not available")) return false
    return true
}

@Composable
private fun DockhandScheduleDetailSheet(
    schedule: com.homelab.app.data.repository.DockhandScheduleItem?,
    detailState: UiState<com.homelab.app.data.repository.DockhandScheduleDetail>,
    activityItems: List<com.homelab.app.data.repository.DockhandActivityItem>,
    onRefresh: () -> Unit
) {
    if (schedule == null) return
    val context = LocalContext.current
    val onlineLabel = stringResource(R.string.home_status_online)
    val disabledLabel = stringResource(R.string.dockhand_schedule_disabled)
    val relatedActivity = remember(schedule.id, schedule.name, activityItems) {
        val idNeedle = schedule.id.lowercase()
        val nameNeedle = schedule.name.lowercase()
        activityItems.filter { item ->
            val haystack = "${item.action} ${item.target}".lowercase()
            (nameNeedle.isNotBlank() && haystack.contains(nameNeedle)) ||
                (idNeedle.isNotBlank() && haystack.contains(idNeedle))
        }.take(6)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 560.dp),
        contentPadding = PaddingValues(horizontal = 18.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(text = schedule.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        }
        if (!schedule.schedule.isNullOrBlank()) {
            item {
                Text(
                    text = schedule.schedule,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MiniPill(
                    label = if (schedule.enabled) stringResource(R.string.home_status_online) else stringResource(R.string.dockhand_schedule_disabled),
                    tint = if (schedule.enabled) DockhandRunningColor else DockhandWarningColor
                )
                if (!schedule.nextRun.isNullOrBlank()) {
                    MiniPill(
                        label = stringResource(
                            R.string.dockhand_label_next_at,
                            formatDockhandDate(schedule.nextRun) ?: schedule.nextRun
                        ),
                        tint = ServiceType.DOCKHAND.primaryColor
                    )
                }
                if (!schedule.lastRun.isNullOrBlank()) {
                    MiniPill(
                        label = stringResource(
                            R.string.dockhand_label_last_at,
                            formatDockhandDate(schedule.lastRun) ?: schedule.lastRun
                        ),
                        tint = DockhandWarningColor
                    )
                }
            }
        }
        item {
            OutlinedButton(onClick = onRefresh) {
                Icon(Icons.Default.Refresh, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.refresh))
            }
        }

        when (detailState) {
            UiState.Loading, UiState.Idle -> item {
                Box(modifier = Modifier.fillMaxWidth().height(120.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> item {
                Text(
                    text = detailState.message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            UiState.Offline -> item { Text(text = stringResource(R.string.error_server_unreachable)) }
            is UiState.Success -> {
                val statusLabel = dockhandLabel(context, "status")
                val detailMap = dockhandDetailMap(detailState.data.rawDetails)
                val detailRows = buildList {
                    add(statusLabel to if (schedule.enabled) onlineLabel else disabledLabel)
                    (schedule.schedule ?: detailMap["cronexpression"] ?: detailMap["schedule"])
                        ?.takeIf { it.isNotBlank() }
                        ?.let { add(dockhandLabel(context, "schedule") to compactDockhandValue(it)) }
                    (formatDockhandDate(schedule.nextRun) ?: formatDockhandDate(detailMap["nextrun"]))
                        ?.let { add(dockhandLabel(context, "nextrun") to it) }
                    (formatDockhandDate(schedule.lastRun) ?: formatDockhandDate(detailMap["lastexecution"]))
                        ?.let { add(dockhandLabel(context, "lastrun") to it) }
                    detailMap["description"]?.takeIf { it.isNotBlank() }?.let { add(dockhandLabel(context, "description") to compactDockhandValue(it)) }
                    detailMap["entityname"]?.takeIf { it.isNotBlank() && !it.equals(schedule.name, ignoreCase = true) }?.let { add(dockhandLabel(context, "entityname") to compactDockhandValue(it)) }
                    dockhandBoolLabel(detailMap["issystem"], onlineLabel, disabledLabel)
                        ?.let { add(dockhandLabel(context, "issystem") to it) }
                    dockhandExecutionSummary(detailMap["recentexecutions"])
                        ?.let { add(dockhandLabel(context, "recentexecutions") to it) }
                }

                item { SectionTitle(title = stringResource(R.string.dockhand_details), trailing = detailRows.size.toString()) }
                items(detailRows, key = { it.first }) { (key, value) ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.Top) {
                            Text(
                                text = key,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.weight(0.35f)
                            )
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(0.65f)
                            )
                        }
                    }
                }
            }
        }

        if (relatedActivity.isNotEmpty()) {
            item { SectionTitle(title = stringResource(R.string.dockhand_activity), trailing = relatedActivity.size.toString()) }
            items(relatedActivity, key = { it.id }) { item ->
                DockhandActivityCard(
                    item = item,
                    showRaw = false,
                    cardColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    borderColor = ServiceType.DOCKHAND.primaryColor.copy(alpha = 0.16f)
                )
            }
        }
    }
}

@Composable
private fun DockhandActionButtons(
    isBusy: Boolean,
    onRefresh: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRestart: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockhandActionButton(
                label = stringResource(R.string.refresh),
                icon = Icons.Default.Refresh,
                tint = ServiceType.DOCKHAND.primaryColor,
                enabled = !isBusy,
                onClick = onRefresh,
                modifier = Modifier.weight(1f)
            )
            DockhandActionButton(
                label = stringResource(R.string.dockhand_action_start),
                icon = Icons.Default.PlayArrow,
                tint = DockhandRunningColor,
                enabled = !isBusy,
                onClick = onStart,
                modifier = Modifier.weight(1f)
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DockhandActionButton(
                label = stringResource(R.string.dockhand_action_restart),
                icon = Icons.Default.Refresh,
                tint = DockhandInfoColor,
                enabled = !isBusy,
                onClick = onRestart,
                modifier = Modifier.weight(1f)
            )
            DockhandActionButton(
                label = stringResource(R.string.dockhand_action_stop),
                icon = Icons.Default.Stop,
                tint = DockhandWarningColor,
                enabled = !isBusy,
                onClick = onStop,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DockhandActionButton(
    label: String,
    icon: ImageVector,
    tint: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 52.dp),
        border = BorderStroke(1.dp, tint.copy(alpha = 0.34f))
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp), tint = tint)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = label,
            maxLines = 2,
            textAlign = TextAlign.Center,
            color = tint
        )
    }
}

@Composable
private fun DockhandDetailValueCard(label: String, value: String) {
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SelectionContainer {
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

@Composable
private fun DockhandDetailGrid(rows: List<Pair<String, String>>) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.chunked(2).forEach { chunk ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                chunk.forEach { (label, value) ->
                    Box(modifier = Modifier.weight(1f)) {
                        DockhandDetailValueCard(label = label, value = value)
                    }
                }
                if (chunk.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DockhandLogViewer(
    logs: String,
    displayLogs: String,
    lineCount: Int,
    onCopy: () -> Unit
) {
    val logScroll = rememberScrollState()
    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (logs.isNotBlank()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MiniPill(label = lineCount.toString(), tint = ServiceType.DOCKHAND.primaryColor)
                    OutlinedButton(onClick = onCopy) {
                        Text(stringResource(R.string.copy))
                    }
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 96.dp, max = 176.dp)
                    .verticalScroll(logScroll)
            ) {
                SelectionContainer {
                    Text(
                        text = displayLogs.ifBlank { stringResource(R.string.not_available) },
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }
}

@Composable
private fun MiniPill(label: String, tint: Color) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.13f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = tint,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SectionTitle(title: String, trailing: String?) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        if (trailing != null) {
            Text(
                text = trailing,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PlaceholderCard(text: String, cardColor: Color, borderColor: Color) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = cardColor,
        border = BorderStroke(1.dp, borderColor.copy(alpha = 0.84f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.NetworkCheck, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = text, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
