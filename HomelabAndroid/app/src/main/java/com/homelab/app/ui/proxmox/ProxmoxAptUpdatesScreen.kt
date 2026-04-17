package com.homelab.app.ui.proxmox
import com.homelab.app.R

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import com.homelab.app.data.remote.dto.proxmox.ProxmoxAptPackage
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
fun ProxmoxAptUpdatesScreen(
    node: String,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val aptUpdatesState by viewModel.aptUpdatesState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(node) {
        viewModel.fetchAptUpdates(node)
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchAptUpdates(node)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("System Updates") },
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
            when (val state = aptUpdatesState) {
                is UiState.Idle, is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = state.retryAction ?: { viewModel.fetchAptUpdates(node) }
                    )
                }
                is UiState.Success -> {
                    val packages = state.data
                    if (packages.isEmpty()) {
                        ProxmoxEmptyState(
                            icon = Icons.Default.CheckCircle,
                            title = "System is up to date",
                            subtitle = "No pending updates on $node",
                            iconTint = Color.Green
                        )
                    } else {
                        Column(modifier = Modifier.fillMaxSize()) {
                            // Summary bar
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = serviceColor.copy(alpha = if (isDark) 0.12f else 0.08f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.Download,
                                            contentDescription = null,
                                            tint = serviceColor,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Spacer(Modifier.width(8.dp))
                                        Text(
                                            "${packages.size} package${if (packages.size != 1) "s" else ""} available",
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Medium,
                                            color = serviceColor
                                        )
                                    }
                                    Text("Node: $node", fontSize = 12.sp, color = Color.Gray)
                                }
                            }

                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(packages, key = { "${it.`package`}-${it.version}" }) { pkg ->
                                    AptPackageCard(
                                        pkg = pkg,
                                        color = serviceColor,
                                        isDark = isDark
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
}

@Composable
private fun AptPackageCard(
    pkg: ProxmoxAptPackage,
    color: Color,
    isDark: Boolean
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
                    Icons.Default.Download,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = pkg.displayName,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = pkg.displayVersion,
                        fontSize = 12.sp,
                        color = color,
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Spacer(Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!pkg.arch.isNullOrBlank()) {
                    Chip(text = "Arch: ${pkg.arch}", fontSize = 10.sp)
                }
                if (!pkg.origin.isNullOrBlank()) {
                    Chip(text = "Origin: ${pkg.origin}", fontSize = 10.sp)
                }
                if (!pkg.`package`.isNullOrBlank() && pkg.`package` != pkg.title) {
                    Chip(text = pkg.`package`, fontSize = 10.sp)
                }
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
