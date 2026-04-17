package com.homelab.app.ui.proxmox
import com.homelab.app.R
import androidx.compose.ui.res.stringResource

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
import com.homelab.app.data.remote.dto.proxmox.ProxmoxBackupJob
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.proxmox.components.ProxmoxEmptyState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxBackupScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val backupJobsState by viewModel.backupJobsState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) {
        viewModel.fetchBackupJobs()
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchBackupJobs()
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proxmox_backup_jobs)) },
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
            when (val state = backupJobsState) {
                is UiState.Idle, is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = state.retryAction ?: { viewModel.fetchBackupJobs() }
                    )
                }
                is UiState.Success -> {
                    val jobs = state.data
                    if (jobs.isEmpty()) {
                        ProxmoxEmptyState(
                            icon = Icons.Default.Backup,
                            title = "No backup jobs configured"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(jobs, key = { it.id ?: "unknown" }) { job ->
                                BackupJobCard(
                                    job = job,
                                    color = serviceColor,
                                    isDark = isDark,
                                    onRunNow = { viewModel.triggerBackupJob(job.id ?: "") }
                                )
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
}

@Composable
private fun BackupJobCard(
    job: ProxmoxBackupJob,
    color: Color,
    isDark: Boolean,
    onRunNow: () -> Unit
) {
    val cardColor = color.copy(alpha = if (isDark) 0.018f else 0.020f)
        .copy(alpha = if (isDark) 0.85f else 0.95f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Backup,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = job.id ?: "Unknown",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (job.isEnabled) Color.Green else Color.Gray)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (job.isEnabled) "Enabled" else "Disabled",
                            fontSize = 11.sp,
                            color = if (job.isEnabled) Color.Green else Color.Gray
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Schedule
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Schedule, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = job.schedule ?: "No schedule",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(4.dp))

            // Storage
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    text = job.storage ?: "Unknown",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Spacer(Modifier.height(4.dp))

            // Mode and compress
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!job.mode.isNullOrBlank()) {
                    Chip(text = "Mode: ${job.mode}", fontSize = 10.sp)
                }
                if (!job.compress.isNullOrBlank()) {
                    Chip(text = "Compress: ${job.compress}", fontSize = 10.sp)
                }
                if (job.backupAll) {
                    Chip(text = "All VMs", fontSize = 10.sp)
                }
            }

            // Target VMs
            if (job.vmidList.isNotEmpty()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "VMs: ${job.vmidList.joinToString(", ")}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(8.dp))

            // Run Now button
            Button(
                onClick = onRunNow,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = color),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("Run Now", fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun Chip(text: String, fontSize: androidx.compose.ui.unit.TextUnit) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(12.dp)),
        color = Color.Gray.copy(alpha = 0.15f)
    ) {
        Text(
            text = text,
            fontSize = fontSize,
            color = Color.Gray,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
