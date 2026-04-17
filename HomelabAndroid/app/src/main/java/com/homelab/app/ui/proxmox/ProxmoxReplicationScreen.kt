package com.homelab.app.ui.proxmox
import com.homelab.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
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
import com.homelab.app.data.remote.dto.proxmox.ProxmoxReplicationJob
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch

private fun replicationCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxReplicationScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val replicationState by viewModel.replicationJobsState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchReplicationJobs()
            isRefreshing = false
        }
    }

    LaunchedEffect(Unit) {
        refresh()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proxmox_replication)) },
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
            when (val state = replicationState) {
                is UiState.Idle, is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.fetchReplicationJobs() }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val jobs = state.data
                    if (jobs.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Sync, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(16.dp))
                                Text("No replication jobs configured", color = Color.Gray, fontSize = 14.sp)
                            }
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                Row(
                                    Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Jobs (${jobs.size})", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                            items(jobs, key = { it.id ?: "unknown" }) { job ->
                                ReplicationJobCard(job, isDark, serviceColor)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ReplicationJobCard(
    job: ProxmoxReplicationJob,
    isDark: Boolean,
    accent: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = replicationCardColor(isDark, accent)
        )
    ) {
        Column(Modifier.padding(16.dp)) {
            // Header row: Guest ID + Status
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Sync,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Guest ${job.guestId ?: job.id ?: "Unknown"}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // Status indicator
                    val (statusColor, statusText) = getJobStatus(job)
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(statusColor)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        statusText,
                        fontSize = 11.sp,
                        color = statusColor,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            // Type
            job.type?.let { type ->
                Text(
                    "Type: ${type.uppercase()}",
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }

            // Source -> Target
            Row(
                Modifier.fillMaxWidth().padding(vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Source", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text(job.source ?: "-", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
                Icon(
                    Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(16.dp)
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Target", fontSize = 11.sp, color = Color.Gray)
                    Spacer(Modifier.width(4.dp))
                    Text(job.target ?: "-", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                }
            }

            // Schedule and Duration
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        job.schedule ?: "No schedule",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                }
                if (job.duration != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            job.formattedDuration,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                    }
                }
            }

            // Enabled/Disabled badge
            Row(
                Modifier.fillMaxWidth().padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (job.isEnabled) Color.Green.copy(alpha = 0.15f) else Color.Red.copy(alpha = 0.15f),
                    modifier = Modifier.padding(end = 8.dp)
                ) {
                    Text(
                        if (job.isEnabled) "Enabled" else "Disabled",
                        Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                        fontSize = 10.sp,
                        color = if (job.isEnabled) Color.Green else Color.Red,
                        fontWeight = FontWeight.Medium
                    )
                }
                // Fail count
                if ((job.fail_count ?: 0) > 0) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = Color.Red.copy(alpha = 0.15f)
                    ) {
                        Text(
                            "Failures: ${job.fail_count}",
                            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                            fontSize = 10.sp,
                            color = Color.Red,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
    }
}

private fun getJobStatus(job: ProxmoxReplicationJob): Pair<Color, String> {
    return when {
        !job.isEnabled -> Pair(Color.Gray, "Disabled")
        (job.fail_count ?: 0) > 0 -> Pair(Color.Red, "Error")
        job.state?.lowercase() == "running" || job.state?.lowercase() == "active" -> Pair(Color.Green, "Running")
        job.state?.lowercase() == "idle" -> Pair(Color.Gray, "Idle")
        job.state?.lowercase() == "error" || job.state?.lowercase() == "failed" -> Pair(Color.Red, "Error")
        job.state?.lowercase() == "waiting" -> Pair(Color(0xFFFF9800), "Waiting")
        else -> Pair(Color.Gray, job.state ?: "Unknown")
    }
}
