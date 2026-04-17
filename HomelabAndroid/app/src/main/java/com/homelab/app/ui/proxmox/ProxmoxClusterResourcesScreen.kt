package com.homelab.app.ui.proxmox
import com.homelab.app.R
import androidx.compose.ui.res.stringResource

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.remote.dto.proxmox.ProxmoxClusterResource
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch

private fun proxmoxCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

private fun resourceTypeColor(resource: ProxmoxClusterResource): Color {
    return when {
        resource.isNode -> Color(0xFF2196F3)
        resource.isQemu -> Color(0xFF9C27B0)
        resource.isLXC -> Color(0xFF4CAF50)
        resource.isStorage -> Color(0xFFFF9800)
        else -> Color.Gray
    }
}

private fun resourceIcon(resource: ProxmoxClusterResource): androidx.compose.ui.graphics.vector.ImageVector {
    return when {
        resource.isNode -> Icons.Default.Dns
        resource.isQemu -> Icons.Default.Computer
        resource.isLXC -> Icons.Default.Storage
        resource.isStorage -> Icons.Default.SdStorage
        else -> Icons.Default.HelpOutline
    }
}

private fun resourceTypeLabel(resource: ProxmoxClusterResource): String {
    return when {
        resource.isNode -> "Node"
        resource.isQemu -> "VM"
        resource.isLXC -> "Container"
        resource.isStorage -> "Storage"
        else -> resource.type ?: "Unknown"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxClusterResourcesScreen(
    onNavigateBack: () -> Unit,
    onNavigateToNode: (String) -> Unit = {},
    onNavigateToGuest: (String, Int, Boolean) -> Unit = { _, _, _ -> },
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val clusterResourcesState by viewModel.clusterResourcesState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.fetchClusterResources()
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchClusterResources()
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proxmox_cluster_resources)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            when (val state = clusterResourcesState) {
                is UiState.Idle, is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = state.retryAction ?: { refresh() }
                    )
                }
                is UiState.Success -> {
                    val resources = state.data
                    if (resources.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Dns, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No cluster resources found", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        // Group by type
                        val grouped = resources.groupBy { resourceTypeLabel(it) }
                        val typeOrder = listOf("Node", "VM", "Container", "Storage")
                        val sortedTypes = typeOrder.filter { it in grouped.keys } +
                            (grouped.keys - typeOrder.toSet()).sorted()

                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            sortedTypes.forEach { type ->
                                val items = grouped[type] ?: emptyList()
                                item {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            val typeColor = when (type) {
                                                "Node" -> Color(0xFF2196F3)
                                                "VM" -> Color(0xFF9C27B0)
                                                "Container" -> Color(0xFF4CAF50)
                                                "Storage" -> Color(0xFFFF9800)
                                                else -> Color.Gray
                                            }
                                            Icon(
                                                imageVector = when (type) {
                                                    "Node" -> Icons.Default.Dns
                                                    "VM" -> Icons.Default.Computer
                                                    "Container" -> Icons.Default.Storage
                                                    "Storage" -> Icons.Default.SdStorage
                                                    else -> Icons.Default.HelpOutline
                                                },
                                                contentDescription = null,
                                                tint = typeColor,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                text = "$type (${items.size})",
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                        // Summary
                                        val runningCount = items.count { it.isRunning }
                                        if (runningCount > 0 && type != "Storage" && type != "Node") {
                                            Text(
                                                "$runningCount/${items.size} running",
                                                fontSize = 11.sp,
                                                color = Color.Green
                                            )
                                        }
                                    }
                                }

                                items(items, key = { "${it.type}_${it.vmid ?: it.storage ?: it.node}" }) { resource ->
                                    ClusterResourceCard(
                                        resource = resource,
                                        color = resourceTypeColor(resource),
                                        isDark = isDark,
                                        onNavigateToNode = onNavigateToNode,
                                        onNavigateToGuest = onNavigateToGuest
                                    )
                                }
                            }
                        }
                    }
                }
                else -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
        }
    }
}

@Composable
private fun ClusterResourceCard(
    resource: ProxmoxClusterResource,
    color: Color,
    isDark: Boolean,
    onNavigateToNode: (String) -> Unit,
    onNavigateToGuest: (String, Int, Boolean) -> Unit
) {
    val cardColor = color.copy(alpha = if (isDark) 0.018f else 0.020f)
        .copy(alpha = if (isDark) 0.85f else 0.95f)

    val onClick: (() -> Unit)? = when {
        resource.isNode && resource.node != null -> { { onNavigateToNode(resource.node!!) } }
        resource.isQemu && resource.node != null && resource.vmid != null -> { { onNavigateToGuest(resource.node!!, resource.vmid!!, true) } }
        resource.isLXC && resource.node != null && resource.vmid != null -> { { onNavigateToGuest(resource.node!!, resource.vmid!!, false) } }
        else -> null
    }

    Card(
        modifier = Modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = resourceIcon(resource),
                contentDescription = null,
                tint = if (resource.isRunning) color else Color.Gray,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = resource.name ?: resource.storage ?: resource.node ?: resource.vmid?.toString() ?: "Unknown",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val typeLabel = resourceTypeLabel(resource)
                    Text(typeLabel, fontSize = 10.sp, color = Color.Gray)
                    if (resource.vmid != null) {
                        Spacer(Modifier.width(6.dp))
                        Text("#${resource.vmid}", fontSize = 10.sp, color = Color.Gray)
                    }
                    if (resource.node != null && !resource.isNode) {
                        Spacer(Modifier.width(6.dp))
                        Text("on ${resource.node}", fontSize = 10.sp, color = Color.Gray)
                    }
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (resource.isRunning) Color.Green else Color.Gray)
                )
                if (resource.cpu != null || resource.mem != null) {
                    Spacer(Modifier.height(4.dp))
                    if (resource.cpu != null) {
                        Text("CPU: ${String.format("%.0f", resource.cpuPercent)}%", fontSize = 9.sp, color = color)
                    }
                    if (resource.mem != null && resource.maxmem != null && resource.maxmem > 0) {
                        Text("RAM: ${String.format("%.0f", resource.memPercent)}%", fontSize = 9.sp, color = Color.Blue)
                    }
                }
                if (!resource.hastate.isNullOrBlank()) {
                    Spacer(Modifier.height(2.dp))
                    Text("HA: ${resource.hastate}", fontSize = 9.sp, color = Color(0xFFFF9800))
                }
            }
        }
    }
}
