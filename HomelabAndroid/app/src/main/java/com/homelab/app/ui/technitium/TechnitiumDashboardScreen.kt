package com.homelab.app.ui.technitium

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.LockClock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.TravelExplore
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.TechnitiumDashboardData
import com.homelab.app.data.repository.TechnitiumLogFile
import com.homelab.app.data.repository.TechnitiumStatsRange
import com.homelab.app.data.repository.TechnitiumTopClient
import com.homelab.app.data.repository.TechnitiumTopDomain
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.text.NumberFormat
import java.util.Locale
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.max

private fun technitiumPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF0A1015),
            Color(0xFF0E161D),
            accent.copy(alpha = 0.04f),
            Color(0xFF0A1117)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFF7FAFD),
            Color(0xFFF2F7FB),
            accent.copy(alpha = 0.025f),
            Color(0xFFF8FBFE)
        )
    )
}

private fun technitiumCardColor(isDarkTheme: Boolean, accent: Color? = null): Color {
    val neutral = if (isDarkTheme) Color(0xFF121820) else Color(0xFFF3F7FB)
    val tintAmount = if (isDarkTheme) 0.09f else 0.05f
    return accent?.let { lerp(neutral, it, tintAmount) } ?: neutral
}

private fun technitiumRaisedCardColor(isDarkTheme: Boolean, accent: Color? = null): Color {
    val neutral = if (isDarkTheme) Color(0xFF18202A) else Color(0xFFF8FBFE)
    val tintAmount = if (isDarkTheme) 0.065f else 0.04f
    return accent?.let { lerp(neutral, it, tintAmount) } ?: neutral
}

private fun technitiumBorderColor(isDarkTheme: Boolean, accent: Color? = null): Color {
    val neutral = if (isDarkTheme) Color(0xFF2D3B4C) else Color(0xFFC5D6E7)
    val tintAmount = if (isDarkTheme) 0.16f else 0.10f
    return accent?.let { lerp(neutral, it, tintAmount) } ?: neutral
}

private fun technitiumTrackColor(isDarkTheme: Boolean, accent: Color? = null): Color {
    val neutral = if (isDarkTheme) Color(0xFF243140) else Color(0xFFD9E5F1)
    val tintAmount = if (isDarkTheme) 0.09f else 0.05f
    return accent?.let { lerp(neutral, it, tintAmount) } ?: neutral
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TechnitiumDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: TechnitiumViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val selectedRange by viewModel.selectedRange.collectAsStateWithLifecycle()
    val selectedPanel by viewModel.selectedPanel.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isRunningAction by viewModel.isRunningAction.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()

    var showDisableDialog by rememberSaveable { mutableStateOf(false) }
    var showCustomDisableDialog by rememberSaveable { mutableStateOf(false) }
    var customDisableMinutes by rememberSaveable { mutableStateOf("15") }
    var showAddDomainDialog by rememberSaveable { mutableStateOf(false) }
    var domainToAdd by rememberSaveable { mutableStateOf("") }

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    val accent = ServiceType.TECHNITIUM.primaryColor
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pageBrush = remember(isDarkTheme) { technitiumPageBackground(isDarkTheme, accent) }
    val cardColor = remember(isDarkTheme) { technitiumCardColor(isDarkTheme, accent) }
    val raisedCardColor = remember(isDarkTheme) { technitiumRaisedCardColor(isDarkTheme, accent) }
    val borderColor = remember(isDarkTheme) { technitiumBorderColor(isDarkTheme, accent) }
    val trackColor = remember(isDarkTheme) { technitiumTrackColor(isDarkTheme, accent) }

    if (showDisableDialog) {
        AlertDialog(
            onDismissRequest = { showDisableDialog = false },
            title = { Text(stringResource(R.string.technitium_disable_dialog_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = {
                            showDisableDialog = false
                            viewModel.temporaryDisable(minutes = 5)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.technitium_disable_5m))
                    }
                    TextButton(
                        onClick = {
                            showDisableDialog = false
                            viewModel.temporaryDisable(minutes = 30)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.technitium_disable_30m))
                    }
                    TextButton(
                        onClick = {
                            showDisableDialog = false
                            showCustomDisableDialog = true
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.technitium_disable_custom))
                    }
                    TextButton(
                        onClick = {
                            showDisableDialog = false
                            viewModel.setBlocking(false)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.technitium_disable_until_manual),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDisableDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showCustomDisableDialog) {
        AlertDialog(
            onDismissRequest = { showCustomDisableDialog = false },
            title = { Text(stringResource(R.string.technitium_disable_custom)) },
            text = {
                OutlinedTextField(
                    value = customDisableMinutes,
                    onValueChange = { customDisableMinutes = it.filter(Char::isDigit).take(3) },
                    label = { Text(stringResource(R.string.pihole_custom_disable_minutes)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = customDisableMinutes.toIntOrNull()?.let { it > 0 } == true,
                    onClick = {
                        val minutes = customDisableMinutes.toIntOrNull() ?: 0
                        if (minutes > 0) {
                            viewModel.temporaryDisable(minutes = minutes)
                        }
                        showCustomDisableDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomDisableDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    if (showAddDomainDialog) {
        AlertDialog(
            onDismissRequest = { showAddDomainDialog = false },
            title = { Text(stringResource(R.string.technitium_add_blocked_domain)) },
            text = {
                OutlinedTextField(
                    value = domainToAdd,
                    onValueChange = { domainToAdd = it },
                    label = { Text(stringResource(R.string.pihole_domain_placeholder)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    enabled = domainToAdd.trim().isNotEmpty(),
                    onClick = {
                        viewModel.addBlockedDomain(domainToAdd.trim())
                        domainToAdd = ""
                        showAddDomainDialog = false
                    }
                ) {
                    Text(stringResource(R.string.confirm))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        domainToAdd = ""
                        showAddDomainDialog = false
                    }
                ) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_technitium),
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

                UiState.Offline -> {
                    ErrorScreen(
                        message = "",
                        onRetry = { viewModel.fetchDashboard(forceLoading = true) },
                        isOffline = true,
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { state.retryAction?.invoke() ?: viewModel.fetchDashboard(forceLoading = true) },
                        modifier = Modifier.padding(paddingValues)
                    )
                }

                is UiState.Success -> {
                    TechnitiumDashboardContent(
                        data = state.data,
                        selectedRange = selectedRange,
                        selectedPanel = selectedPanel,
                        isRunningAction = isRunningAction,
                        instances = instances,
                        selectedInstanceId = viewModel.instanceId,
                        cardColor = cardColor,
                        raisedCardColor = raisedCardColor,
                        borderColor = borderColor,
                        trackColor = trackColor,
                        accent = accent,
                        onSelectInstance = { instanceId ->
                            viewModel.setPreferredInstance(instanceId)
                            onNavigateToInstance(instanceId)
                        },
                        onSelectRange = viewModel::selectRange,
                        onSelectPanel = viewModel::selectPanel,
                        onToggleBlocking = { enabled ->
                            if (enabled) {
                                viewModel.setBlocking(true)
                            } else {
                                showDisableDialog = true
                            }
                        },
                        onForceUpdateBlockLists = viewModel::forceUpdateBlockLists,
                        onAddBlockedDomain = { showAddDomainDialog = true },
                        onRemoveBlockedDomain = viewModel::removeBlockedDomain,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(paddingValues)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TechnitiumDashboardContent(
    data: TechnitiumDashboardData,
    selectedRange: TechnitiumStatsRange,
    selectedPanel: TechnitiumViewModel.SummaryPanel,
    isRunningAction: Boolean,
    instances: List<com.homelab.app.domain.model.ServiceInstance>,
    selectedInstanceId: String,
    cardColor: Color,
    raisedCardColor: Color,
    borderColor: Color,
    trackColor: Color,
    accent: Color,
    onSelectInstance: (String) -> Unit,
    onSelectRange: (TechnitiumStatsRange) -> Unit,
    onSelectPanel: (TechnitiumViewModel.SummaryPanel) -> Unit,
    onToggleBlocking: (Boolean) -> Unit,
    onForceUpdateBlockLists: () -> Unit,
    onAddBlockedDomain: () -> Unit,
    onRemoveBlockedDomain: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val actionsEnabled = !isRunningAction
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        if (instances.size > 1) {
            item {
                ServiceInstancePicker(
                    instances = instances,
                    selectedInstanceId = selectedInstanceId,
                    onInstanceSelected = { onSelectInstance(it.id) }
                )
            }
        }

        item {
            Surface(
                color = cardColor,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = stringResource(R.string.technitium_stats_range),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TechnitiumStatsRange.entries.forEach { range ->
                            FilterChip(
                                selected = range == selectedRange,
                                onClick = { onSelectRange(range) },
                                label = {
                                    Text(
                                        text = rangeLabel(range),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                leadingIcon = if (range == selectedRange) {
                                    { Icon(Icons.Default.AutoGraph, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null
                            )
                        }
                    }
                }
            }
        }

        item {
            val blockingEnabled = data.settings.enableBlocking
            Surface(
                color = raisedCardColor,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (blockingEnabled) StatusGreen.copy(alpha = 0.16f) else StatusRed.copy(alpha = 0.16f),
                            modifier = Modifier.size(42.dp)
                        ) {
                            Icon(
                                imageVector = if (blockingEnabled) Icons.Default.Shield else Icons.Default.Warning,
                                contentDescription = null,
                                tint = if (blockingEnabled) StatusGreen else StatusRed,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.technitium_blocking),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = if (blockingEnabled) {
                                    stringResource(R.string.technitium_blocking_enabled)
                                } else {
                                    stringResource(R.string.technitium_blocking_disabled)
                                },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        FilledTonalButton(
                            onClick = { onToggleBlocking(!blockingEnabled) },
                            enabled = actionsEnabled
                        ) {
                            Text(
                                text = if (blockingEnabled) {
                                    stringResource(R.string.technitium_action_disable)
                                } else {
                                    stringResource(R.string.technitium_action_enable)
                                },
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AssistChip(
                            onClick = onForceUpdateBlockLists,
                            enabled = actionsEnabled,
                            label = {
                                Text(
                                    text = stringResource(R.string.technitium_force_update),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Refresh, null) }
                        )
                        AssistChip(
                            onClick = onAddBlockedDomain,
                            enabled = actionsEnabled,
                            label = {
                                Text(
                                    text = stringResource(R.string.technitium_add_blocked_domain),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            leadingIcon = { Icon(Icons.Default.Block, null) }
                        )
                    }
                }
            }
        }

        item {
            SectionTitle(stringResource(R.string.home_summary_title))
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard(
                        title = stringResource(R.string.technitium_total_queries),
                        value = formatNumber(data.summary.totalQueries),
                        icon = Icons.Default.AutoGraph,
                        iconTint = accent,
                        cardColor = cardColor,
                        borderColor = borderColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = stringResource(R.string.technitium_blocked_queries),
                        value = formatNumber(data.summary.totalBlocked),
                        icon = Icons.Default.Block,
                        iconTint = StatusRed,
                        cardColor = cardColor,
                        borderColor = borderColor,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SummaryMetricCard(
                        title = stringResource(R.string.technitium_total_clients),
                        value = formatNumber(data.summary.totalClients),
                        icon = Icons.Default.Group,
                        iconTint = StatusGreen,
                        cardColor = cardColor,
                        borderColor = borderColor,
                        modifier = Modifier.weight(1f)
                    )
                    SummaryMetricCard(
                        title = stringResource(R.string.technitium_blocked_zones),
                        value = formatNumber(data.summary.blockedZones),
                        icon = Icons.Default.Dns,
                        iconTint = StatusOrange,
                        cardColor = cardColor,
                        borderColor = borderColor,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        item {
            Surface(
                color = cardColor,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.technitium_queries_trend),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (data.chartSeries.isNotEmpty() && data.chartSeries.first().values.size > 1) {
                        val primary = data.chartSeries.first()
                        val secondary = data.chartSeries.getOrNull(1)
                        val total = primary.values.map { it.toInt() }
                        val blocked = secondary?.values?.map { it.toInt() } ?: List(total.size) { 0 }
                        val maxValue = max(1, total.maxOrNull() ?: 0)

                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(
                                modifier = Modifier
                                    .width(44.dp)
                                    .height(140.dp),
                                verticalArrangement = Arrangement.SpaceBetween,
                                horizontalAlignment = Alignment.End
                            ) {
                                Text(
                                    text = formatNumber(maxValue),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = formatNumber(maxValue / 2),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "0",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            TechnitiumQueryChart(
                                total = total,
                                blocked = blocked,
                                allowedColor = StatusGreen,
                                blockedColor = StatusRed,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(140.dp)
                            )
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            LegendPill(primary.label, StatusGreen)
                            secondary?.let { LegendPill(it.label, StatusRed) }
                        }
                    } else {
                        Text(
                            text = stringResource(R.string.no_data),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        item {
            Surface(
                color = cardColor,
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, borderColor)
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_summary_title),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TechnitiumPanelChip(
                            title = stringResource(R.string.technitium_top_clients),
                            selected = selectedPanel == TechnitiumViewModel.SummaryPanel.CLIENTS,
                            accent = accent,
                            onClick = { onSelectPanel(TechnitiumViewModel.SummaryPanel.CLIENTS) }
                        )
                        TechnitiumPanelChip(
                            title = stringResource(R.string.technitium_top_domains),
                            selected = selectedPanel == TechnitiumViewModel.SummaryPanel.DOMAINS,
                            accent = accent,
                            onClick = { onSelectPanel(TechnitiumViewModel.SummaryPanel.DOMAINS) }
                        )
                        TechnitiumPanelChip(
                            title = stringResource(R.string.technitium_blocked_domains),
                            selected = selectedPanel == TechnitiumViewModel.SummaryPanel.BLOCKED,
                            accent = StatusRed,
                            onClick = { onSelectPanel(TechnitiumViewModel.SummaryPanel.BLOCKED) }
                        )
                        TechnitiumPanelChip(
                            title = stringResource(R.string.technitium_zone_details),
                            selected = selectedPanel == TechnitiumViewModel.SummaryPanel.ZONES,
                            accent = StatusOrange,
                            onClick = { onSelectPanel(TechnitiumViewModel.SummaryPanel.ZONES) }
                        )
                    }
                }
            }
        }

        item {
            val panelTitle = when (selectedPanel) {
                TechnitiumViewModel.SummaryPanel.CLIENTS -> stringResource(R.string.technitium_top_clients)
                TechnitiumViewModel.SummaryPanel.DOMAINS -> stringResource(R.string.technitium_top_domains)
                TechnitiumViewModel.SummaryPanel.BLOCKED -> stringResource(R.string.technitium_blocked_domains)
                TechnitiumViewModel.SummaryPanel.ZONES -> stringResource(R.string.technitium_zone_details)
            }
            SectionTitle(panelTitle)
        }

        when (selectedPanel) {
            TechnitiumViewModel.SummaryPanel.CLIENTS -> {
                if (data.topClients.isEmpty()) {
                    item {
                        EmptyPanel(stringResource(R.string.technitium_no_clients), cardColor, borderColor)
                    }
                } else {
                    val maxHits = max(1, data.topClients.maxOf { it.hits })
                    items(data.topClients, key = { it.name + (it.domain ?: "") }) { client ->
                        TopClientCard(
                            client = client,
                            maxHits = maxHits,
                            cardColor = cardColor,
                            borderColor = borderColor,
                            trackColor = trackColor
                        )
                    }
                }
            }

            TechnitiumViewModel.SummaryPanel.DOMAINS -> {
                if (data.topDomains.isEmpty()) {
                    item {
                        EmptyPanel(stringResource(R.string.technitium_no_domains), cardColor, borderColor)
                    }
                } else {
                    val maxHits = max(1, data.topDomains.maxOf { it.hits })
                    items(data.topDomains.take(20), key = { it.name }) { domain ->
                        TopDomainCard(
                            domain = domain,
                            maxHits = maxHits,
                            cardColor = cardColor,
                            borderColor = borderColor,
                            trackColor = trackColor
                        )
                    }
                }
            }

            TechnitiumViewModel.SummaryPanel.BLOCKED -> {
                if (data.topBlockedDomains.isNotEmpty()) {
                    val maxHits = max(1, data.topBlockedDomains.maxOf { it.hits })
                    items(data.topBlockedDomains.take(10), key = { "top-${it.name}" }) { domain ->
                        TopDomainCard(
                            domain = domain,
                            maxHits = maxHits,
                            cardColor = raisedCardColor,
                            borderColor = borderColor,
                            trackColor = trackColor
                        )
                    }
                }

                if (data.blockedDomains.isEmpty()) {
                    item {
                        EmptyPanel(stringResource(R.string.technitium_no_blocked_domains), cardColor, borderColor)
                    }
                } else {
                    items(data.blockedDomains.take(60), key = { "blocked-$it" }) { domain ->
                        Surface(
                            color = cardColor,
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, borderColor)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = domain,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                OutlinedButton(
                                    onClick = { onRemoveBlockedDomain(domain) },
                                    enabled = actionsEnabled
                                ) {
                                    Text(stringResource(R.string.delete))
                                }
                            }
                        }
                    }
                }
            }

            TechnitiumViewModel.SummaryPanel.ZONES -> {
                item {
                    ZoneDetailsCard(
                        data = data,
                        cardColor = cardColor,
                        raisedCardColor = raisedCardColor,
                        borderColor = borderColor
                    )
                }

                if (data.logFiles.isEmpty()) {
                    item {
                        EmptyPanel(stringResource(R.string.technitium_no_logs), cardColor, borderColor)
                    }
                } else {
                    items(data.logFiles.take(20), key = { it.fileName }) { log ->
                        LogCard(log = log, cardColor = cardColor, borderColor = borderColor)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun TechnitiumPanelChip(
    title: String,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingIcon = if (selected) {
            { Icon(Icons.Default.AutoGraph, contentDescription = null, modifier = Modifier.size(18.dp)) }
        } else null,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = accent.copy(alpha = 0.14f),
            selectedLabelColor = accent,
            selectedLeadingIconColor = accent
        )
    )
}

@Composable
private fun SummaryMetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    iconTint: Color,
    cardColor: Color,
    borderColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 96.dp),
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconTint.copy(alpha = 0.14f),
                    modifier = Modifier.size(30.dp)
                ) {
                    Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.padding(7.dp))
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun TechnitiumQueryChart(
    total: List<Int>,
    blocked: List<Int>,
    allowedColor: Color,
    blockedColor: Color,
    modifier: Modifier = Modifier
) {
    if (total.isEmpty()) {
        Text(
            text = stringResource(R.string.no_data),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    val maxValue = max(1, total.maxOrNull() ?: 0)
    val barCount = total.size

    Canvas(modifier = modifier) {
        val availableWidth = size.width
        val rawBarWidth = availableWidth / (barCount * 1.6f)
        val barWidth = rawBarWidth.coerceAtLeast(3f)
        val gap = (barWidth * 0.5f).coerceAtLeast(2f)
        val totalWidth = (barCount * barWidth) + ((barCount - 1).coerceAtLeast(0) * gap)
        val startX = ((availableWidth - totalWidth).coerceAtLeast(0f)) / 2f

        total.forEachIndexed { index, totalVal ->
            val blockedVal = blocked.getOrNull(index)?.coerceAtLeast(0) ?: 0
            val allowedVal = (totalVal - blockedVal).coerceAtLeast(0)

            val blockedHeight = (blockedVal.toFloat() / maxValue) * size.height
            val allowedHeight = (allowedVal.toFloat() / maxValue) * size.height
            val x = startX + index * (barWidth + gap)
            val yBlocked = size.height - blockedHeight
            val yAllowed = size.height - blockedHeight - allowedHeight

            if (allowedHeight > 0f) {
                drawRect(
                    color = allowedColor,
                    topLeft = Offset(x, yAllowed),
                    size = androidx.compose.ui.geometry.Size(barWidth, allowedHeight)
                )
            }

            if (blockedHeight > 0f) {
                drawRect(
                    color = blockedColor,
                    topLeft = Offset(x, yBlocked),
                    size = androidx.compose.ui.geometry.Size(barWidth, blockedHeight)
                )
            }
        }
    }
}

@Composable
private fun LegendPill(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(modifier = Modifier.size(8.dp).background(color, CircleShape))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopClientCard(
    client: TechnitiumTopClient,
    maxHits: Int,
    cardColor: Color,
    borderColor: Color,
    trackColor: Color
) {
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = client.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatNumber(client.hits),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!client.domain.isNullOrBlank()) {
                Text(
                    text = client.domain,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            if (client.rateLimited) {
                AssistChip(
                    onClick = {},
                    enabled = false,
                    label = { Text(stringResource(R.string.technitium_rate_limited)) },
                    leadingIcon = { Icon(Icons.Default.LockClock, null) }
                )
            }
            LinearProgressIndicator(
                progress = { client.hits.toFloat() / max(1, maxHits).toFloat() },
                color = ServiceType.TECHNITIUM.primaryColor,
                trackColor = trackColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}

@Composable
private fun TopDomainCard(
    domain: TechnitiumTopDomain,
    maxHits: Int,
    cardColor: Color,
    borderColor: Color,
    trackColor: Color
) {
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = domain.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatNumber(domain.hits),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            LinearProgressIndicator(
                progress = { domain.hits.toFloat() / max(1, maxHits).toFloat() },
                color = ServiceType.TECHNITIUM.primaryColor,
                trackColor = trackColor,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
            )
        }
    }
}

@Composable
private fun ZoneDetailsCard(
    data: TechnitiumDashboardData,
    cardColor: Color,
    raisedCardColor: Color,
    borderColor: Color
) {
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneStat(
                    icon = Icons.Default.Dns,
                    label = stringResource(R.string.technitium_zones_count),
                    value = formatNumber(data.zoneCount),
                    tint = StatusOrange,
                    modifier = Modifier.weight(1f),
                    cardColor = raisedCardColor
                )
                ZoneStat(
                    icon = Icons.Default.Storage,
                    label = stringResource(R.string.technitium_cache_entries),
                    value = formatNumber(data.cacheRecordCount),
                    tint = StatusGreen,
                    modifier = Modifier.weight(1f),
                    cardColor = raisedCardColor
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ZoneStat(
                    icon = Icons.Default.Security,
                    label = stringResource(R.string.technitium_blocklist_zones),
                    value = formatNumber(data.summary.blockListZones),
                    tint = StatusRed,
                    modifier = Modifier.weight(1f),
                    cardColor = raisedCardColor
                )
                ZoneStat(
                    icon = Icons.Default.TravelExplore,
                    label = stringResource(R.string.technitium_settings_version),
                    value = data.settings.version ?: "—",
                    tint = ServiceType.TECHNITIUM.primaryColor,
                    modifier = Modifier.weight(1f),
                    cardColor = raisedCardColor
                )
            }

            if (data.settings.temporaryDisableBlockingTill?.isNotBlank() == true) {
                Text(
                    text = stringResource(
                        R.string.technitium_disabled_until,
                        data.settings.temporaryDisableBlockingTill
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val sourceCount = data.settings.blockListUrls.size
            Text(
                text = stringResource(R.string.technitium_blocklist_sources_count, sourceCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ZoneStat(
    icon: ImageVector,
    label: String,
    value: String,
    tint: Color,
    cardColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = cardColor,
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun LogCard(log: TechnitiumLogFile, cardColor: Color, borderColor: Color) {
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Storage, contentDescription = null, tint = ServiceType.TECHNITIUM.primaryColor)
            Spacer(modifier = Modifier.width(10.dp))
            Text(
                text = log.fileName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = log.size,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyPanel(message: String, cardColor: Color, borderColor: Color) {
    Surface(
        color = cardColor,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, borderColor),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(14.dp)
        )
    }
}

@Composable
private fun rangeLabel(range: TechnitiumStatsRange): String {
    return when (range) {
        TechnitiumStatsRange.LAST_HOUR -> stringResource(R.string.technitium_last_hour)
        TechnitiumStatsRange.LAST_DAY -> stringResource(R.string.technitium_last_day)
        TechnitiumStatsRange.LAST_WEEK -> stringResource(R.string.technitium_last_week)
        TechnitiumStatsRange.LAST_MONTH -> stringResource(R.string.technitium_last_month)
    }
}

private fun parseColor(hex: String?, fallback: Color): Color {
    if (hex.isNullOrBlank()) return fallback
    val clean = hex.trim().removePrefix("#")
    val long = when (clean.length) {
        6 -> "FF$clean"
        8 -> clean
        else -> return fallback
    }
    return runCatching { Color(long.toULong(16)) }.getOrDefault(fallback)
}

private fun formatNumber(value: Int): String = NumberFormat.getNumberInstance(Locale.getDefault()).format(value)
