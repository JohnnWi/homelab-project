package com.homelab.app.ui.adguard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdGuardHomeUserRulesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdGuardHomeViewModel = hiltViewModel()
) {
    val rulesState by viewModel.userRulesState.collectAsStateWithLifecycle()

    var showAddDialog by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        viewModel.fetchUserRules()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adguard_user_rules), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.adguard_add_rule))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = rulesState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                ErrorScreen(message = state.message, onRetry = { state.retryAction?.invoke() }, modifier = Modifier.padding(paddingValues))
            }
            is UiState.Offline -> {
                ErrorScreen(message = "", onRetry = { viewModel.fetchUserRules() }, isOffline = true, modifier = Modifier.padding(paddingValues))
            }
            is UiState.Success -> {
                val rules = state.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (rules.isEmpty()) {
                        item {
                            Text(stringResource(R.string.adguard_no_user_rules), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(rules, key = { it }) { rule ->
                            val dismissState = rememberSwipeToDismissBoxState()
                            val isActive = dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                                dismissState.targetValue != SwipeToDismissBoxValue.Settled
                            LaunchedEffect(dismissState.currentValue) {
                                if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                    pendingDelete = rule
                                    dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                                }
                            }
                            SwipeToDismissBox(
                                state = dismissState,
                                backgroundContent = { AdGuardDeleteBackground(isActive = isActive) },
                                enableDismissFromStartToEnd = false
                            ) {
                                UserRuleRow(rule = rule)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AddRuleDialog(
            onDismiss = { showAddDialog = false },
            onAdd = { rule ->
                viewModel.addUserRule(rule)
                showAddDialog = false
            }
        )
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.adguard_delete_rule)) },
            text = { Text(pendingDelete.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { viewModel.removeUserRule(it) }
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
private fun UserRuleRow(rule: String) {
    val isAllow = rule.trim().startsWith("@@")
    val accent = if (isAllow) StatusGreen else StatusRed

    AdGuardGlassCard(shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                Icon(if (isAllow) Icons.Default.Done else Icons.Default.Warning, contentDescription = null, tint = accent, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    cleanRule(rule),
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(shape = RoundedCornerShape(8.dp), color = accent.copy(alpha = 0.16f)) {
                Text(
                    text = if (isAllow) stringResource(R.string.adguard_allow) else stringResource(R.string.adguard_blocked_label),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = accent
                )
            }
        }
    }
}

@Composable
private fun AddRuleDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var rule by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adguard_add_rule)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = rule, onValueChange = { rule = it }, label = { Text(stringResource(R.string.adguard_rule)) }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            TextButton(onClick = { onAdd(rule.trim()) }, enabled = rule.isNotBlank()) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private fun cleanRule(rule: String): String {
    var cleaned = rule.trim()
    if (cleaned.startsWith("@@||")) cleaned = cleaned.removePrefix("@@||")
    else if (cleaned.startsWith("@@|")) cleaned = cleaned.removePrefix("@@|")
    else if (cleaned.startsWith("@@")) cleaned = cleaned.removePrefix("@@")
    if (cleaned.startsWith("||")) cleaned = cleaned.removePrefix("||")
    if (cleaned.startsWith("|")) cleaned = cleaned.removePrefix("|")
    if (cleaned.endsWith("^")) cleaned = cleaned.removeSuffix("^")
    return cleaned.trim()
}
