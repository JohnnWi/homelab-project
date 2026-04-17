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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.data.remote.dto.proxmox.ProxmoxHAGroup
import com.homelab.app.data.remote.dto.proxmox.ProxmoxHAResource
import com.homelab.app.data.remote.dto.proxmox.ProxmoxReplicationJob
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.proxmox.components.ProxmoxEmptyState
import androidx.compose.material3.pulltorefresh.PullToRefreshBox

private fun proxmoxCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

private fun stateColor(state: String?): Color {
    return when (state?.lowercase()) {
        "started", "online", "active" -> Color.Green
        "stopped", "offline" -> Color.Red
        "error", "failed" -> Color(0xFFE53935)
        "started_standalone", "fence", "fenced" -> Color(0xFFFF9800)
        else -> Color.Gray
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxHAScreen(
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val haResourcesState by viewModel.haResourcesState.collectAsStateWithLifecycle()
    val haGroupsState by viewModel.haGroupsState.collectAsStateWithLifecycle()
    val replicationJobsState by viewModel.replicationJobsState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    var selectedTab by remember { mutableIntStateOf(0) }
    var isRefreshing by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchHAResources()
        viewModel.fetchHAGroups()
    }

    LaunchedEffect(selectedTab) {
        if (selectedTab == 2) {
            viewModel.fetchReplicationJobs()
        }
    }

    fun refresh() {
        isRefreshing = true
        scope.launch {
            viewModel.fetchHAResources()
            viewModel.fetchHAGroups()
            if (selectedTab == 2) viewModel.fetchReplicationJobs()
            isRefreshing = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.proxmox_ha)) },
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
            // Tab Row
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = MaterialTheme.colorScheme.surface,
                contentColor = serviceColor
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("Resources", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Computer, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("Groups", fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Group, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                Tab(
                    selected = selectedTab == 2,
                    onClick = { selectedTab = 2 },
                    text = { Text(stringResource(R.string.proxmox_replication), fontSize = 13.sp) },
                    icon = { Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
            }

            when (selectedTab) {
                0 -> HAResourcesTab(haResourcesState, serviceColor, isDark, viewModel)
                1 -> HAGroupsTab(haGroupsState, serviceColor, isDark, viewModel)
                2 -> ReplicationTab(replicationJobsState, serviceColor, isDark, viewModel, snackbarHostState, scope)
            }
            }
        }
    }
}

@Composable
private fun HAResourcesTab(
    state: UiState<List<ProxmoxHAResource>>,
    serviceColor: Color,
    isDark: Boolean,
    viewModel: ProxmoxViewModel
) {
    when (val s = state) {
        is UiState.Idle, is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            ErrorScreen(
                message = s.message,
                onRetry = s.retryAction ?: { viewModel.fetchHAResources() }
            )
        }
        is UiState.Success -> {
            val resources = s.data
            if (resources.isEmpty()) {
                ProxmoxEmptyState(
                    icon = Icons.Default.Computer,
                    title = "No HA resources configured"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(resources, key = { it.sid ?: "unknown" }) { resource ->
                        HAResourceCard(resource, serviceColor, isDark)
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

@Composable
private fun HAResourceCard(
    resource: ProxmoxHAResource,
    color: Color,
    isDark: Boolean
) {
    val cardColor = color.copy(alpha = if (isDark) 0.018f else 0.020f)
        .copy(alpha = if (isDark) 0.85f else 0.95f)

    val resourceStateColor = stateColor(resource.state)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (resource.isVm) Icons.Default.Computer else Icons.Default.Storage,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = resource.resourceId ?: resource.sid ?: "Unknown",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (resource.isVm) "VM" else if (resource.isCt) "Container" else (resource.type ?: "Unknown"),
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    if (!resource.group.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("•", fontSize = 10.sp, color = Color.Gray)
                        Spacer(Modifier.width(6.dp))
                        Text("Group: ${resource.group}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(resourceStateColor)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = resource.state ?: "unknown",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = resourceStateColor
                )
            }
        }

        if (!resource.comment.isNullOrBlank() || resource.max_relocate != null || resource.max_restart != null) {
            Divider(Modifier.padding(horizontal = 14.dp), color = Color.Gray.copy(alpha = 0.15f))
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (resource.max_relocate != null) {
                    Chip(text = "Max Relocate: ${resource.max_relocate}", fontSize = 10.sp)
                }
                if (resource.max_restart != null) {
                    Chip(text = "Max Restart: ${resource.max_restart}", fontSize = 10.sp)
                }
            }
            if (!resource.comment.isNullOrBlank()) {
                Text(
                    text = resource.comment,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 4.dp),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HAGroupsTab(
    state: UiState<List<ProxmoxHAGroup>>,
    serviceColor: Color,
    isDark: Boolean,
    viewModel: ProxmoxViewModel
) {
    when (val s = state) {
        is UiState.Idle, is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            ErrorScreen(
                message = s.message,
                onRetry = s.retryAction ?: { viewModel.fetchHAGroups() }
            )
        }
        is UiState.Success -> {
            val groups = s.data
            if (groups.isEmpty()) {
                ProxmoxEmptyState(
                    icon = Icons.Default.Group,
                    title = "No HA groups configured"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(groups, key = { it.group ?: "unknown" }) { group ->
                        HAGroupCard(group, serviceColor, isDark)
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

@Composable
private fun HAGroupCard(
    group: ProxmoxHAGroup,
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
                    Icons.Default.Group,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = group.group ?: "Unknown",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                    if (!group.type.isNullOrBlank()) {
                        Text("Type: ${group.type}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
            }

            if (!group.comment.isNullOrBlank()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = group.comment,
                    fontSize = 11.sp,
                    color = Color.Gray,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(Modifier.height(10.dp))

            // Nodes
            if (group.nodeList.isNotEmpty()) {
                Text(stringResource(R.string.proxmox_nodes), fontSize = 11.sp, fontWeight = FontWeight.Medium, color = Color.Gray)
                Spacer(Modifier.height(6.dp))
                group.nodeList.forEach { nodeName ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Dns, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(nodeName, fontSize = 12.sp)
                        }
                        // Parse priority from nodes string "node:priority"
                        val priority = group.nodes?.split(",")
                            ?.firstOrNull { it.startsWith("$nodeName:") }
                            ?.split(":")
                            ?.lastOrNull()
                        if (priority != null) {
                            Chip(text = "Priority: $priority", fontSize = 10.sp)
                        }
                    }
                }
            }

            // Flags
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (group.restricted == 1) {
                    Chip(text = "Restricted", fontSize = 10.sp)
                }
                if (group.nofailback == 1) {
                    Chip(text = "No Failback", fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun ReplicationTab(
    state: UiState<List<ProxmoxReplicationJob>>,
    serviceColor: Color,
    isDark: Boolean,
    viewModel: ProxmoxViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    when (val s = state) {
        is UiState.Idle, is UiState.Loading -> {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }
        is UiState.Error -> {
            ErrorScreen(
                message = s.message,
                onRetry = s.retryAction ?: { viewModel.fetchReplicationJobs() }
            )
        }
        is UiState.Success -> {
            val jobs = s.data
            if (jobs.isEmpty()) {
                ProxmoxEmptyState(
                    icon = Icons.Default.Sync,
                    title = "No replication jobs configured"
                )
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(jobs, key = { it.id ?: "unknown" }) { job ->
                        ReplicationJobCard(job, serviceColor, isDark, viewModel, snackbarHostState, scope)
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

@Composable
private fun ReplicationJobCard(
    job: ProxmoxReplicationJob,
    color: Color,
    isDark: Boolean,
    viewModel: ProxmoxViewModel,
    snackbarHostState: SnackbarHostState,
    scope: CoroutineScope
) {
    val cardColor = color.copy(alpha = if (isDark) 0.018f else 0.020f)
        .copy(alpha = if (isDark) 0.85f else 0.95f)

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Row(Modifier.padding(14.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Default.Sync,
                contentDescription = null,
                tint = if (job.isEnabled) color else Color.Gray,
                modifier = Modifier.size(22.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Job ${job.id ?: "Unknown"}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Guest: ${job.guestId ?: "-"}",
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    if (!job.source.isNullOrBlank()) {
                        Spacer(Modifier.width(6.dp))
                        Text("->", fontSize = 10.sp, color = Color.Gray)
                        Spacer(Modifier.width(6.dp))
                        Text("Target: ${job.target}", fontSize = 11.sp, color = Color.Gray)
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (!job.schedule.isNullOrBlank()) {
                        Chip(text = "Schedule: ${job.schedule}", fontSize = 10.sp)
                    }
                    if (job.fail_count != null && job.fail_count > 0) {
                        Chip(text = "Failures: ${job.fail_count}", fontSize = 10.sp)
                    }
                }
            }
            IconButton(
                onClick = {
                    val jobId = job.id ?: return@IconButton
                    viewModel.triggerReplicationJob(
                        id = jobId,
                        onSuccess = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Replication triggered for job $jobId")
                            }
                        }
                    )
                },
                enabled = job.isEnabled
            ) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = "Run Now",
                    tint = if (job.isEnabled) color else Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
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
