package com.homelab.app.ui.proxmox
import com.homelab.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.remote.dto.proxmox.ProxmoxTaskLogEntry
import com.homelab.app.data.remote.dto.proxmox.ProxmoxTask
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxTaskLogScreen(
    node: String,
    upid: String,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val taskLogState by viewModel.taskLogState.collectAsStateWithLifecycle()
    val taskStatusState by viewModel.taskStatusState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(node, upid) {
        var firstLoad = true
        while (isActive) {
            viewModel.refreshTaskProgress(node, upid, showLoading = firstLoad)
            firstLoad = false

            val task = (viewModel.taskStatusState.value as? UiState.Success)?.data
            if (task != null && !task.isRunning) {
                break
            }
            delay(2_000)
        }
    }

    // Auto-scroll to bottom when log entries change
    LaunchedEffect(taskLogState) {
        if (taskLogState is UiState.Success) {
            val data = (taskLogState as UiState.Success).data
            if (data.isNotEmpty()) {
                listState.animateScrollToItem(data.size - 1)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Task Log")
                        val task = (taskStatusState as? UiState.Success)?.data
                        if (task != null) {
                            Text(
                                text = if (task.isRunning) "Running" else (task.exitstatus ?: task.status ?: "Finished"),
                                fontSize = 12.sp,
                                color = if (task.isRunning) Color(0xFF2E7D32) else Color.Gray
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = taskLogState) {
                is UiState.Idle, is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.fetchTaskLog(node, upid) }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val logEntries = state.data
                    if (logEntries.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No log entries", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(12.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            val task = (taskStatusState as? UiState.Success)?.data
                            if (task != null) {
                                item(key = "status") {
                                    TaskStatusHeader(task)
                                }
                            }
                            items(logEntries, key = { it.n }) { entry ->
                                LogEntryRow(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TaskStatusHeader(task: ProxmoxTask) {
    Surface(
        color = if (task.isRunning) Color(0x1A4CAF50) else Color(0x12000000),
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (task.isRunning) "Task running" else "Task finished",
                fontWeight = FontWeight.Medium,
                color = if (task.isRunning) Color(0xFF2E7D32) else Color.Gray
            )
            Text(
                text = task.exitstatus ?: task.status ?: "-",
                fontSize = 12.sp,
                color = Color.Gray
            )
        }
    }
}

@Composable
private fun LogEntryRow(entry: ProxmoxTaskLogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = "[${entry.n}]",
            fontSize = 10.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.widthIn(min = 40.dp)
        )
        if (!entry.t.isNullOrBlank()) {
            Text(
                text = entry.t,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Unspecified,
                lineHeight = 14.sp
            )
        }
    }
}
