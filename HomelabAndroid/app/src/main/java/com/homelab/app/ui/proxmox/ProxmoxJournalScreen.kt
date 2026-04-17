package com.homelab.app.ui.proxmox
import com.homelab.app.R

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.remote.dto.proxmox.ProxmoxJournalLine
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.proxmox.components.ProxmoxEmptyState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.launch

private fun journalCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

private val journalTimeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())

private fun formatTimestamp(timestamp: Double?): String {
    if (timestamp == null) return "--:--:--"
    val date = Date((timestamp * 1000).toLong())
    return journalTimeFormat.format(date)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxJournalScreen(
    node: String,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val journalState by viewModel.journalState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val listState = rememberLazyListState()

    LaunchedEffect(node) {
        viewModel.fetchJournal(node, limit = 200)
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchJournal(node, limit = 200)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Journal: $node") },
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
                when (val state = journalState) {
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
                            onRetry = { refresh() }
                        )
                    }
                    is UiState.Success -> {
                        val lines = state.data
                        if (lines.isEmpty()) {
                            ProxmoxEmptyState(
                                icon = Icons.AutoMirrored.Filled.Article,
                                title = "No journal entries found"
                            )
                        } else {
                            LazyColumn(
                                state = listState,
                                contentPadding = PaddingValues(8.dp),
                                verticalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                items(lines, key = { it.n ?: it.t?.toLong() ?: 0 }) { line ->
                                    JournalLineItem(
                                        line = line,
                                        formatTimestamp = { formatTimestamp(it) },
                                        isDark = isDark,
                                        accentColor = serviceColor
                                    )
                                }
                            }
                        }
                    }
                    is UiState.Offline -> {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No internet connection", color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JournalLineItem(
    line: ProxmoxJournalLine,
    formatTimestamp: (Double?) -> String,
    isDark: Boolean,
    accentColor: Color
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = journalCardColor(isDark, accentColor),
                shape = MaterialTheme.shapes.extraSmall
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Timestamp
        Text(
            text = formatTimestamp(line.t),
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = accentColor.copy(alpha = 0.8f),
            modifier = Modifier.width(72.dp),
            maxLines = 1
        )

        // Line number (optional)
        line.n?.let { n ->
            Text(
                text = "#$n",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color.Gray,
                modifier = Modifier.width(48.dp),
                maxLines = 1
            )
        }

        // Log text
        val logText = line.text ?: ""
        val logColor = when {
            logText.contains(Regex("\\b(error|fail|critical|emergency|alert)\\b", RegexOption.IGNORE_CASE)) ->
                MaterialTheme.colorScheme.error
            logText.contains(Regex("\\b(warn|warning)\\b", RegexOption.IGNORE_CASE)) ->
                Color(0xFFFFA500) // Orange
            else -> MaterialTheme.colorScheme.onSurface
        }

        Text(
            text = logText,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = logColor,
            lineHeight = 16.sp,
            modifier = Modifier.weight(1f)
        )
    }
}
