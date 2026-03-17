package com.homelab.app.ui.adguard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoGraph
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Gavel
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.adguard.AdGuardStats
import com.homelab.app.data.remote.dto.adguard.AdGuardTopItem
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdGuardHomeDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    onNavigateToQueryLog: (String) -> Unit,
    onNavigateToFilters: () -> Unit,
    onNavigateToRewrites: () -> Unit,
    onNavigateToUserRules: () -> Unit,
    onNavigateToBlockedServices: () -> Unit,
    viewModel: AdGuardHomeViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val topQueried by viewModel.topQueried.collectAsStateWithLifecycle()
    val topBlocked by viewModel.topBlocked.collectAsStateWithLifecycle()
    val topClients by viewModel.topClients.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isToggling by viewModel.isTogglingProtection.collectAsStateWithLifecycle()
    val actionError by viewModel.actionError.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.fetchDashboard()
    }

    LaunchedEffect(actionError) {
        actionError?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearActionError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(text = stringResource(R.string.service_adguard_home), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchDashboard(force = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = uiState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.ADGUARD_HOME.primaryColor)
                }
            }
            is UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { state.retryAction?.invoke() ?: viewModel.fetchDashboard() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Offline -> {
                ErrorScreen(
                    message = "",
                    onRetry = { viewModel.fetchDashboard() },
                    isOffline = true,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Success -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ServiceInstancePicker(
                            instances = instances,
                            selectedInstanceId = viewModel.instanceId,
                            onInstanceSelected = { instance ->
                                viewModel.setPreferredInstance(instance.id)
                                onNavigateToInstance(instance.id)
                            }
                        )
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        ProtectionCard(
                            enabled = status?.protectionEnabled == true,
                            isToggling = isToggling,
                            onToggle = { duration -> viewModel.toggleProtection(duration) }
                        )
                    }

                    if (stats != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AdGuardSectionHeader(title = stringResource(R.string.adguard_overview))
                        }
                        item {
                            StatCard(
                                icon = Icons.Default.AutoGraph,
                                iconBg = ServiceType.ADGUARD_HOME.primaryColor,
                                value = formatNum(stats!!.numDnsQueries),
                                label = stringResource(R.string.adguard_total_queries),
                                onClick = { onNavigateToQueryLog("all") }
                            )
                        }
                        item {
                            StatCard(
                                icon = Icons.Default.Block,
                                iconBg = StatusRed,
                                value = formatNum(stats!!.numBlockedFiltering),
                                label = stringResource(R.string.adguard_blocked),
                                onClick = { onNavigateToQueryLog("blocked") }
                            )
                        }
                        item {
                            val pct = if (stats!!.numDnsQueries > 0) {
                                (stats!!.numBlockedFiltering.toDouble() / stats!!.numDnsQueries.toDouble()) * 100.0
                            } else 0.0
                            StatCard(
                                icon = Icons.Default.BarChart,
                                iconBg = StatusOrange,
                                value = String.format(Locale.getDefault(), "%.1f%%", pct),
                                label = stringResource(R.string.adguard_blocked_percent)
                            )
                        }
                        item {
                            val avgMs = stats!!.avgProcessingTime * 1000.0
                            StatCard(
                                icon = Icons.Default.Info,
                                iconBg = StatusBlue,
                                value = String.format(Locale.getDefault(), "%.2f ms", avgMs),
                                label = stringResource(R.string.adguard_avg_processing)
                            )
                        }

                        item(span = { GridItemSpan(maxLineSpan) }) {
                            QueryChartSection(stats = stats!!)
                        }
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        QuickActionsSection(
                            onNavigateToRewrites = onNavigateToRewrites,
                            onNavigateToQueryLog = { onNavigateToQueryLog("all") },
                            onNavigateToUserRules = onNavigateToUserRules,
                            onNavigateToFilters = onNavigateToFilters,
                            onNavigateToBlockedServices = onNavigateToBlockedServices
                        )
                    }

                    if (status != null) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            AdGuardSectionHeader(title = stringResource(R.string.adguard_server_info))
                        }
                        item { InfoCard(icon = Icons.Default.Settings, label = stringResource(R.string.adguard_version), value = status?.version.orEmpty()) }
                        item { InfoCard(icon = Icons.Default.Storage, label = stringResource(R.string.adguard_dns_address), value = status?.dnsAddresses?.joinToString(", ").orEmpty()) }
                        item { InfoCard(icon = Icons.Default.Security, label = stringResource(R.string.adguard_dns_port), value = status?.dnsPort?.toString().orEmpty()) }
                        item { InfoCard(icon = Icons.Default.Link, label = stringResource(R.string.adguard_http_port), value = status?.httpPort?.toString().orEmpty()) }
                    }

                    if (topQueried.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TopListSection(title = stringResource(R.string.adguard_top_queried), items = topQueried, accent = ServiceType.ADGUARD_HOME.primaryColor)
                        }
                    }

                    if (topBlocked.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TopListSection(title = stringResource(R.string.adguard_top_blocked), items = topBlocked, accent = StatusRed)
                        }
                    }

                    if (topClients.isNotEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            TopListSection(title = stringResource(R.string.adguard_top_clients), items = topClients, accent = StatusGreen)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProtectionCard(
    enabled: Boolean,
    isToggling: Boolean,
    onToggle: (durationSeconds: Int?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = {
                Text(
                    if (enabled) stringResource(R.string.adguard_disable_protection)
                    else stringResource(R.string.adguard_enable_protection)
                )
            },
            text = {
                Column {
                    if (enabled) {
                        for (minutes in listOf(15, 30, 60)) {
                            TextButton(
                                onClick = {
                                    showDialog = false
                                    onToggle(minutes * 60)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(stringResource(R.string.adguard_disable_for_minutes, minutes))
                            }
                        }
                        TextButton(
                            onClick = {
                                showDialog = false
                                onToggle(null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.adguard_disable), color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Text(stringResource(R.string.adguard_resume_protection_desc))
                        TextButton(
                            onClick = {
                                showDialog = false
                                onToggle(null)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.adguard_enable))
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioNoBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        )
    )

    val color = if (enabled) StatusGreen else StatusRed

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                enabled = !isToggling,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    if (enabled) showDialog = true else onToggle(null)
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (enabled) Icons.Default.CheckCircle else Icons.Default.Block,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (enabled) stringResource(R.string.adguard_protection_enabled) else stringResource(R.string.adguard_protection_paused),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                )
                Text(
                    text = if (enabled) stringResource(R.string.adguard_tap_to_pause) else stringResource(R.string.adguard_tap_to_resume),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isToggling) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
            }
        }
    }
}

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, iconBg: Color, value: String, label: String, onClick: (() -> Unit)? = null) {
    val modifier = if (onClick != null) {
        Modifier.clickable(onClick = onClick)
    } else {
        Modifier
    }
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = iconBg.copy(alpha = 0.16f), modifier = Modifier.size(36.dp)) {
                Icon(icon, contentDescription = label, tint = iconBg, modifier = Modifier.padding(8.dp))
            }
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun QueryChartSection(stats: AdGuardStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdGuardSectionHeader(title = stringResource(R.string.adguard_query_activity))
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val maxValue = (stats.dnsQueries.maxOrNull() ?: 0).coerceAtLeast(1)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(
                        modifier = Modifier.width(44.dp).height(140.dp),
                        verticalArrangement = Arrangement.SpaceBetween,
                        horizontalAlignment = Alignment.End
                    ) {
                        Text(text = formatNum(maxValue.toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = formatNum((maxValue / 2).toLong()), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(text = "0", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    AdGuardQueryChart(
                        total = stats.dnsQueries,
                        blocked = stats.blockedFiltering,
                        allowedColor = StatusGreen,
                        blockedColor = StatusRed,
                        modifier = Modifier.weight(1f).height(140.dp)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    LegendDot(color = StatusGreen, label = stringResource(R.string.adguard_allowed))
                    LegendDot(color = StatusRed, label = stringResource(R.string.adguard_blocked_label))
                }
            }
        }
    }
}

@Composable
private fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
        Spacer(modifier = Modifier.width(6.dp))
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun AdGuardQueryChart(
    total: List<Int>,
    blocked: List<Int>,
    allowedColor: Color,
    blockedColor: Color,
    modifier: Modifier = Modifier
) {
    val points = total
    if (points.isEmpty()) {
        Text(text = stringResource(R.string.adguard_no_data), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }
    val maxValue = maxOf(points.maxOrNull() ?: 1, 1)
    val barCount = points.size

    androidx.compose.foundation.Canvas(modifier = modifier) {
        val availableWidth = size.width
        val rawBarWidth = availableWidth / (barCount * 1.6f)
        val barWidth = rawBarWidth.coerceAtLeast(3f)
        val gap = (barWidth * 0.5f).coerceAtLeast(2f)
        val totalWidth = (barCount * barWidth) + ((barCount - 1).coerceAtLeast(0) * gap)
        val startX = ((availableWidth - totalWidth).coerceAtLeast(0f)) / 2f

        points.forEachIndexed { index, totalVal ->
            val blockedVal = blocked.getOrNull(index) ?: 0
            val allowedVal = (totalVal - blockedVal).coerceAtLeast(0)

            val totalHeight = (totalVal.toFloat() / maxValue) * size.height
            val blockedHeight = (blockedVal.toFloat() / maxValue) * size.height
            val allowedHeight = (allowedVal.toFloat() / maxValue) * size.height

            val x = startX + index * (barWidth + gap)
            val yBlocked = size.height - blockedHeight
            val yAllowed = size.height - blockedHeight - allowedHeight

            if (allowedHeight > 0f) {
                drawRect(
                    color = allowedColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, yAllowed),
                    size = androidx.compose.ui.geometry.Size(barWidth, allowedHeight)
                )
            }
            if (blockedHeight > 0f) {
                drawRect(
                    color = blockedColor,
                    topLeft = androidx.compose.ui.geometry.Offset(x, yBlocked),
                    size = androidx.compose.ui.geometry.Size(barWidth, blockedHeight)
                )
            }
        }
    }
}

@Composable
private fun QuickActionsSection(
    onNavigateToRewrites: () -> Unit,
    onNavigateToQueryLog: () -> Unit,
    onNavigateToUserRules: () -> Unit,
    onNavigateToFilters: () -> Unit,
    onNavigateToBlockedServices: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdGuardSectionHeader(title = stringResource(R.string.adguard_quick_actions))
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onNavigateToRewrites() },
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(12.dp), color = StatusBlue.copy(alpha = 0.18f), modifier = Modifier.size(44.dp)) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = StatusBlue, modifier = Modifier.padding(10.dp))
                }
                Spacer(modifier = Modifier.width(14.dp))
                Text(text = stringResource(R.string.adguard_dns_rewrites), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
                Spacer(modifier = Modifier.weight(1f))
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard(title = stringResource(R.string.adguard_query_log), icon = Icons.AutoMirrored.Filled.ListAlt, color = StatusBlue, onClick = onNavigateToQueryLog)
            QuickActionCard(title = stringResource(R.string.adguard_user_rules), icon = Icons.AutoMirrored.Filled.Rule, color = StatusGreen, onClick = onNavigateToUserRules)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            QuickActionCard(title = stringResource(R.string.adguard_filter_lists), icon = Icons.Default.Gavel, color = StatusOrange, onClick = onNavigateToFilters)
            QuickActionCard(title = stringResource(R.string.adguard_blocked_services), icon = Icons.Default.Block, color = StatusRed, onClick = onNavigateToBlockedServices)
        }
    }
}

@Composable
private fun RowScope.QuickActionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .weight(1f)
            .heightIn(min = 108.dp)
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.18f), modifier = Modifier.size(36.dp)) {
                Icon(icon, contentDescription = title, tint = color, modifier = Modifier.padding(8.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Surface(
        modifier = Modifier.heightIn(min = 110.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = ServiceType.ADGUARD_HOME.primaryColor.copy(alpha = 0.16f), modifier = Modifier.size(34.dp)) {
                Icon(icon, contentDescription = label, tint = ServiceType.ADGUARD_HOME.primaryColor, modifier = Modifier.padding(7.dp))
            }
            Text(
                text = value.ifBlank { "—" },
                style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                maxLines = 2,
                minLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TopListSection(title: String, items: List<AdGuardTopItem>, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        AdGuardSectionHeader(title = title)
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                val trimmed = items.take(10)
                val maxValue = trimmed.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
                trimmed.forEachIndexed { index, item ->
                    TopItemRow(rank = index + 1, label = item.name, value = item.count, maxValue = maxValue, accent = accent)
                    if (index < trimmed.lastIndex) {
                        androidx.compose.material3.HorizontalDivider(
                            modifier = Modifier.padding(start = 56.dp),
                            color = MaterialTheme.colorScheme.outlineVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TopItemRow(rank: Int, label: String, value: Long, maxValue: Long, accent: Color) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.1f), modifier = Modifier.size(28.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = rank.toString(), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = accent)
                }
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(text = formatNum(value), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val pct = (value.toFloat() / maxValue.toFloat()).coerceAtLeast(0.05f)
        val barShape = RoundedCornerShape(percent = 50)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(barShape)
                .background(accent.copy(alpha = 0.18f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(pct)
                    .clip(barShape)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                accent.copy(alpha = 0.95f),
                                accent.copy(alpha = 0.55f)
                            )
                        )
                    )
            )
        }
    }
}

private fun formatNum(value: Long): String {
    return NumberFormat.getInstance().format(value)
}
