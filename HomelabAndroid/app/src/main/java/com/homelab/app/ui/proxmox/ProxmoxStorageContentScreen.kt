package com.homelab.app.ui.proxmox

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
import com.homelab.app.data.remote.dto.proxmox.ProxmoxStorageContent
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

private fun proxmoxCardColor(isDarkTheme: Boolean, accent: Color): Color =
    accent.copy(alpha = if (isDarkTheme) 0.018f else 0.020f)
        .copy(alpha = if (isDarkTheme) 0.85f else 0.95f)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProxmoxStorageContentScreen(
    node: String,
    storage: String,
    onNavigateBack: () -> Unit,
    viewModel: ProxmoxViewModel = hiltViewModel()
) {
    val storageContentState by viewModel.storageContentState.collectAsStateWithLifecycle()
    val isDark = isThemeDark()
    val serviceColor = ServiceType.PROXMOX.primaryColor
    var isRefreshing by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<ProxmoxStorageContent?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(node, storage) {
        viewModel.fetchStorageContent(node, storage)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Storage: $storage") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchStorageContent(node, storage) }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            when (val state = storageContentState) {
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
                        onRetry = { viewModel.fetchStorageContent(node, storage) }
                    )
                }
                is UiState.Offline -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No internet connection", color = Color.Gray)
                    }
                }
                is UiState.Success -> {
                    val content = state.data
                    if (content.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(8.dp))
                                Text("No content in this storage", color = Color.Gray)
                            }
                        }
                    } else {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing,
                            onRefresh = {
                                isRefreshing = true
                                scope.launch {
                                    viewModel.fetchStorageContent(node, storage)
                                    isRefreshing = false
                                }
                            },
                            modifier = Modifier.fillMaxSize()
                        ) {
                            LazyColumn(
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(content, key = { it.volid }) { item ->
                                    StorageContentRow(
                                        content = item,
                                        color = serviceColor,
                                        isDark = isDark,
                                        onDelete = { showDeleteDialog = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Delete confirmation dialog
    showDeleteDialog?.let { item ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Volume") },
            text = { Text("Are you sure you want to delete \"${item.volid}\"? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.deleteStorageContent(
                            node = node,
                            storage = storage,
                            volume = item.volid,
                            onSuccess = {
                                scope.launch {
                                    snackbarHostState.showSnackbar("Volume deleted successfully")
                                }
                                showDeleteDialog = null
                            }
                        )
                    }
                ) {
                    Text("Delete", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun StorageContentRow(content: ProxmoxStorageContent, color: Color, isDark: Boolean, onDelete: () -> Unit) {
    var showMenu by remember { mutableStateOf(false) }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = proxmoxCardColor(isDark, color))
    ) {
        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            // Icon based on content type
            Icon(
                contentTypeIcon(content.content),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(content.volid, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (content.isProtected) {
                        Spacer(Modifier.width(6.dp))
                        Icon(Icons.Default.Lock, contentDescription = "Protected", tint = Color(0xFFFF9800), modifier = Modifier.size(12.dp))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(content.content ?: "-", fontSize = 10.sp, color = Color.Gray)
                    if (content.vmid != null) {
                        Text("  VM: ${content.vmid}", fontSize = 10.sp, color = Color.Gray)
                    }
                    if (!content.format.isNullOrBlank()) {
                        Text("  Format: ${content.format}", fontSize = 10.sp, color = Color.Gray)
                    }
                }
                if (!content.notes.isNullOrBlank()) {
                    Text(content.notes, fontSize = 9.sp, color = Color.Gray, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(content.formattedSize, fontSize = 11.sp, fontWeight = FontWeight.Medium, color = color)
                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Actions", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("Delete", color = Color.Red) },
                            leadingIcon = {
                                Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(18.dp))
                            },
                            onClick = {
                                showMenu = false
                                onDelete()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun contentTypeIcon(contentType: String?): androidx.compose.ui.graphics.vector.ImageVector {
    return when (contentType?.lowercase()) {
        "images" -> Icons.Default.Computer
        "iso" -> Icons.Default.DiscFull
        "backup", "vztmpl" -> Icons.Default.Folder
        "rootdir" -> Icons.Default.Storage
        else -> Icons.Default.Description
    }
}
