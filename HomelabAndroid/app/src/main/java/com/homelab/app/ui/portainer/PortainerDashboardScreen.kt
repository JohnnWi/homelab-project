package com.homelab.app.ui.portainer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.*
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
import com.homelab.app.data.remote.dto.portainer.EndpointSnapshot
import com.homelab.app.data.remote.dto.portainer.PortainerEndpoint
import com.homelab.app.data.remote.dto.portainer.DockerSnapshotRaw
import com.homelab.app.ui.components.M3ExpressiveButtonCard
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortainerDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToContainers: (endpointId: Int) -> Unit,
    viewModel: PortainerViewModel = hiltViewModel()
) {
    val haptic = LocalHapticFeedback.current
    val endpoints by viewModel.endpoints.collectAsStateWithLifecycle()
    val selectedEndpoint by viewModel.selectedEndpoint.collectAsStateWithLifecycle()
    val containers by viewModel.containers.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchAll()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_portainer), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        onNavigateBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        viewModel.fetchAll()
                    }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && containers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        } else if (error != null && endpoints.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                Text(
                    text = error ?: stringResource(R.string.error_unknown),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Endpoint Picker if > 1
                if (endpoints.size > 1) {
                    item {
                        Text(
                            text = stringResource(R.string.portainer_endpoints),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            items(endpoints) { ep ->
                                EndpointCard(
                                    endpoint = ep,
                                    isSelected = selectedEndpoint?.id == ep.id,
                                    onClick = { viewModel.selectEndpoint(ep) }
                                )
                            }
                        }
                    }
                }

                if (selectedEndpoint != null) {
                    val raw = selectedEndpoint?.snapshots?.firstOrNull()?.dockerSnapshotRaw
                    val hasInfo = (raw?.operatingSystem?.isNotBlank() == true && raw.operatingSystem != "N/A") ||
                                  (raw?.serverVersion?.isNotBlank() == true && raw.serverVersion != "N/A") ||
                                  (raw?.architecture?.isNotBlank() == true && raw.architecture != "N/A")

                    if (hasInfo) {
                        item {
                            Text(
                                text = stringResource(R.string.beszel_info_title),
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 8.dp)
                            )
                            ServerInfoSection(endpoint = selectedEndpoint!!, raw = raw!!)
                        }
                    }

                    item {
                        val snapshot = selectedEndpoint?.snapshots?.firstOrNull()
                        ContainerStatsSection(containers = containers, stackCount = snapshot?.stackCount ?: 0)
                    }

                    item {
                        Spacer(modifier = Modifier.height(12.dp))
                        M3ExpressiveButtonCard(
                            text = stringResource(R.string.portainer_all_containers),
                            icon = Icons.Default.ChevronRight,
                            color = ServiceType.PORTAINER.primaryColor,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onNavigateToContainers(selectedEndpoint!!.id) }
                        )
                    }

                    val snapshot = selectedEndpoint?.snapshots?.firstOrNull()
                    if (snapshot != null) {
                        item {
                            ResourcesSection(snapshot = snapshot)
                        }
                        item {
                            HealthSection(snapshot = snapshot)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EndpointCard(
    endpoint: PortainerEndpoint,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "BouncyScale"
    )
    val haptic = LocalHapticFeedback.current

    Surface(
        modifier = Modifier
            .width(200.dp)
            .scale(scale)
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onClick()
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = if (isSelected) ServiceType.PORTAINER.primaryColor.copy(alpha = 0.1f) else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(if (endpoint.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336))
                )
                Spacer(modifier = Modifier.width(8.dp))
                if (isSelected) {
                    Surface(
                        color = ServiceType.PORTAINER.primaryColor.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.pihole_status_active),
                            style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                            color = ServiceType.PORTAINER.primaryColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = endpoint.name,
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = endpoint.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ServerInfoSection(endpoint: PortainerEndpoint, raw: DockerSnapshotRaw) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = ServiceType.PORTAINER.primaryColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(
                        Icons.Default.Dns,
                        contentDescription = null,
                        tint = ServiceType.PORTAINER.primaryColor,
                        modifier = Modifier.padding(12.dp)
                    )
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text(
                        text = endpoint.name,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            if (endpoint.isOnline) Icons.Default.Wifi else Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = if (endpoint.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(if (endpoint.isOnline) R.string.home_status_online else R.string.home_status_offline),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (endpoint.isOnline) Color(0xFF4CAF50) else Color(0xFFF44336)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                    val os = raw.operatingSystem?.takeIf { it.isNotBlank() && it != "N/A" } ?: stringResource(R.string.not_available)
                    val docker = raw.serverVersion?.takeIf { it.isNotBlank() && it != "N/A" } ?: stringResource(R.string.not_available)
                    val arch = raw.architecture?.takeIf { it.isNotBlank() && it != "N/A" } ?: stringResource(R.string.not_available)
                    
                    val snapshot = endpoint.snapshots?.firstOrNull()
                    val cpuCores = snapshot?.totalCpu?.toString() ?: stringResource(R.string.not_available)
                    val totalMemory = snapshot?.totalMemory?.let { formatServerMemory(it) } ?: stringResource(R.string.not_available)
                    
                    InfoRow(label = "OS", value = os)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    InfoRow(label = "Docker", value = docker)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    InfoRow(label = "Arch", value = arch)
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    InfoRow(label = "CPU", value = "$cpuCores Cores")
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    
                    InfoRow(label = "RAM", value = totalMemory)
                }
            }
        }
    }
}

private fun formatServerMemory(bytes: Long): String {
    if (bytes == 0L) return "0 B"
    val gb = bytes / (1024.0 * 1024.0 * 1024.0)
    if (gb >= 1.0) {
        return String.format("%.1f GB", gb)
    }
    val mb = bytes / (1024.0 * 1024.0)
    return String.format("%.0f MB", mb)
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
private fun ContainerStatsSection(containers: List<com.homelab.app.data.remote.dto.portainer.PortainerContainer>, stackCount: Int) {
    val total = containers.size
    val running = containers.count { it.state == "running" }
    val stopped = containers.count { it.state == "exited" || it.state == "dead" }
    val paused = containers.count { it.state == "paused" }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.portainer_containers).uppercase(),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            MiniStatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.pihole_total_queries), value = total, color = MaterialTheme.colorScheme.primary)
            MiniStatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.portainer_running), value = running, color = Color(0xFF4CAF50))
            MiniStatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.portainer_stopped), value = stopped, color = Color(0xFFF44336))
            MiniStatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.portainer_stacks), value = stackCount, color = ServiceType.PORTAINER.primaryColor)
        }
        
        // Visual Status Bar
        if (total > 0) {
            Surface(
                shape = RoundedCornerShape(4.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier.fillMaxWidth().height(8.dp)
            ) {
                Row(modifier = Modifier.fillMaxSize()) {
                    if (running > 0) {
                        Box(modifier = Modifier.weight(running.toFloat()).fillMaxHeight().background(Color(0xFF4CAF50)))
                    }
                    if (paused > 0) {
                        Box(modifier = Modifier.weight(paused.toFloat()).fillMaxHeight().background(Color(0xFFFF9800)))
                    }
                    if (stopped > 0) {
                        Box(modifier = Modifier.weight(stopped.toFloat()).fillMaxHeight().background(Color(0xFFF44336)))
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniStatCard(modifier: Modifier = Modifier, label: String, value: Int, color: Color) {
    Surface(
        modifier = modifier.height(70.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = "$value", style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold), color = color)
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun ResourcesSection(snapshot: EndpointSnapshot) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = stringResource(R.string.beszel_resources_title),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResourceCard(modifier = Modifier.weight(1f), icon = Icons.Default.Image, value = "${snapshot.imageCount}", label = stringResource(R.string.portainer_images), color = Color(0xFFFF9800))
            ResourceCard(modifier = Modifier.weight(1f), icon = Icons.Default.SdStorage, value = "${snapshot.volumeCount}", label = stringResource(R.string.portainer_volumes), color = ServiceType.PORTAINER.primaryColor)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            ResourceCard(modifier = Modifier.weight(1f), icon = Icons.Default.Memory, value = "${snapshot.totalCpu}", label = "CPUs", color = Color(0xFF2196F3))
            ResourceCard(modifier = Modifier.weight(1f), icon = Icons.Default.Storage, value = "${snapshot.totalMemory / (1024*1024*1024)}GB", label = stringResource(R.string.beszel_memory), color = Color(0xFF9C27B0))
        }
    }
}

@Composable
private fun ResourceCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, value: String, label: String, color: Color) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(36.dp)
            ) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun HealthSection(snapshot: EndpointSnapshot) {
    if (snapshot.healthyContainerCount > 0 || snapshot.unhealthyContainerCount > 0) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = stringResource(R.string.portainer_health),
                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (snapshot.healthyContainerCount > 0) {
                    MiniStatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.portainer_healthy), value = snapshot.healthyContainerCount, color = Color(0xFF4CAF50))
                }
                if (snapshot.unhealthyContainerCount > 0) {
                    MiniStatCard(modifier = Modifier.weight(1f), label = stringResource(R.string.portainer_unhealthy), value = snapshot.unhealthyContainerCount, color = Color(0xFFF44336))
                }
            }
        }
    }
}


