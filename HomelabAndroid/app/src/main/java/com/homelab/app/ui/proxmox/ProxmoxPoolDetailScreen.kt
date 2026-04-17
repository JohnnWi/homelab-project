@file:Suppress("DEPRECATION")

package com.homelab.app.ui.proxmox

import com.homelab.app.R
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
import com.homelab.app.data.remote.dto.proxmox.ProxmoxPoolDetail
import com.homelab.app.data.remote.dto.proxmox.ProxmoxPoolMember
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
fun ProxmoxPoolDetailScreen(
    poolId: String,
    onNavigateBack: () -> Unit,
    onNavigateToGuest: (String, Int, Boolean) -> Unit,
    onNavigateToStorage: (String, String) -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val poolDetailState by viewModel.poolDetailState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(poolId) {
        viewModel.fetchPoolDetail(poolId)
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchPoolDetail(poolId)
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pool: $poolId") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
            when (val state = poolDetailState) {
                is UiState.Idle, is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { viewModel.fetchPoolDetail(poolId) }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val detail = state.data
                    val members = detail.members.orEmpty()
                    if (members.isEmpty()) {
                        ProxmoxEmptyState(
                            icon = Icons.Default.GroupWork,
                            title = "No members in this pool"
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // Comment Card
                            if (!detail.comment.isNullOrBlank()) {
                                item {
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, serviceColor))
                                    ) {
                                        Column(Modifier.padding(16.dp)) {
                                            Text("Comment", fontSize = 12.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                                            Spacer(Modifier.height(4.dp))
                                            Text(detail.comment, fontSize = 14.sp)
                                        }
                                    }
                                }
                            }

                            // Members Header
                            item {
                                Text("Members (${detail.members?.size ?: 0})", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }

                            // Members List
                            val members = detail.members.orEmpty()
                            items(members, key = { it.name ?: it.storage ?: it.type ?: "unknown" }) { member ->
                                when (member.type) {
                                    "qemu" -> PoolMemberCard(
                                        member = member,
                                        icon = Icons.Default.Computer,
                                        color = serviceColor,
                                        isDark = isDark,
                                        onClick = {
                                            val node = member.node ?: return@PoolMemberCard
                                            val vmid = member.vmid ?: return@PoolMemberCard
                                            onNavigateToGuest(node, vmid, true)
                                        }
                                    )
                                    "lxc" -> PoolMemberCard(
                                        member = member,
                                        icon = Icons.Default.Storage,
                                        color = Color(0xFF4CAF50),
                                        isDark = isDark,
                                        onClick = {
                                            val node = member.node ?: return@PoolMemberCard
                                            val vmid = member.vmid ?: return@PoolMemberCard
                                            onNavigateToGuest(node, vmid, false)
                                        }
                                    )
                                    "storage" -> PoolMemberCard(
                                        member = member,
                                        icon = Icons.Default.SdStorage,
                                        color = Color(0xFF9C27B0),
                                        isDark = isDark,
                                        onClick = {
                                            val node = member.node ?: return@PoolMemberCard
                                            val storage = member.storage ?: return@PoolMemberCard
                                            onNavigateToStorage(node, storage)
                                        }
                                    )
                                    else -> PoolMemberCard(
                                        member = member,
                                        icon = Icons.Default.HelpOutline,
                                        color = Color.Gray,
                                        isDark = isDark,
                                        onClick = { }
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

@Suppress("DEPRECATION")
@Composable
private fun PoolMemberCard(
    member: ProxmoxPoolMember,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, color))
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = member.name ?: (member.type?.uppercase() ?: "Unknown"),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (member.vmid != null) {
                        Text("#${member.vmid}", fontSize = 11.sp, color = Color.Gray)
                        Spacer(Modifier.width(6.dp))
                    }
                    if (member.node != null) {
                        Text("Node: ${member.node}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            // Status indicator
            member.status?.let { status ->
                val statusColor = when (status.lowercase()) {
                    "running", "online" -> Color.Green
                    "stopped", "offline" -> Color.Red
                    else -> Color.Yellow
                }
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(statusColor)
                )
            }
        }
    }
}
