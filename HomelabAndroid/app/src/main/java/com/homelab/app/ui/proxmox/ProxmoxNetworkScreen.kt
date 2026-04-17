package com.homelab.app.ui.proxmox

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.proxmox.ProxmoxNetworkInterface
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.proxmox.components.ProxmoxEmptyState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch

private fun proxmoxCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxNetworkScreen(
    node: String,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val networkState by viewModel.networkState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(node) {
        viewModel.fetchNetwork(node)
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchNetwork(node)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Column {
                    Text("Network Configuration")
                    Text(node, fontSize = 12.sp, color = Color.Gray)
                } },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
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
            modifier = Modifier.padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            when (val state = networkState) {
                is UiState.Idle -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.fetchNetwork(node) }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val interfaces = state.data
                    if (interfaces.isEmpty()) {
                        ProxmoxEmptyState(
                            icon = Icons.Default.Lan,
                            title = "No network interfaces found"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.animateContentSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(interfaces, key = { it.iface }) { iface ->
                                NetworkInterfaceCard(
                                    iface = iface,
                                    isDark = isDark,
                                    accentColor = serviceColor
                                )
                            }
                        }
                    }
                }
            }
            }
        }
    }
}

@Composable
private fun NetworkInterfaceCard(
    iface: ProxmoxNetworkInterface,
    isDark: Boolean,
    accentColor: Color
) {
    val (icon, typeLabel, typeColor) = when (iface.type?.lowercase()) {
        "bridge" -> Triple(Icons.Default.DeviceHub, "Bridge", Color(0xFF00BCD4))
        "bond" -> Triple(Icons.Default.Link, "Bond", Color(0xFFFF9800))
        "eth" -> Triple(Icons.Default.Lan, "Ethernet", Color(0xFF4CAF50))
        "alias" -> Triple(Icons.Default.CallSplit, "Alias", Color(0xFF9C27B0))
        else -> Triple(Icons.Default.Router, iface.type ?: "Unknown", Color.Gray)
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, accentColor))
    ) {
        Column(Modifier.padding(14.dp)) {
            // Header: Name + Type + Status indicators
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Icon(
                        icon,
                        contentDescription = null,
                        tint = typeColor,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Column {
                        Text(iface.iface, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        Text(typeLabel, fontSize = 10.sp, color = typeColor, fontWeight = FontWeight.Medium)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    if (iface.isActive) {
                        StatusBadge("Active", Color.Green)
                    }
                    if (iface.isAutostart) {
                        StatusBadge("Autostart", Color.Blue)
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // IP Addresses
            if (iface.ipAddress != "-") {
                InfoRowWithIcon(icon = Icons.Default.Computer, label = "IPv4", value = iface.ipAddress)
            }
            if (iface.gateway != null) {
                InfoRowWithIcon(icon = Icons.Default.Route, label = "Gateway", value = iface.gateway)
            }
            if (iface.ipAddress6.isNotBlank()) {
                InfoRowWithIcon(icon = Icons.Default.Computer, label = "IPv6", value = iface.ipAddress6)
            }
            if (iface.gateway6 != null) {
                InfoRowWithIcon(icon = Icons.Default.Route, label = "Gateway6", value = iface.gateway6)
            }

            // Bond / Bridge details
            if (iface.type?.lowercase() == "bridge" && !iface.bridge_ports.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                InfoRowWithIcon(icon = Icons.Default.DeviceHub, label = "Bridge Ports", value = iface.bridge_ports)
            }
            if (iface.type?.lowercase() == "bond" && !iface.bond_mode.isNullOrBlank()) {
                Spacer(Modifier.height(4.dp))
                InfoRowWithIcon(icon = Icons.Default.Link, label = "Bond Mode", value = iface.bond_mode)
            }

            // Comments
            if (!iface.comments.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Divider(color = Color.Gray.copy(alpha = 0.15f), modifier = Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.Top) {
                    Icon(
                        Icons.Default.Comment,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp).align(Alignment.CenterVertically)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        iface.comments,
                        fontSize = 11.sp,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun StatusBadge(label: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun InfoRowWithIcon(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
            Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(13.dp))
            Spacer(Modifier.width(6.dp))
            Text(label, fontSize = 11.sp, color = Color.Gray, maxLines = 1)
        }
        Text(value, fontSize = 11.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1.5f), maxLines = 2)
    }
}
