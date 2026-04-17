package com.homelab.app.ui.proxmox

import android.widget.Toast
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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
import com.homelab.app.ui.components.ServiceInstancePicker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.launch

private fun proxmoxCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    onNavigateToNode: (String) -> Unit,
    onNavigateToGuest: (String, Int, Boolean) -> Unit,
    onNavigateToStorage: (String, String) -> Unit,
    onNavigateToPool: (String) -> Unit,
    onNavigateToBackup: () -> Unit,
    onNavigateToFirewall: () -> Unit,
    onNavigateToHA: () -> Unit,
    onNavigateToClusterResources: () -> Unit,
    onNavigateToReplication: () -> Unit = {},
    onNavigateToCreate: () -> Unit = {},
    onNavigateToGlobalTasks: () -> Unit = {},
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var searchQuery by rememberSaveable { mutableStateOf("") }
    var favoritesOnly by rememberSaveable { mutableStateOf(false) }

    fun String.matchesQuery(query: String): Boolean {
        if (query.isBlank()) return true
        val lower = query.lowercase()
        return this.lowercase().contains(lower)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_proxmox)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToCreate) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.proxmox_guest_create))
                    }
                    IconButton(onClick = { viewModel.pullToRefresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = uiState) {
                is UiState.Idle -> {
                    LaunchedEffect(Unit) { viewModel.fetchAll() }
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = state.retryAction ?: { viewModel.fetchAll() }
                    )
                }
                is UiState.Success -> {
                    val data = state.data

                    val filteredNodes = data.nodes.filter { it.node.matchesQuery(searchQuery) }

                    val allVms = data.vmsByNode.flatMap { (node, vms) -> vms.map { it to node } }
                    val filteredVms = allVms.filter { (vm, node) ->
                        vm.displayName.matchesQuery(searchQuery) ||
                            node.matchesQuery(searchQuery) ||
                            vm.vmid.toString().contains(searchQuery)
                    }.let { list ->
                        if (favoritesOnly) {
                            list.filter { (vm, node) ->
                                favoriteIds.contains("${node}-vm-${vm.vmid}")
                            }
                        } else {
                            list.sortedWith(
                                compareByDescending<Pair<ProxmoxVM, String>> { (vm, node) ->
                                    favoriteIds.contains("${node}-vm-${vm.vmid}")
                                }.thenByDescending { (vm, _) -> vm.isRunning }
                            )
                        }
                    }

                    val allLxcs = data.lxcsByNode.flatMap { (node, lxcs) -> lxcs.map { it to node } }
                    val filteredLxcs = allLxcs.filter { (lxc, node) ->
                        lxc.displayName.matchesQuery(searchQuery) ||
                            node.matchesQuery(searchQuery) ||
                            lxc.vmid.toString().contains(searchQuery)
                    }.let { list ->
                        if (favoritesOnly) {
                            list.filter { (lxc, node) ->
                                favoriteIds.contains("${node}-lxc-${lxc.vmid}")
                            }
                        } else {
                            list.sortedWith(
                                compareByDescending<Pair<ProxmoxLXC, String>> { (lxc, node) ->
                                    favoriteIds.contains("${node}-lxc-${lxc.vmid}")
                                }.thenByDescending { (lxc, _) -> lxc.isRunning }
                            )
                        }
                    }

                    val filteredPools = data.pools.filter { it.poolid.matchesQuery(searchQuery) }

                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.pullToRefresh() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        LazyVerticalGrid(
                            columns = GridCells.Fixed(2),
                            contentPadding = PaddingValues(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                        // Instance Picker
                        if (instances.size > 1) {
                            item(span = { GridItemSpan(2) }) {
                                ServiceInstancePicker(
                                    instances = instances,
                                    selectedInstanceId = viewModel.instanceId,
                                    onInstanceSelected = { instance ->
                                        onNavigateToInstance(instance.id)
                                    }
                                )
                            }
                        }

                        // Search TextField
                        item(span = { GridItemSpan(2) }) {
                            OutlinedTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                modifier = Modifier.fillMaxWidth(),
                                placeholder = { Text(stringResource(R.string.proxmox_search_placeholder), fontSize = 13.sp) },
                                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray) },
                                trailingIcon = {
                                    if (searchQuery.isNotBlank()) {
                                        IconButton(onClick = { searchQuery = "" }) {
                                            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.clear_search))
                                        }
                                    }
                                },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = serviceColor,
                                    unfocusedBorderColor = Color.Gray.copy(alpha = 0.3f)
                                ),
                                textStyle = LocalTextStyle.current.copy(fontSize = 13.sp)
                            )
                        }

                        // Favorites Filter Chip
                        item(span = { GridItemSpan(2) }) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilterChip(
                                    selected = favoritesOnly,
                                    onClick = { favoritesOnly = !favoritesOnly },
                                    label = { Text(stringResource(R.string.proxmox_favorites), fontSize = 12.sp) },
                                    leadingIcon = {
                                        Icon(
                                            if (favoritesOnly) Icons.Default.Star else Icons.Outlined.StarOutline,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = if (favoritesOnly) Color(0xFFFFC107) else Color.Gray
                                        )
                                    },
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = Color(0xFFFFC107).copy(alpha = 0.15f),
                                        selectedLabelColor = Color(0xFFFFC107),
                                        selectedLeadingIconColor = Color(0xFFFFC107)
                                    )
                                )
                                if (favoriteIds.isNotEmpty()) {
                                    Text(
                                        "${favoriteIds.size} pinned",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.align(Alignment.CenterVertically)
                                    )
                                }
                            }
                        }

                        // Quick Nav: Backup & Firewall
                        item {
                            QuickNavCard(
                                icon = Icons.Default.Backup,
                                label = stringResource(R.string.proxmox_backup_jobs),
                                color = serviceColor,
                                isDark = isDark,
                                onClick = onNavigateToBackup
                            )
                        }
                        item {
                            QuickNavCard(
                                icon = Icons.Default.Shield,
                                label = stringResource(R.string.proxmox_firewall),
                                color = Color(0xFF9C27B0),
                                isDark = isDark,
                                onClick = onNavigateToFirewall
                            )
                        }
                        item {
                            QuickNavCard(
                                icon = Icons.Default.Group,
                                label = stringResource(R.string.proxmox_ha),
                                color = Color(0xFFFF9800),
                                isDark = isDark,
                                onClick = onNavigateToHA
                            )
                        }
                        item {
                            QuickNavCard(
                                icon = Icons.Default.Dns,
                                label = stringResource(R.string.proxmox_cluster_resources),
                                color = Color(0xFF00BCD4),
                                isDark = isDark,
                                onClick = onNavigateToClusterResources
                            )
                        }
                        item {
                            QuickNavCard(
                                icon = Icons.Default.Sync,
                                label = stringResource(R.string.proxmox_replication),
                                color = Color(0xFF673AB7),
                                isDark = isDark,
                                onClick = onNavigateToReplication
                            )
                        }
                        item {
                            QuickNavCard(
                                icon = Icons.Default.Timeline,
                                label = stringResource(R.string.proxmox_cluster_tasks),
                                color = Color(0xFF607D8B),
                                isDark = isDark,
                                onClick = onNavigateToGlobalTasks
                            )
                        }

                        // Version Card
                        item(span = { GridItemSpan(2) }) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, serviceColor))
                            ) {
                                Row(Modifier.padding(16.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Column {
                                        Text("Proxmox VE", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                        Text("v${data.version.version ?: "?"} (${data.version.release ?: "?"})", fontSize = 12.sp, color = Color.Gray)
                                    }
                                    Text("${data.onlineNodes}/${data.nodes.size} ${if (data.onlineNodes == 1) stringResource(R.string.proxmox_node) else "nodes"}", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = if (data.onlineNodes == data.nodes.size) Color.Green else Color.Red)
                                }
                            }
                        }

                        // Quick Stats
                        item {
                            StatCard(
                                icon = Icons.Default.Computer,
                                label = stringResource(R.string.proxmox_vms),
                                value = "${data.runningVMs}/${data.totalVMs}",
                                color = serviceColor,
                                isDark = isDark
                            )
                        }
                        item {
                            StatCard(
                                icon = Icons.Default.Storage,
                                label = stringResource(R.string.proxmox_containers),
                                value = "${data.runningLXCs}/${data.totalLXCs}",
                                color = Color(0xFF4CAF50),
                                isDark = isDark
                            )
                        }

                        // Nodes
                        if (filteredNodes.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Text(stringResource(R.string.proxmox_nodes), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                            }
                            items(filteredNodes, key = { it.node }) { node ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToNode(node.node) },
                                    colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, serviceColor))
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.Dns,
                                                contentDescription = null,
                                                tint = if (node.isOnline) serviceColor else Color.Gray,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Column(Modifier.weight(1f)) {
                                                Text(node.node, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                                Text("${node.formattedUptime} uptime", fontSize = 10.sp, color = Color.Gray)
                                            }
                                            Box(
                                                modifier = Modifier
                                                    .size(8.dp)
                                                    .clip(CircleShape)
                                                    .background(if (node.isOnline) Color.Green else Color.Red)
                                            )
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        ProgressBar(label = "CPU", percent = node.cpuPercent, color = serviceColor)
                                        Spacer(Modifier.height(4.dp))
                                        ProgressBar(label = "RAM", percent = node.memPercent, color = Color.Blue)
                                    }
                                }
                            }
                        }

                        // VMs
                        if (filteredVms.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Text(stringResource(R.string.proxmox_vms), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                            }
                            items(filteredVms, key = { it.first.vmid }) { (vm, node) ->
                                GuestCard(vm = vm, node = node, isQemu = true, color = serviceColor, isDark = isDark, isFavorite = favoriteIds.contains("${node}-vm-${vm.vmid}"), onAction = { action -> viewModel.performAction(action, node, vm.vmid, true) }, onNavigate = { onNavigateToGuest(node, vm.vmid, true) }, snackbarHostState = snackbarHostState, context = context, scope = scope)
                            }
                        }

                        // LXCs
                        if (filteredLxcs.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Text(stringResource(R.string.proxmox_containers), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                            }
                            items(filteredLxcs, key = { it.first.vmid }) { (lxc, node) ->
                                GuestCard(vm = null, lxc = lxc, node = node, isQemu = false, color = Color(0xFF4CAF50), isDark = isDark, isFavorite = favoriteIds.contains("${node}-lxc-${lxc.vmid}"), onAction = { action -> viewModel.performAction(action, node, lxc.vmid, false) }, onNavigate = { onNavigateToGuest(node, lxc.vmid, false) }, snackbarHostState = snackbarHostState, context = context, scope = scope)
                            }
                        }

                        // Pools
                        if (filteredPools.isNotEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Text(stringResource(R.string.proxmox_pools), fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                            }
                            items(filteredPools, key = { it.poolid }) { pool ->
                                Card(
                                    modifier = Modifier.fillMaxWidth().clickable { onNavigateToPool(pool.poolid) },
                                    colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, Color(0xFF9C27B0)))
                                ) {
                                    Column(Modifier.padding(12.dp)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFF9C27B0))
                                            Spacer(Modifier.width(8.dp))
                                            Text(pool.poolid, fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                        }
                                        if (!pool.comment.isNullOrBlank()) {
                                            Text(pool.comment, fontSize = 10.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }
                        }

                        // Empty search result
                        if (searchQuery.isNotBlank() && filteredNodes.isEmpty() && filteredVms.isEmpty() && filteredLxcs.isEmpty() && filteredPools.isEmpty()) {
                            item(span = { GridItemSpan(2) }) {
                                Column(
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Default.Search, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                    Spacer(Modifier.height(8.dp))
                                    Text(stringResource(R.string.proxmox_no_results, searchQuery), color = Color.Gray, fontSize = 14.sp)
                                }
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
private fun QuickNavCard(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    color: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (isDark) 0.02f else 0.03f))
    ) {
        Column(
            Modifier.padding(12.dp).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
        }
    }
}

@Composable
private fun StatCard(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, color: Color, isDark: Boolean) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = if (isDark) 0.02f else 0.03f))
    ) {
        Column(Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Text(label, fontSize = 10.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun ProgressBar(label: String, percent: Double, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 9.sp, color = Color.Gray)
            Text("${String.format("%.0f", percent)}%", fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(Color.Gray.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percent.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color)
            )
        }
    }
}

@Composable
private fun GuestCard(
    vm: ProxmoxVM?,
    lxc: ProxmoxLXC? = null,
    node: String,
    isQemu: Boolean,
    color: Color,
    isDark: Boolean,
    isFavorite: Boolean = false,
    onAction: (ProxmoxGuestAction) -> Unit,
    onNavigate: () -> Unit,
    snackbarHostState: SnackbarHostState,
    context: android.content.Context,
    scope: CoroutineScope
) {
    val name = vm?.displayName ?: lxc?.displayName ?: "Unknown"
    val isRunning = vm?.isRunning ?: (lxc?.isRunning ?: false)
    val isStopped = vm?.isStopped ?: (lxc?.isStopped ?: false)
    val cpuPercent = vm?.cpuPercent ?: (lxc?.cpuPercent ?: 0.0)
    val memPercent = vm?.memPercent ?: (lxc?.memPercent ?: 0.0)
    val vmid = vm?.vmid ?: (lxc?.vmid ?: 0)

    var showMenu by remember { mutableStateOf(false) }
    val actionLabel = when {
        isStopped -> stringResource(R.string.proxmox_start)
        isRunning -> "Actions"
        else -> null
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { onNavigate() },
                onLongClick = { if (actionLabel != null) showMenu = true }
            ),
        colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, color))
    ) {
        Column(Modifier.padding(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    if (isQemu) Icons.Default.Computer else Icons.Default.Storage,
                    contentDescription = null,
                    tint = if (isRunning) color else Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(6.dp))
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(name, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (isFavorite) {
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.Default.Star,
                                contentDescription = null,
                                tint = Color(0xFFFFC107),
                                modifier = Modifier.size(12.dp)
                            )
                        }
                    }
                    Text("#$vmid • $node", fontSize = 9.sp, color = Color.Gray)
                }
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(if (isRunning) Color.Green else if (isStopped) Color.Red else Color.Yellow)
                )
            }
            Spacer(Modifier.height(6.dp))
            ProgressBar(label = "CPU", percent = cpuPercent, color = color)
            Spacer(Modifier.height(2.dp))
            ProgressBar(label = "RAM", percent = memPercent, color = Color.Blue)

            if (actionLabel != null) {
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(4.dp)
                ) {
                    Text(actionLabel, fontSize = 9.sp, color = color, fontWeight = FontWeight.Medium)
                }
            }
        }
    }

    if (showMenu) {
        DropdownMenu(
            expanded = true,
            onDismissRequest = { showMenu = false }
        ) {
            val startSent = stringResource(R.string.proxmox_start_sent)
            if (isStopped) {
                DropdownMenuItem(
                    text = { Text(startSent, color = Color.Green) },
                    onClick = {
                        showMenu = false
                        onAction(ProxmoxGuestAction.START)
                        launchSnackbar(snackbarHostState, startSent, scope)
                    },
                    leadingIcon = { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Green) }
                )
            } else {
                val shutdownSent = stringResource(R.string.proxmox_shutdown_sent)
                val stopSent = stringResource(R.string.proxmox_stop_sent)
                val rebootSent = stringResource(R.string.proxmox_reboot_sent)
                DropdownMenuItem(
                    text = { Text(shutdownSent) },
                    onClick = {
                        showMenu = false
                        onAction(ProxmoxGuestAction.SHUTDOWN)
                        launchSnackbar(snackbarHostState, shutdownSent, scope)
                    },
                    leadingIcon = { Icon(Icons.Default.PowerSettingsNew, contentDescription = null) }
                )
                DropdownMenuItem(
                    text = { Text(stopSent, color = Color.Red) },
                    onClick = {
                        showMenu = false
                        onAction(ProxmoxGuestAction.STOP)
                        launchSnackbar(snackbarHostState, stopSent, scope)
                    },
                    leadingIcon = { Icon(Icons.Default.Stop, contentDescription = null, tint = Color.Red) }
                )
                DropdownMenuItem(
                    text = { Text(rebootSent) },
                    onClick = {
                        showMenu = false
                        onAction(ProxmoxGuestAction.REBOOT)
                        launchSnackbar(snackbarHostState, rebootSent, scope)
                    },
                    leadingIcon = { Icon(Icons.Default.RestartAlt, contentDescription = null) }
                )
            }
        }
    }
}

private fun launchSnackbar(snackbarHostState: SnackbarHostState, message: String, scope: CoroutineScope) {
    scope.launch {
        snackbarHostState.showSnackbar(message)
    }
}
