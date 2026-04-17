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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.remote.dto.proxmox.ProxmoxFirewallRule
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.proxmox.components.ProxmoxEmptyState
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxFirewallScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val firewallRulesState by viewModel.firewallRulesState.collectAsStateWithLifecycle()
    val firewallOptionsState by viewModel.firewallOptionsState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchFirewallRules()
        viewModel.fetchFirewallOptions()
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchFirewallRules()
            viewModel.fetchFirewallOptions()
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cluster Firewall") },
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
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { refresh() },
            modifier = Modifier.padding(padding)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
            // Firewall Toggle Card
            FirewallToggleCard(
                optionsState = firewallOptionsState,
                onToggle = { enable ->
                    viewModel.toggleFirewall(
                        enable = enable,
                        onSuccess = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Firewall ${if (enable) "enabled" else "disabled"}")
                            }
                        }
                    )
                }
            )

            // Firewall Rules List
            when (val state = firewallRulesState) {
                is UiState.Idle, is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = state.retryAction ?: { viewModel.fetchFirewallRules() }
                    )
                }
                is UiState.Success -> {
                    val rules = state.data
                    if (rules.isEmpty()) {
                        ProxmoxEmptyState(
                            icon = Icons.Default.Shield,
                            title = "No firewall rules defined"
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(rules, key = { it.pos ?: 0 }) { rule ->
                                FirewallRuleCard(rule = rule, isDark = isDark)
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
private fun FirewallToggleCard(
    optionsState: UiState<com.homelab.app.data.remote.dto.proxmox.ProxmoxFirewallOptions>,
    onToggle: (Boolean) -> Unit
) {
    val cardBg = if (isThemeDark()) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)

    Card(
        modifier = Modifier.fillMaxWidth().padding(16.dp, 8.dp, 16.dp, 8.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Shield,
                contentDescription = null,
                tint = Color(0xFF2196F3),
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Enable Firewall",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = when (optionsState) {
                        is UiState.Success -> if (optionsState.data.isEnabled) "Firewall is active" else "Firewall is disabled"
                        else -> "Loading..."
                    },
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            when (optionsState) {
                is UiState.Success -> {
                    Switch(
                        checked = optionsState.data.isEnabled,
                        onCheckedChange = onToggle
                    )
                }
                else -> {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                }
            }
        }
    }
}

@Composable
private fun FirewallRuleCard(
    rule: ProxmoxFirewallRule,
    isDark: Boolean
) {
    val actionColor = when (rule.action?.uppercase()) {
        "ACCEPT" -> Color(0xFF4CAF50)
        "DROP" -> Color(0xFFF44336)
        "REJECT" -> Color(0xFFFF9800)
        else -> Color.Gray
    }

    val cardBg = if (isDark) Color(0xFF1E1E1E) else Color(0xFFF5F5F5)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardBg)
    ) {
        Column(Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Action badge
                Surface(
                    modifier = Modifier.clip(RoundedCornerShape(8.dp)),
                    color = actionColor.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(actionColor)
                        )
                        Text(
                            text = rule.action?.uppercase() ?: "N/A",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = actionColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }

                Spacer(Modifier.width(10.dp))

                // Direction
                val directionIcon = when (rule.type?.lowercase()) {
                    "in" -> Icons.Default.Download
                    "out" -> Icons.Default.Upload
                    else -> Icons.Default.DeviceHub
                }
                Icon(
                    directionIcon,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = (rule.type?.uppercase() ?: "N/A"),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Gray,
                    modifier = Modifier.weight(1f)
                )

                // Enabled indicator
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (rule.isEnabled) Color.Green else Color.Gray)
                )
            }

            // Source / Dest / Protocol / Port
            Column(Modifier.padding(top = 10.dp)) {
                if (!rule.source.isNullOrBlank()) {
                    DetailRow(icon = Icons.Default.LocationOn, label = "Source", value = rule.source)
                }
                if (!rule.dest.isNullOrBlank()) {
                    DetailRow(icon = Icons.Default.LocationOn, label = "Dest", value = rule.dest)
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    if (!rule.proto.isNullOrBlank()) {
                        SmallChip(text = rule.proto.uppercase())
                    }
                    if (!rule.dport.isNullOrBlank()) {
                        SmallChip(text = "Port: ${rule.dport}")
                    }
                    if (!rule.iface.isNullOrBlank()) {
                        SmallChip(text = "IF: ${rule.iface}")
                    }
                }

                if (!rule.comment.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = rule.comment,
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

@Composable
private fun DetailRow(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(13.dp))
        Text(text = "$label:", fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Medium)
        Text(
            text = value,
            fontSize = 11.sp,
            color = Color.Unspecified,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun SmallChip(text: String) {
    Surface(
        modifier = Modifier.clip(RoundedCornerShape(10.dp)),
        color = Color.Gray.copy(alpha = 0.12f)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            color = Color.Gray,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
        )
    }
}
