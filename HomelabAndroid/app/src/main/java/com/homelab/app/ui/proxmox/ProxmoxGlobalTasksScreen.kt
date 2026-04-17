package com.homelab.app.ui.proxmox

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
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
import com.homelab.app.data.remote.dto.proxmox.ProxmoxTask
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.proxmox.components.ProxmoxEmptyState
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxGlobalTasksScreen(
    onNavigateBack: () -> Unit,
    onNavigateToTaskLog: (String, String) -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val globalTasksState by viewModel.globalTasksState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isGlobalTasksRefreshing.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proxmox_cluster_tasks)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchGlobalTasks() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = globalTasksState) {
                is UiState.Idle -> {
                    LaunchedEffect(Unit) {
                        viewModel.fetchGlobalTasks()
                    }
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
                        onRetry = { viewModel.fetchGlobalTasks() }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val tasks = state.data
                    PullToRefreshBox(
                        isRefreshing = isRefreshing,
                        onRefresh = { viewModel.refreshGlobalTasks() },
                        modifier = Modifier.fillMaxSize()
                    ) {
                        if (tasks.isEmpty()) {
                            ProxmoxEmptyState(
                                icon = Icons.Default.CheckCircle,
                                title = "No tasks found",
                                subtitle = "Tasks from all nodes will appear here"
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(tasks, key = { it.upid }) { task ->
                                    GlobalTaskRow(
                                        task = task,
                                        isDark = isDark,
                                        serviceColor = serviceColor,
                                        onClick = {
                                            onNavigateToTaskLog(task.node ?: "", task.upid)
                                        }
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
private fun GlobalTaskRow(
    task: ProxmoxTask,
    isDark: Boolean,
    serviceColor: Color,
    onClick: () -> Unit
) {
    val cardColor = if (isDark) Color.DarkGray.copy(alpha = 0.35f) else Color.LightGray.copy(alpha = 0.35f)
    val statusColor = if (task.isOk) Color.Green else if (task.isRunning) Color.Blue else Color.Red

    Card(
        modifier = Modifier.fillMaxWidth().clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Status icon
            Icon(
                if (task.isOk) Icons.Default.CheckCircle else if (task.isRunning) Icons.Default.Sync else Icons.Default.Warning,
                contentDescription = null,
                tint = statusColor,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                // Node badge + Task type
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!task.node.isNullOrBlank()) {
                        Text(
                            text = task.node,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium,
                            color = serviceColor,
                            modifier = Modifier
                                .clip(RoundedCornerShape(4.dp))
                                .background(serviceColor.copy(alpha = 0.15f))
                                .padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(
                        text = task.type ?: "unknown",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(Modifier.height(4.dp))

                // User
                Text(
                    text = task.user ?: "-",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            // Right column: duration + time
            Column(
                horizontalAlignment = Alignment.End
            ) {
                Text(
                    text = task.duration,
                    fontSize = 10.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Medium
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = task.formattedStart,
                    fontSize = 9.sp,
                    color = Color.Gray
                )
            }
        }
    }
}
