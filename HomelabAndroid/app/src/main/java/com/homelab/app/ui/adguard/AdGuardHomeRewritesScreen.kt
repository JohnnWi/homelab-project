package com.homelab.app.ui.adguard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.adguard.AdGuardRewriteEntry
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdGuardHomeRewritesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdGuardHomeViewModel = hiltViewModel()
) {
    val rewritesState by viewModel.rewritesState.collectAsStateWithLifecycle()
    val rewriteSettings by viewModel.rewriteSettings.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var editingEntry by remember { mutableStateOf<AdGuardRewriteEntry?>(null) }
    var pendingDelete by remember { mutableStateOf<AdGuardRewriteEntry?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchRewrites()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adguard_dns_rewrites), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.adguard_add_rewrite))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = rewritesState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                ErrorScreen(message = state.message, onRetry = { state.retryAction?.invoke() }, modifier = Modifier.padding(paddingValues))
            }
            is UiState.Offline -> {
                ErrorScreen(message = "", onRetry = { viewModel.fetchRewrites() }, isOffline = true, modifier = Modifier.padding(paddingValues))
            }
            is UiState.Success -> {
                val entries = state.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        RewriteSettingsCard(
                            enabled = rewriteSettings?.enabled ?: true,
                            onToggle = { viewModel.toggleRewriteSettings(it) }
                        )
                    }

                    items(entries, key = { it.domain + it.answer }) { entry ->
                        val dismissState = rememberSwipeToDismissBoxState()
                        val isActive = dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                            dismissState.targetValue != SwipeToDismissBoxValue.Settled
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = entry
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = { AdGuardDeleteBackground(isActive = isActive) },
                            enableDismissFromStartToEnd = false
                        ) {
                            RewriteRow(
                                entry = entry,
                                onEdit = { editingEntry = entry },
                                onToggle = { enabled -> viewModel.updateRewrite(entry, entry.copy(enabled = enabled)) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        RewriteEditDialog(
            title = stringResource(R.string.adguard_add_rewrite),
            initialDomain = "",
            initialAnswer = "",
            initialEnabled = true,
            onDismiss = { showAddDialog = false },
            onSave = { domain, answer, enabled ->
                viewModel.addRewrite(domain, answer, enabled)
                showAddDialog = false
            }
        )
    }

    if (editingEntry != null) {
        val entry = editingEntry!!
        RewriteEditDialog(
            title = stringResource(R.string.adguard_edit_rewrite),
            initialDomain = entry.domain,
            initialAnswer = entry.answer,
            initialEnabled = entry.enabled ?: true,
            onDismiss = { editingEntry = null },
            onSave = { domain, answer, enabled ->
                viewModel.updateRewrite(entry, entry.copy(domain = domain, answer = answer, enabled = enabled))
                editingEntry = null
            }
        )
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.adguard_delete_rewrite)) },
            text = { Text(pendingDelete?.domain.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { viewModel.deleteRewrite(it) }
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun RewriteSettingsCard(enabled: Boolean, onToggle: (Boolean) -> Unit) {
    AdGuardGlassCard(shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = StatusBlue.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Link, contentDescription = null, tint = StatusBlue, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.adguard_rewrites_enabled), style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold))
                Text(stringResource(R.string.adguard_apply_rewrites), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun RewriteRow(entry: AdGuardRewriteEntry, onEdit: () -> Unit, onToggle: (Boolean) -> Unit) {
    AdGuardGlassCard(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable { onEdit() }) {
                Text(entry.domain, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.answer, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Switch(checked = entry.enabled ?: true, onCheckedChange = onToggle)
        }
    }
}

@Composable
private fun RewriteEditDialog(
    title: String,
    initialDomain: String,
    initialAnswer: String,
    initialEnabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var domain by remember { mutableStateOf(initialDomain) }
    var answer by remember { mutableStateOf(initialAnswer) }
    var enabled by remember { mutableStateOf(initialEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = domain, onValueChange = { domain = it }, label = { Text(stringResource(R.string.adguard_domain)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = answer, onValueChange = { answer = it }, label = { Text(stringResource(R.string.adguard_answer)) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Text(if (enabled) stringResource(R.string.adguard_enabled) else stringResource(R.string.adguard_disabled))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(domain.trim(), answer.trim(), enabled) }, enabled = domain.isNotBlank() && answer.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}
