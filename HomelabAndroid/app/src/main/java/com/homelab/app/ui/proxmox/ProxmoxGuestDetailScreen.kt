package com.homelab.app.ui.proxmox

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.*
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.proxmox.*
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import kotlinx.coroutines.launch

private fun proxmoxCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

private enum class GuestTab(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    OVERVIEW("Overview", Icons.Default.Info),
    SNAPSHOTS("Snapshots", Icons.Default.PhotoCamera),
    CONSOLE("Console", Icons.Default.DesktopWindows),
    LOGS("Logs", Icons.AutoMirrored.Filled.ListAlt)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxGuestDetailScreen(
    node: String,
    vmid: Int,
    isQemu: Boolean,
    onNavigateBack: () -> Unit,
    onNavigateToTaskLog: ((String, String) -> Unit)? = null,
    onNavigateToConsole: ((String, Int, Boolean) -> Unit)? = null,
    onNavigateToConfig: ((String, Int, Boolean) -> Unit)? = null,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val guestDetailState by viewModel.guestDetailState.collectAsStateWithLifecycle()
    val favoriteIds by viewModel.favoriteIds.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    val guestColor = if (isQemu) serviceColor else Color(0xFF4CAF50)
    var isRefreshing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    var selectedTabIndex by remember { mutableIntStateOf(0) }

    var showCreateSnapshotDialog by remember { mutableStateOf(false) }
    var snapshotName by remember { mutableStateOf("") }
    var snapshotDescription by remember { mutableStateOf("") }

    var showCloneDialog by remember { mutableStateOf(false) }
    var cloneNewId by remember { mutableStateOf("") }
    var cloneName by remember { mutableStateOf("") }
    var cloneFullClone by remember { mutableStateOf(true) }
    var cloneTargetNode by remember { mutableStateOf("") }
    var cloneTargetStorage by remember { mutableStateOf("") }

    var showMigrateDialog by remember { mutableStateOf(false) }
    var migrateTargetNode by remember { mutableStateOf("") }
    var migrateOnline by remember { mutableStateOf(false) }

    val actionResultState by viewModel.actionResultState.collectAsStateWithLifecycle()
    var cloneSuccessMessage by remember { mutableStateOf<String?>(null) }
    var cloneErrorMessage by remember { mutableStateOf<String?>(null) }
    var actionSuccessMessage by remember { mutableStateOf<String?>(null) }
    var actionErrorMessage by remember { mutableStateOf<String?>(null) }

    var showEditNotesDialog by remember { mutableStateOf(false) }
    var editNotesText by remember { mutableStateOf("") }
    var editNotesLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(actionResultState) {
        when (actionResultState) {
            is UiState.Success<*> -> {
                val data = (actionResultState as UiState.Success<String>).data
                if (data.startsWith("UPID:")) {
                    actionSuccessMessage = "Task started: $data"
                } else {
                    cloneSuccessMessage = data
                }
                viewModel.clearActionResult()
                viewModel.fetchGuestDetail(node, vmid, isQemu)
            }
            is UiState.Error -> {
                val msg = (actionResultState as UiState.Error).message
                if (msg.contains("Action failed")) {
                    actionErrorMessage = msg
                } else {
                    cloneErrorMessage = msg
                }
                viewModel.clearActionResult()
            }
            else -> {}
        }
    }

    LaunchedEffect(node, vmid, isQemu) {
        viewModel.fetchGuestDetail(node, vmid, isQemu)
    }

    val guestConfigState by viewModel.guestConfigState.collectAsStateWithLifecycle()

    LaunchedEffect(showEditNotesDialog, guestConfigState) {
        if (showEditNotesDialog && guestConfigState is UiState.Success && !editNotesLoaded) {
            val config = (guestConfigState as UiState.Success<Map<String, String>>).data
            editNotesText = config["description"] ?: ""
            editNotesLoaded = true
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = { Text(if (isQemu) "VM $vmid" else "CT $vmid") },
                    navigationIcon = {
                        IconButton(onClick = onNavigateBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                        }
                    },
                    actions = {
                        val isFav = favoriteIds.contains("${node}-${if (isQemu) "vm" else "lxc"}-${vmid}")
                        IconButton(onClick = { viewModel.toggleFavorite(node, vmid, isQemu) }) {
                            Icon(
                                if (isFav) Icons.Default.Star else Icons.Outlined.StarBorder,
                                contentDescription = if (isFav) "Remove from favorites" else "Add to favorites",
                                tint = if (isFav) Color(0xFFFFC107) else Color.Gray
                            )
                        }
                        IconButton(onClick = { viewModel.fetchGuestDetail(node, vmid, isQemu) }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
                PrimaryTabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = MaterialTheme.colorScheme.surface
                ) {
                    GuestTab.entries.forEachIndexed { index, tab ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(tab.label, fontSize = 12.sp) },
                            icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = guestDetailState) {
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
                        onRetry = { viewModel.fetchGuestDetail(node, vmid, isQemu) }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val data = state.data
                    when (selectedTabIndex) {
                        0 -> GuestOverviewTab(
                            data = data,
                            node = node,
                            isQemu = isQemu,
                            guestColor = guestColor,
                            isDark = isDark,
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                isRefreshing = true
                                scope.launch {
                                    viewModel.fetchGuestDetail(node, vmid, isQemu)
                                    isRefreshing = false
                                }
                            },
                            onAction = { action -> viewModel.performAction(action, node, data.vmid, isQemu) },
                            onNavigateToConsole = onNavigateToConsole,
                            onNavigateToConfig = onNavigateToConfig,
                            onCloneClick = {
                                cloneNewId = ""
                                cloneName = ""
                                cloneFullClone = true
                                cloneTargetNode = ""
                                cloneTargetStorage = ""
                                showCloneDialog = true
                            },
                            onMigrateClick = {
                                migrateTargetNode = ""
                                migrateOnline = false
                                showMigrateDialog = true
                            },
                            onEditNotesClick = {
                                editNotesText = ""
                                editNotesLoaded = false
                                showEditNotesDialog = true
                                viewModel.fetchGuestConfig(node, data.vmid, isQemu)
                            }
                        )
                        1 -> GuestSnapshotsTab(
                            snapshots = data.snapshots,
                            guestColor = guestColor,
                            isDark = isDark,
                            onCreateSnapshot = { showCreateSnapshotDialog = true },
                            onDelete = { viewModel.deleteSnapshot(node, data.vmid, isQemu, it) },
                            onRollback = { viewModel.rollbackSnapshot(node, data.vmid, isQemu, it) }
                        )
                        2 -> GuestConsoleTab(
                            node = node,
                            vmid = vmid,
                            isQemu = isQemu,
                            onNavigateToFullConsole = onNavigateToConsole,
                            viewModel = viewModel
                        )
                        3 -> GuestLogsTab(
                            node = node,
                            vmid = data.vmid,
                            isQemu = isQemu,
                            onNavigateToTaskLog = onNavigateToTaskLog,
                            viewModel = viewModel
                        )
                    }
                }
            }
        }

        // Create Snapshot Dialog
        if (showCreateSnapshotDialog) {
            AlertDialog(
                onDismissRequest = { showCreateSnapshotDialog = false },
                title = { Text("Create Snapshot") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = snapshotName,
                            onValueChange = { snapshotName = it },
                            label = { Text("Snapshot Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = snapshotDescription,
                            onValueChange = { snapshotDescription = it },
                            label = { Text("Description") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 3
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (snapshotName.isNotBlank()) {
                                viewModel.createSnapshot(node, vmid, isQemu, snapshotName.trim(), snapshotDescription.trim())
                                snapshotName = ""
                                snapshotDescription = ""
                                showCreateSnapshotDialog = false
                            }
                        },
                        enabled = snapshotName.isNotBlank()
                    ) {
                        Text("Create")
                    }
                },
                dismissButton = {
                    TextButton(onClick = {
                        showCreateSnapshotDialog = false
                        snapshotName = ""
                        snapshotDescription = ""
                    }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Clone Dialog
        if (showCloneDialog) {
            AlertDialog(
                onDismissRequest = { showCloneDialog = false },
                title = { Text("Clone ${if (isQemu) "VM" else "CT"} $vmid") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = cloneNewId,
                            onValueChange = { cloneNewId = it.filter { c -> c.isDigit() } },
                            label = { Text("New ID") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cloneName,
                            onValueChange = { cloneName = it },
                            label = { Text("Name") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Text("Full Clone", fontSize = 14.sp)
                            Checkbox(checked = cloneFullClone, onCheckedChange = { cloneFullClone = it })
                        }
                        Spacer(Modifier.height(4.dp))
                        OutlinedTextField(
                            value = cloneTargetNode,
                            onValueChange = { cloneTargetNode = it },
                            label = { Text("Target Node (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = cloneTargetStorage,
                            onValueChange = { cloneTargetStorage = it },
                            label = { Text("Target Storage (optional)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val newIdInt = cloneNewId.toIntOrNull()
                            if (newIdInt != null && cloneName.isNotBlank()) {
                                if (isQemu) {
                                    viewModel.cloneVM(
                                        node = node,
                                        vmid = vmid,
                                        newId = newIdInt,
                                        name = cloneName.trim(),
                                        fullClone = cloneFullClone,
                                        targetNode = cloneTargetNode.takeIf { it.isNotBlank() },
                                        targetStorage = cloneTargetStorage.takeIf { it.isNotBlank() }
                                    ) { showCloneDialog = false }
                                } else {
                                    viewModel.cloneLXC(
                                        node = node,
                                        vmid = vmid,
                                        newId = newIdInt,
                                        name = cloneName.trim(),
                                        targetNode = cloneTargetNode.takeIf { it.isNotBlank() },
                                        targetStorage = cloneTargetStorage.takeIf { it.isNotBlank() }
                                    ) { showCloneDialog = false }
                                }
                            }
                        },
                        enabled = cloneNewId.isNotBlank() && cloneName.isNotBlank()
                    ) {
                        Text("Clone")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCloneDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Migrate Dialog
        if (showMigrateDialog) {
            AlertDialog(
                onDismissRequest = { showMigrateDialog = false },
                title = { Text("Migrate ${if (isQemu) "VM" else "CT"} $vmid") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = migrateTargetNode,
                            onValueChange = { migrateTargetNode = it },
                            label = { Text("Target Node") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        if (isQemu) {
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text("Online Migration", fontSize = 14.sp)
                                Checkbox(checked = migrateOnline, onCheckedChange = { migrateOnline = it })
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            if (migrateTargetNode.isNotBlank()) {
                                if (isQemu) {
                                    viewModel.migrateVM(
                                        node = node,
                                        vmid = vmid,
                                        targetNode = migrateTargetNode.trim(),
                                        online = migrateOnline
                                    ) { showMigrateDialog = false }
                                } else {
                                    viewModel.migrateLXC(
                                        node = node,
                                        vmid = vmid,
                                        targetNode = migrateTargetNode.trim()
                                    ) { showMigrateDialog = false }
                                }
                            }
                        },
                        enabled = migrateTargetNode.isNotBlank()
                    ) {
                        Text("Migrate")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showMigrateDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Edit Notes Dialog
        if (showEditNotesDialog) {
            AlertDialog(
                onDismissRequest = { showEditNotesDialog = false },
                title = { Text("Edit Notes - ${if (isQemu) "VM" else "CT"} $vmid") },
                text = {
                    Column {
                        OutlinedTextField(
                            value = editNotesText,
                            onValueChange = { editNotesText = it },
                            label = { Text("Notes / Description") },
                            modifier = Modifier.fillMaxWidth(),
                            maxLines = 8,
                            minLines = 4
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.updateGuestDescription(
                                node = node,
                                vmid = vmid,
                                isQemu = isQemu,
                                description = editNotesText.trim()
                            ) {
                                showEditNotesDialog = false
                            }
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditNotesDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }

        // Clone/Migrate Success/Error Alerts
        cloneSuccessMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { cloneSuccessMessage = null },
                title = { Text("Task Started") },
                text = { Text("Clone/Migrate task started: $msg") },
                confirmButton = {
                    TextButton(onClick = { cloneSuccessMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }
        cloneErrorMessage?.let { msg ->
            AlertDialog(
                onDismissRequest = { cloneErrorMessage = null },
                title = { Text("Error") },
                text = { Text(msg, color = Color.Red) },
                confirmButton = {
                    TextButton(onClick = { cloneErrorMessage = null }) {
                        Text("OK")
                    }
                }
            )
        }
    }
}

// ---------------------------------------------------------------------------
// Overview Tab
// ---------------------------------------------------------------------------
@Composable
private fun GuestOverviewTab(
    data: ProxmoxGuestDetailData,
    node: String,
    isQemu: Boolean,
    guestColor: Color,
    isDark: Boolean,
    isRefreshing: Boolean,
    onRefresh: () -> Unit,
    onAction: (ProxmoxGuestAction) -> Unit,
    onNavigateToConsole: ((String, Int, Boolean) -> Unit)?,
    onNavigateToConfig: ((String, Int, Boolean) -> Unit)?,
    onCloneClick: () -> Unit,
    onMigrateClick: () -> Unit,
    onEditNotesClick: () -> Unit
) {
    PullToRefreshBox(
        isRefreshing = isRefreshing,
        onRefresh = onRefresh,
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            modifier = Modifier.animateContentSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Card
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, guestColor))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    if (isQemu) Icons.Default.Computer else Icons.Default.Storage,
                                    contentDescription = null,
                                    tint = if (data.isRunning) guestColor else Color.Gray,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Column {
                                    Text(data.name, fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("Node: $node", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(10.dp)
                                        .clip(CircleShape)
                                        .background(if (data.isRunning) Color.Green else if (data.isStopped) Color.Red else Color.Yellow)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (data.isRunning) "Running" else if (data.isStopped) "Stopped" else "Unknown",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (data.isRunning) Color.Green else if (data.isStopped) Color.Red else Color.Gray
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        if (data.uptime != "-") {
                            Text("Uptime: ${data.uptime}", fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }

            // Resource Bars
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, guestColor))
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Resources", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(12.dp))
                        ProgressBar(label = "CPU", percent = data.cpuPercent, color = guestColor)
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(label = "RAM", percent = data.memPercent, color = Color.Blue)
                        Spacer(Modifier.height(10.dp))
                        ProgressBar(label = "Disk", percent = data.diskPercent, color = Color(0xFF9C27B0))
                    }
                }
            }

            // Action Buttons
            if (data.isRunning || data.isStopped) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, guestColor))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Actions", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (data.isStopped) {
                                    ActionButton(
                                        label = stringResource(R.string.proxmox_start),
                                        icon = Icons.Default.PlayArrow,
                                        color = Color.Green,
                                        onClick = { onAction(ProxmoxGuestAction.START) },
                                        modifier = Modifier.weight(1f)
                                    )
                                } else {
                                    ActionButton(
                                        label = stringResource(R.string.proxmox_shutdown),
                                        icon = Icons.Default.PowerSettingsNew,
                                        color = Color(0xFFFF9800),
                                        onClick = { onAction(ProxmoxGuestAction.SHUTDOWN) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionButton(
                                        label = stringResource(R.string.proxmox_stop),
                                        icon = Icons.Default.Stop,
                                        color = Color.Red,
                                        onClick = { onAction(ProxmoxGuestAction.STOP) },
                                        modifier = Modifier.weight(1f)
                                    )
                                    ActionButton(
                                        label = stringResource(R.string.proxmox_reboot),
                                        icon = Icons.Default.RestartAlt,
                                        color = Color.Blue,
                                        onClick = { onAction(ProxmoxGuestAction.REBOOT) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Console & Config Buttons
            if (data.isRunning) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, guestColor))
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text("Tools", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                if (onNavigateToConsole != null) {
                                    ActionButton(
                                        label = "Console",
                                        icon = Icons.Default.DesktopWindows,
                                        color = guestColor,
                                        onClick = { onNavigateToConsole(node, data.vmid, isQemu) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                                if (onNavigateToConfig != null) {
                                    ActionButton(
                                        label = "Edit Config",
                                        icon = Icons.Default.Settings,
                                        color = Color(0xFF607D8B),
                                        onClick = { onNavigateToConfig(node, data.vmid, isQemu) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ActionButton(
                                    label = "Clone",
                                    icon = Icons.Default.ContentCopy,
                                    color = Color(0xFF9C27B0),
                                    onClick = onCloneClick,
                                    modifier = Modifier.weight(1f)
                                )
                                ActionButton(
                                    label = "Migrate",
                                    icon = Icons.Default.SwapHoriz,
                                    color = Color(0xFF00BCD4),
                                    onClick = onMigrateClick,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(Modifier.fillMaxWidth()) {
                                ActionButton(
                                    label = "Edit Notes",
                                    icon = Icons.Default.EditNote,
                                    color = Color(0xFF795548),
                                    onClick = onEditNotesClick,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Snapshots Tab
// ---------------------------------------------------------------------------
@Composable
private fun GuestSnapshotsTab(
    snapshots: List<ProxmoxSnapshot>,
    guestColor: Color,
    isDark: Boolean,
    onCreateSnapshot: () -> Unit,
    onDelete: (String) -> Unit,
    onRollback: (String) -> Unit
) {
    PullToRefreshBox(
        isRefreshing = false,
        onRefresh = {},
        modifier = Modifier.fillMaxSize()
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Snapshots (${snapshots.size})", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    TextButton(onClick = onCreateSnapshot) {
                        Text("Create", fontSize = 12.sp, color = guestColor)
                    }
                }
            }
            if (snapshots.isEmpty()) {
                item {
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Text("No snapshots", Modifier.padding(16.dp), color = Color.Gray)
                    }
                }
            } else {
                items(snapshots, key = { it.name }) { snapshot ->
                    SnapshotRow(
                        snapshot = snapshot,
                        isDark = isDark,
                        onDelete = { onDelete(snapshot.name) },
                        onRollback = { onRollback(snapshot.name) }
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Console Tab
// ---------------------------------------------------------------------------
@Composable
private fun GuestConsoleTab(
    node: String,
    vmid: Int,
    isQemu: Boolean,
    onNavigateToFullConsole: ((String, Int, Boolean) -> Unit)?,
    viewModel: ProxmoxViewModel
) {
    var consoleInitialized by remember { mutableStateOf(false) }
    val vncTicketState by viewModel.vncTicketState.collectAsStateWithLifecycle()
    var webViewReady by remember { mutableStateOf(false) }
    var loadingError by remember { mutableStateOf<String?>(null) }
    var sslError by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        if (!consoleInitialized) {
            viewModel.fetchVncTicket(node, vmid, isQemu)
            consoleInitialized = true
        }
    }

    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize()) {
        if (onNavigateToFullConsole != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { onNavigateToFullConsole(node, vmid, isQemu) }) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Open Full Console")
                }
            }
        }

        Box(modifier = Modifier.fillMaxSize().padding(bottom = if (onNavigateToFullConsole != null) 0.dp else 0.dp)) {
            when (val state = vncTicketState) {
                is UiState.Idle,
                is UiState.Loading,
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(16.dp))
                            Text("Fetching VNC ticket...", color = Color.Gray)
                        }
                    }
                }
                is UiState.Error -> {
                    Column(
                        modifier = Modifier.fillMaxSize().padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                        Spacer(Modifier.height(16.dp))
                        Text(stringResource(R.string.proxmox_failed_fetch_vnc_ticket), color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Spacer(Modifier.height(8.dp))
                        Text(state.message, color = Color.Gray, fontSize = 14.sp)
                        Spacer(Modifier.height(24.dp))
                        Button(onClick = { viewModel.fetchVncTicket(node, vmid, isQemu) }) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Retry")
                        }
                    }
                }
                is UiState.Success -> {
                    val ticketData = state.data

                    if (sslError) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFFF9800), modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("SSL Certificate Error", color = Color(0xFFFF9800), fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text(
                                "The WebView could not verify the server's SSL certificate.",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { viewModel.fetchVncTicket(node, vmid, isQemu); sslError = false }) {
                                Text("Retry")
                            }
                        }
                    } else if (loadingError != null) {
                        Column(
                            modifier = Modifier.fillMaxSize().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                            Spacer(Modifier.height(16.dp))
                            Text("Failed to load console", color = Color.Red, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.height(8.dp))
                            Text(loadingError!!, color = Color.Gray, fontSize = 14.sp)
                            Spacer(Modifier.height(24.dp))
                            Button(onClick = { viewModel.fetchVncTicket(node, vmid, isQemu); loadingError = null }) {
                                Text("Retry")
                            }
                        }
                    } else {
                        LaunchedEffect(ticketData.ticket) {
                            val cookieManager = android.webkit.CookieManager.getInstance()
                            cookieManager.setAcceptCookie(true)
                            cookieManager.setAcceptThirdPartyCookies(android.webkit.WebView(context), true)
                            val baseUri = runCatching { java.net.URI(ticketData.baseUrl) }.getOrNull()
                            val cookieOrigin = baseUri?.scheme?.takeIf { it.isNotBlank() }?.let { scheme ->
                                val authority = baseUri.authority?.takeIf { it.isNotBlank() } ?: return@let null
                                "$scheme://$authority"
                            }
                            if (!cookieOrigin.isNullOrBlank()) {
                                val secureAttribute = if (baseUri?.scheme.equals("https", ignoreCase = true)) "; Secure" else ""
                                cookieManager.setCookie(
                                    cookieOrigin,
                                    "PVEAuthCookie=${ticketData.ticket}; Path=/$secureAttribute"
                                )
                                cookieManager.flush()
                            }
                        }

                        AndroidView(
                            factory = { ctx ->
                                android.webkit.WebView(ctx).apply {
                                    settings.javaScriptEnabled = true
                                    settings.domStorageEnabled = true
                                    settings.useWideViewPort = true
                                    settings.loadWithOverviewMode = true
                                    settings.setSupportZoom(true)
                                    settings.builtInZoomControls = true
                                    settings.displayZoomControls = false
                                    webViewClient = object : android.webkit.WebViewClient() {
                                        override fun onPageStarted(view: android.webkit.WebView?, url: String?, favicon: android.graphics.Bitmap?) {
                                            super.onPageStarted(view, url, favicon)
                                            loadingError = null
                                            sslError = false
                                        }

                                        override fun onPageFinished(view: android.webkit.WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            webViewReady = true
                                        }

                                        override fun onReceivedError(
                                            view: android.webkit.WebView?,
                                            request: android.webkit.WebResourceRequest?,
                                            error: android.webkit.WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                loadingError = error?.description?.toString() ?: context.getString(R.string.error_unknown)
                                            }
                                        }

                                        @Suppress("DEPRECATION")
                                        override fun onReceivedSslError(
                                            view: android.webkit.WebView?,
                                            handler: android.webkit.SslErrorHandler?,
                                            error: android.net.http.SslError?
                                        ) {
                                            super.onReceivedSslError(view, handler, error)
                                            sslError = true
                                            handler?.cancel()
                                        }
                                    }
                                    loadUrl(ticketData.buildConsoleUrl())
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        if (!webViewReady && loadingError == null && !sslError) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator()
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Logs Tab
// ---------------------------------------------------------------------------
@Composable
private fun GuestLogsTab(
    node: String,
    vmid: Int,
    isQemu: Boolean,
    onNavigateToTaskLog: ((String, String) -> Unit)?,
    viewModel: ProxmoxViewModel
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Task Log", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ListAlt,
                    contentDescription = null,
                    tint = Color.Gray,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(Modifier.height(12.dp))
                Text(
                    "View the full task log for this ${if (isQemu) "VM" else "container"}.",
                    color = Color.Gray,
                    fontSize = 13.sp
                )
                Spacer(Modifier.height(16.dp))
                if (onNavigateToTaskLog != null) {
                    // Note: Task log navigation requires a valid UPID. 
                    // This button navigates to the task list for the current node where user can select a specific task.
                    Text("Task log available for running/recent tasks", color = Color.Gray, fontSize = 11.sp)
                } else {
                    Text(
                        "Task log navigation not available",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Shared Composables
// ---------------------------------------------------------------------------
@Composable
private fun ProgressBar(label: String, percent: Double, color: Color) {
    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 11.sp, color = Color.Gray)
            Text("${String.format("%.0f", percent)}%", fontSize = 11.sp, color = color, fontWeight = FontWeight.Medium)
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

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.outlinedButtonColors(contentColor = color)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(4.dp))
            Text(label, fontSize = 11.sp)
        }
    }
}

@Composable
private fun SnapshotRow(
    snapshot: ProxmoxSnapshot,
    isDark: Boolean,
    onDelete: () -> Unit,
    onRollback: () -> Unit
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var showRollbackConfirm by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = if (isDark) Color.DarkGray.copy(alpha = 0.3f) else Color.LightGray.copy(alpha = 0.3f))
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF9C27B0), modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Column(Modifier.weight(1f)) {
                    Text(snapshot.name, fontSize = 12.sp, fontWeight = FontWeight.Medium)
                    if (!snapshot.description.isNullOrBlank()) {
                        Text(snapshot.description, fontSize = 10.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    Text(snapshot.formattedTime, fontSize = 9.sp, color = Color.Gray)
                }
                if (snapshot.vmstate == 1) {
                    Text("RAM", fontSize = 9.sp, color = Color.Blue, fontWeight = FontWeight.Medium)
                }
                Spacer(Modifier.width(4.dp))
                IconButton(onClick = { showRollbackConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.RestartAlt, contentDescription = "Rollback", tint = Color(0xFFFF9800), modifier = Modifier.size(16.dp))
                }
                IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Delete Snapshot") },
            text = { Text("Are you sure you want to delete snapshot '${snapshot.name}'?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete()
                    showDeleteConfirm = false
                }) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRollbackConfirm) {
        AlertDialog(
            onDismissRequest = { showRollbackConfirm = false },
            title = { Text("Rollback Snapshot") },
            text = { Text("Are you sure you want to rollback to snapshot '${snapshot.name}'? This will restart the VM.") },
            confirmButton = {
                TextButton(onClick = {
                    onRollback()
                    showRollbackConfirm = false
                }) {
                    Text("Rollback", color = Color(0xFFFF9800))
                }
            },
            dismissButton = {
                TextButton(onClick = { showRollbackConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
