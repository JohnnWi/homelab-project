package com.homelab.app.ui.proxmox
import com.homelab.app.R

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.remote.dto.proxmox.*
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.proxmox.components.ProxmoxEmptyState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch

private fun cephCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxCephScreen(
    node: String,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val cephState by viewModel.cephStatusState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(node) {
        viewModel.fetchCephStatus(node)
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchCephStatus(node)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Ceph - $node") },
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
            modifier = Modifier.padding(padding)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
            when (val state = cephState) {
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
                        onRetry = { viewModel.fetchCephStatus(node) }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val ceph = state.data
                    val hasCephData = ceph.health != null ||
                        !(ceph.monmap?.mons.isNullOrEmpty()) ||
                        ceph.osdmap != null ||
                        ceph.pgmap != null

                    if (!hasCephData) {
                        ProxmoxEmptyState(
                            icon = Icons.Default.Storage,
                            title = "No Ceph cluster detected on this node"
                        )
                    } else {
                    LazyColumn(
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Health Card
                        item {
                            val healthStatus = ceph.health?.status ?: "Unknown"
                            val healthColor = when (healthStatus.lowercase()) {
                                "ok", "health_ok", "health_warn" -> if (healthStatus.lowercase().contains("warn")) Color(0xFFFF9800) else Color.Green
                                "err", "health_err" -> Color.Red
                                else -> Color.Gray
                            }
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = cephCardColor(isDark, serviceColor))
                            ) {
                                Column(Modifier.padding(16.dp)) {
                                    Row(
                                        Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                Icons.Default.HealthAndSafety,
                                                contentDescription = null,
                                                tint = healthColor,
                                                modifier = Modifier.size(24.dp)
                                            )
                                            Spacer(Modifier.width(10.dp))
                                            Text("Health", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Box(
                                                modifier = Modifier
                                                    .size(10.dp)
                                                    .clip(CircleShape)
                                                    .background(healthColor)
                                            )
                                            Spacer(Modifier.width(6.dp))
                                            Text(
                                                healthStatus,
                                                fontSize = 14.sp,
                                                fontWeight = FontWeight.Medium,
                                                color = healthColor
                                            )
                                        }
                                    }
                                    // Show warnings if any
                                    ceph.health?.checks?.let { checks ->
                                        if (checks.isNotEmpty()) {
                                            Spacer(Modifier.height(8.dp))
                                            checks.entries.filter { it.value.severity == "HEALTH_WARN" || it.value.severity == "HEALTH_ERR" }.take(3).forEach { (key, check) ->
                                                Text(
                                                    text = check.summary?.message ?: key,
                                                    fontSize = 11.sp,
                                                    color = Color.Gray,
                                                    maxLines = 2,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Monitors Card
                        ceph.monmap?.mons?.let { mons ->
                            if (mons.isNotEmpty()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = cephCardColor(isDark, serviceColor))
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("Monitors (${mons.size})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                            Spacer(Modifier.height(8.dp))
                                            mons.forEach { mon ->
                                                Row(
                                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Icon(
                                                            Icons.Default.Storage,
                                                            contentDescription = null,
                                                            tint = serviceColor,
                                                            modifier = Modifier.size(18.dp)
                                                        )
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(mon.name, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                                    }
                                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                                        Text("Rank ${mon.rank}", fontSize = 11.sp, color = Color.Gray)
                                                        Spacer(Modifier.width(8.dp))
                                                        Text(
                                                            mon.state ?: "unknown",
                                                            fontSize = 11.sp,
                                                            color = if (mon.state == "leader") Color.Green else Color.Gray,
                                                            fontWeight = FontWeight.Medium
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // OSD Map Card
                        ceph.osdmap?.let { osdmap ->
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = cephCardColor(isDark, serviceColor))
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("OSD Map", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(8.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                                            OsdStat(osdmap.num_osds ?: 0, "Total")
                                            OsdStat(osdmap.num_up_osds ?: 0, "Up", Color.Green)
                                            OsdStat(osdmap.num_in_osds ?: 0, "In", Color.Blue)
                                        }
                                    }
                                }
                            }
                        }

                        // OSD List
                        ceph.osdmap?.osds?.let { osds ->
                            if (osds.isNotEmpty()) {
                                item {
                                    Text("OSDs (${osds.size})", fontSize = 14.sp, fontWeight = FontWeight.Medium, modifier = Modifier.padding(top = 4.dp))
                                }
                                items(osds, key = { it.osd }) { osd ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(
                                            containerColor = cephCardColor(isDark, serviceColor).copy(alpha = 0.5f)
                                        )
                                    ) {
                                        Row(
                                            Modifier.padding(12.dp).fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Box(
                                                    modifier = Modifier
                                                        .size(8.dp)
                                                        .clip(CircleShape)
                                                        .background(if (osd.isUp && osd.isIn) Color.Green else if (osd.isUp) Color(0xFFFF9800) else Color.Red)
                                                )
                                                Spacer(Modifier.width(8.dp))
                                                Text("OSD ${osd.osd}", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                                            }
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(osd.displayWeight, fontSize = 11.sp, color = Color.Gray)
                                                Spacer(Modifier.width(8.dp))
                                                Text(
                                                    if (osd.isUp) "UP" else "DOWN",
                                                    fontSize = 10.sp,
                                                    color = if (osd.isUp) Color.Green else Color.Red,
                                                    fontWeight = FontWeight.Bold
                                                )
                                                Spacer(Modifier.width(4.dp))
                                                Text(
                                                    if (osd.isIn) "IN" else "OUT",
                                                    fontSize = 10.sp,
                                                    color = if (osd.isIn) Color.Blue else Color.Gray,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // PG Map Card
                        ceph.pgmap?.let { pgmap ->
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = cephCardColor(isDark, serviceColor))
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Placement Groups", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(8.dp))
                                        pgmap.num_pgs?.let { numPgs ->
                                            Text("Total PGs: $numPgs", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        Spacer(Modifier.height(8.dp))
                                        // PG States
                                        pgmap.pgs_by_state?.take(5)?.forEach { pgState ->
                                            Row(
                                                Modifier.fillMaxWidth().padding(vertical = 2.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween
                                            ) {
                                                Text(pgState.state_name, fontSize = 11.sp, color = Color.Gray)
                                                Text(pgState.count.toString(), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                                            }
                                        }
                                    }
                                }
                            }

                            // Data Usage Card
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = cephCardColor(isDark, serviceColor))
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Data Usage", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(8.dp))
                                        // Progress bar
                                        ProgressBar(label = "Used", percent = pgmap.usagePercent, color = serviceColor)
                                        Spacer(Modifier.height(8.dp))
                                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                            Text("Used: ${formatBytes(pgmap.bytes_used ?: 0L)}", fontSize = 11.sp, color = Color.Gray)
                                            Text("Total: ${formatBytes(pgmap.bytes_total ?: 0L)}", fontSize = 11.sp, color = Color.Gray)
                                        }
                                        Spacer(Modifier.height(4.dp))
                                        Text("Available: ${formatBytes(pgmap.bytes_avail ?: 0L)}", fontSize = 11.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }

                        // FS Map (if present)
                        ceph.fsmap?.let { fsmap ->
                            item {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = cephCardColor(isDark, serviceColor))
                                ) {
                                    Column(Modifier.padding(16.dp)) {
                                        Text("Filesystem Map", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                        Spacer(Modifier.height(8.dp))
                                        fsmap.epoch?.let { epoch ->
                                            Text("Epoch: $epoch", fontSize = 12.sp, color = Color.Gray)
                                        }
                                        fsmap.by_rank?.let { ranks ->
                                            ranks.forEach { rank ->
                                                Row(
                                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(rank.name ?: "Unknown", fontSize = 12.sp)
                                                    Text(
                                                        rank.state ?: "-",
                                                        fontSize = 12.sp,
                                                        color = if (rank.state == "up:active") Color.Green else Color.Gray
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
                }
            }
            }
        }
    }
}

@Composable
private fun OsdStat(count: Int, label: String, color: Color = Color.Gray) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(count.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = color)
        Text(label, fontSize = 11.sp, color = Color.Gray)
    }
}

@Composable
private fun ProgressBar(label: String, percent: Double, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text("${String.format("%.1f", percent)}%", fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Color.Gray.copy(alpha = 0.2f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth((percent.coerceIn(0.0, 100.0) / 100.0).toFloat())
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color)
            )
        }
    }
}

private fun formatBytes(bytes: Long): String {
    return when {
        bytes >= 1_099_511_627_776 -> String.format("%.1f TB", bytes.toDouble() / 1_099_511_627_776)
        bytes >= 1_073_741_824 -> String.format("%.1f GB", bytes.toDouble() / 1_073_741_824)
        bytes >= 1_048_576 -> String.format("%.1f MB", bytes.toDouble() / 1_048_576)
        bytes >= 1024 -> String.format("%.1f KB", bytes.toDouble() / 1024)
        else -> "${bytes} B"
    }
}
