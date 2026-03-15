package com.homelab.app.ui.beszel

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.StatusPurple
import com.homelab.app.util.ServiceType
import com.homelab.app.util.ResourceFormatters
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceInstancePicker
import java.text.NumberFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeszelDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    onNavigateToSystem: (String) -> Unit,
    viewModel: BeszelViewModel = hiltViewModel()
) {
    val systemsState by viewModel.systemsState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchSystems()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_beszel), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchSystems() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = systemsState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.BESZEL.primaryColor)
                }
            }
            is UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { viewModel.fetchSystems() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Offline -> {
                ErrorScreen(
                    message = "",
                    onRetry = { viewModel.fetchSystems() },
                    isOffline = true,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Success -> {
                val systems = state.data
                LazyVerticalGrid(
                    columns = GridCells.Fixed(1),
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
                        OverviewCard(systems = systems)
                    }

                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Row(
                            modifier = Modifier.padding(horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh), modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(stringResource(R.string.beszel_background_update_info), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }

                    if (systems.isEmpty()) {
                        item(span = { GridItemSpan(maxLineSpan) }) {
                            Box(modifier = Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    Icon(Icons.Default.Dns, contentDescription = stringResource(R.string.beszel_no_systems), modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(stringResource(R.string.beszel_no_systems), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    } else {
                        items(systems, key = { it.id }) { sys ->
                            SystemCard(system = sys, onClick = { onNavigateToSystem(sys.id) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OverviewCard(systems: List<BeszelSystem>) {
    val onlineCount = systems.count { it.isOnline }
    val offlineCount = systems.size - onlineCount
    val beszelColor = ServiceType.BESZEL.primaryColor

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = beszelColor.copy(alpha = 0.1f),
                modifier = Modifier.size(48.dp)
            ) {
                Icon(Icons.Default.Dns, contentDescription = stringResource(R.string.beszel_monitored_servers), tint = beszelColor, modifier = Modifier.padding(12.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.beszel_monitored_servers), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${systems.size}", style = MaterialTheme.typography.headlineSmall.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(StatusGreen))
                    Text("$onlineCount ${stringResource(R.string.home_status_online)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = StatusGreen)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    Box(modifier = Modifier.size(7.dp).clip(CircleShape).background(StatusRed))
                    Text("$offlineCount ${stringResource(R.string.home_status_offline)}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = StatusRed)
                }
            }
        }
    }
}

@Composable
private fun SystemCard(system: BeszelSystem, onClick: () -> Unit) {
    val isUp = system.isOnline
    val statusColor = if (isUp) StatusGreen else StatusRed
    val beszelColor = ServiceType.BESZEL.primaryColor
    val memoryColor = StatusPurple // Purple matching iOS

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = Spring.StiffnessLow),
        label = "BouncyScale"
    )
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current

    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress)
                    onClick()
                }
            )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(statusColor))
                    Text(system.name, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = statusColor.copy(alpha = 0.1f)
                    ) {
                        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (isUp) Icons.Default.Wifi else Icons.Default.WifiOff, contentDescription = stringResource(if (isUp) R.string.home_status_online else R.string.home_status_offline), modifier = Modifier.size(12.dp), tint = statusColor)
                            Text(if (isUp) stringResource(R.string.home_status_online) else stringResource(R.string.home_status_offline), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = statusColor)
                        }
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = stringResource(R.string.beszel_system_details), tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isUp && system.info != null) {
                val info = system.info
                val extraDiskPercent = info.efs
                    ?.values
                    ?.filterNotNull()
                    ?.maxOrNull()
                    ?: 0.0

                // Metrics
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    MetricBar(icon = Icons.Default.Memory, iconColor = beszelColor, label = stringResource(R.string.beszel_cpu), value = info.cpuValue, barColor = beszelColor)
                    MetricBar(icon = Icons.Default.SdCard, iconColor = memoryColor, label = stringResource(R.string.beszel_ram), value = info.mpValue, barColor = memoryColor)
                    DualDiskMetricBar(
                        icon = Icons.Default.Storage,
                        iconColor = StatusOrange,
                        label = stringResource(R.string.beszel_disk),
                        rootPercent = info.dpValue,
                        extraPercent = extraDiskPercent,
                        barColor = StatusOrange
                    )
                }
                
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Host text
            Text(system.host, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MetricBar(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, label: String, value: Double, barColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = label, modifier = Modifier.size(16.dp), tint = iconColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(String.format("%.1f%%", value), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }

        SingleBar(percent = value, barColor = barColor)
    }
}

@Composable
private fun DualDiskMetricBar(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    iconColor: Color,
    label: String,
    rootPercent: Double,
    extraPercent: Double,
    barColor: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp), tint = iconColor)
            Spacer(modifier = Modifier.width(6.dp))
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            // Root disk percentage (primary)
            Text(String.format("%.1f%%", rootPercent), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
        }

        // Root bar
        SingleBar(percent = rootPercent, barColor = barColor)

        if (extraPercent > 0.0) {
            // Extra filesystems percentage, same style as other metrics
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                Text(
                    String.format("%.1f%%", extraPercent),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                )
            }
            SingleBar(percent = extraPercent, barColor = barColor.copy(alpha = 0.85f))
        }
    }
}

@Composable
private fun SingleBar(percent: Double, barColor: Color) {
    val pct = (percent / 100.0).coerceIn(0.0, 1.0).toFloat()
    val animatedPct by animateFloatAsState(targetValue = pct, animationSpec = spring(dampingRatio = 0.8f))

    Surface(
        shape = RoundedCornerShape(3.dp),
        color = if (isThemeDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant,
        modifier = Modifier.fillMaxWidth().height(6.dp)
    ) {
        Row {
            Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(animatedPct).background(barColor))
        }
    }
}

@Composable
private fun InfoChip(icon: androidx.compose.ui.graphics.vector.ImageVector, iconColor: Color, text: String) {
    Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
        Row(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Icon(icon, contentDescription = text, modifier = Modifier.size(10.dp), tint = iconColor)
            Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun NetworkLabel(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = text, modifier = Modifier.size(12.dp), tint = color)
        Text(text, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// --- Formatters moved to ResourceFormatters ---
