package com.homelab.app.ui.proxmox

import androidx.compose.animation.animateContentSize
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.proxmox.*
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import kotlinx.coroutines.launch

private fun proxmoxCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxNodeDetailScreen(
    node: String,
    onNavigateBack: () -> Unit,
    onNavigateToGuest: (String, Int, Boolean) -> Unit,
    onNavigateToUpdates: (String) -> Unit,
    onNavigateToCeph: (String) -> Unit = {},
    onNavigateToJournal: (String) -> Unit = {},
    onNavigateToNetwork: (String) -> Unit = {},
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val nodeDetailState by viewModel.nodeDetailState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(node) {
        viewModel.fetchNodeDetail(node)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Node: $node") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { onNavigateToJournal(node) }) {
                        Icon(Icons.Default.Description, contentDescription = stringResource(R.string.proxmox_journal))
                    }
                    IconButton(onClick = { viewModel.fetchNodeDetail(node) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = nodeDetailState) {
                is UiState.Idle -> {
                    // LaunchedEffect will trigger fetch
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
                        onRetry = { viewModel.fetchNodeDetail(node) }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val data = state.data
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = {
                            isRefreshing = true
                            scope.launch {
                                viewModel.fetchNodeDetail(node)
                                isRefreshing = false
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyColumn(
                            modifier = Modifier.animateContentSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        // Status Card
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, serviceColor))
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Text("Status", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        Text(data.status.pveversion ?: "-", fontSize = 11.sp, color = Color.Gray)
                                    }
                                    Spacer(Modifier.height(12.dp))

                                    // Uptime
                                    InfoRow(label = "Uptime", value = data.status.formattedUptime)
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.15f))

                                    // Kernel
                                    if (!data.status.kversion.isNullOrBlank()) {
                                        InfoRow(label = "Kernel", value = data.status.kversion)
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.15f))
                                    }

                                    // CPUs
                                    if (data.status.cpus != null) {
                                        InfoRow(label = "CPUs", value = "${data.status.cpus}")
                                        HorizontalDivider(Modifier.padding(vertical = 8.dp), color = Color.Gray.copy(alpha = 0.15f))
                                    }

                                    // Progress bars
                                    ProgressBar(label = "CPU", percent = data.status.cpuPercent, color = serviceColor)
                                    Spacer(Modifier.height(8.dp))
                                    ProgressBar(label = "RAM", percent = data.status.memPercent, color = Color.Blue, detail = formatBytes(data.status.mem ?: 0L) + " / " + formatBytes(data.status.maxmem ?: 0L))
                                    Spacer(Modifier.height(8.dp))
                                    if ((data.status.maxswap ?: 0) > 0) {
                                        ProgressBar(label = "Swap", percent = data.status.swapPercent, color = Color(0xFFFF9800), detail = formatBytes(data.status.swap ?: 0L) + " / " + formatBytes(data.status.maxswap ?: 0L))
                                        Spacer(Modifier.height(8.dp))
                                    }
                                    data.status.rootfs?.let { rootfs ->
                                        ProgressBar(label = "RootFS", percent = data.status.rootfsPercent, color = Color(0xFF9C27B0), detail = formatBytes(rootfs.used ?: 0L) + " / " + formatBytes(rootfs.total ?: 0L))
                                    }

                                    Spacer(Modifier.height(12.dp))

                                    // Updates, Ceph and Network buttons
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(
                                            onClick = { onNavigateToUpdates(node) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = serviceColor
                                            ),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Updates", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { onNavigateToCeph(node) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFF00BCD4)
                                            ),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Ceph", fontSize = 12.sp)
                                        }
                                        OutlinedButton(
                                            onClick = { onNavigateToNetwork(node) },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.outlinedButtonColors(
                                                contentColor = Color(0xFF4CAF50)
                                            ),
                                            contentPadding = PaddingValues(vertical = 8.dp)
                                        ) {
                                            Icon(Icons.Default.Lan, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("Network", fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        }

                        // VMs Section
                        if (data.vms.isNotEmpty()) {
                            item {
                                Text("Virtual Machines (${data.vms.size})", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            items(data.vms, key = { it.vmid }) { vm ->
                                GuestRowCard(vm = vm, lxc = null, node = node, isQemu = true, color = serviceColor, isDark = isDark, onNavigate = { onNavigateToGuest(node, vm.vmid, true) })
                            }
                        }

                        // LXCs Section
                        if (data.lxcs.isNotEmpty()) {
                            item {
                                Text("Containers (${data.lxcs.size})", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            }
                            items(data.lxcs, key = { it.vmid }) { lxc ->
                                GuestRowCard(vm = null, lxc = lxc, node = node, isQemu = false, color = Color(0xFF4CAF50), isDark = isDark, onNavigate = { onNavigateToGuest(node, lxc.vmid, false) })
                            }
                        }

                        // Tasks Section
                        item {
                            Text("Recent Tasks (${data.tasks.size})", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                        }
                        if (data.tasks.isEmpty()) {
                            item {
                                Card(modifier = Modifier.fillMaxWidth()) {
                                    Text("No recent tasks", Modifier.padding(16.dp), color = Color.Gray)
                                }
                            }
                        } else {
                            items(data.tasks, key = { it.upid }) { task ->
                                TaskRow(task = task, isDark = isDark)
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
private fun InfoRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(value, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun ProgressBar(label: String, percent: Double, color: Color, detail: String? = null) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (detail != null) {
                    Text(detail, fontSize = 9.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                }
                Text("${String.format("%.0f", percent)}%", fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(Color.Gray.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percent.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun GuestRowCard(
    vm: ProxmoxVM?,
    lxc: ProxmoxLXC? = null,
    node: String,
    isQemu: Boolean,
    color: Color,
    isDark: Boolean,
    onNavigate: () -> Unit
) {
    val name = vm?.displayName ?: lxc?.displayName ?: "Unknown"
    val isRunning = vm?.isRunning ?: (lxc?.isRunning ?: false)
    val cpuPercent = vm?.cpuPercent ?: (lxc?.cpuPercent ?: 0.0)
    val memPercent = vm?.memPercent ?: (lxc?.memPercent ?: 0.0)
    val vmid = vm?.vmid ?: (lxc?.vmid ?: 0)

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onNavigate() },
        colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, color))
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (isQemu) Icons.Default.Computer else Icons.Default.Storage,
                contentDescription = null,
                tint = if (isRunning) color else Color.Gray,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(name, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text("#$vmid", fontSize = 10.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) Color.Green else if (vm?.isStopped ?: (lxc?.isStopped ?: false)) Color.Red else Color.Yellow)
                )
                Spacer(Modifier.height(4.dp))
                Text("CPU: ${String.format("%.0f", cpuPercent)}%", fontSize = 9.sp, color = color)
                Text("RAM: ${String.format("%.0f", memPercent)}%", fontSize = 9.sp, color = Color.Blue)
            }
        }
    }
}

@Composable
private fun TaskRow(task: ProxmoxTask, isDark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color.DarkGray.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.3f))
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (task.isOk) Icons.Default.CheckCircle else if (task.isRunning) Icons.Default.Sync else Icons.Default.Warning,
                contentDescription = null,
                tint = if (task.isOk) Color.Green else if (task.isRunning) Color.Blue else Color.Red,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.weight(1f)) {
                Text(task.type ?: "unknown", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                Text(task.user ?: "-", fontSize = 9.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(task.formattedStart, fontSize = 9.sp, color = Color.Gray)
                Text(task.duration, fontSize = 9.sp, color = Color.Gray)
            }
        }
    }
}

private fun formatBytes(bytes: Long): String {
    val b = bytes.toDouble()
    return when {
        b >= 1_099_511_627_776 -> String.format("%.1f TB", b / 1_099_511_627_776)
        b >= 1_073_741_824 -> String.format("%.1f GB", b / 1_073_741_824)
        b >= 1_048_576 -> String.format("%.1f MB", b / 1_048_576)
        b >= 1024 -> String.format("%.1f KB", b / 1024)
        else -> "${bytes} B"
    }
}
