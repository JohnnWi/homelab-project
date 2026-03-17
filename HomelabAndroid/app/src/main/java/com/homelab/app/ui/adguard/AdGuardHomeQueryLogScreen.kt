package com.homelab.app.ui.adguard

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import com.homelab.app.data.remote.dto.adguard.AdGuardQueryLogEntry
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.util.UiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdGuardHomeQueryLogScreen(
    initialStatus: String,
    onNavigateBack: () -> Unit,
    viewModel: AdGuardHomeViewModel = hiltViewModel()
) {
    val queryState by viewModel.queryLogState.collectAsStateWithLifecycle()

    var search by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(initialStatus) }
    var clientFilter by remember { mutableStateOf("all") }

    LaunchedEffect(search) {
        delay(350)
        val trimmed = search.trim().takeIf { it.isNotBlank() }
        viewModel.fetchQueryLog(search = trimmed, status = null)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adguard_query_log), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = search,
                onValueChange = { search = it },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                label = { Text(stringResource(R.string.adguard_filter_domain_or_client)) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                shape = RoundedCornerShape(14.dp),
                singleLine = true
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item {
                    FilterChip(
                        selected = statusFilter == "all",
                        onClick = { statusFilter = "all" },
                        label = { Text(stringResource(R.string.all)) }
                    )
                }
                item {
                    FilterChip(
                        selected = statusFilter == "blocked",
                        onClick = { statusFilter = "blocked" },
                        label = { Text(stringResource(R.string.adguard_blocked)) }
                    )
                }
                item {
                    FilterChip(
                        selected = statusFilter == "processed",
                        onClick = { statusFilter = "processed" },
                        label = { Text(stringResource(R.string.adguard_allowed)) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (val state = queryState) {
                is UiState.Loading, is UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = { state.retryAction?.invoke() },
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is UiState.Offline -> {
                    ErrorScreen(
                        message = "",
                        onRetry = { viewModel.fetchQueryLog(search = search.trim().takeIf { it.isNotBlank() }, status = statusFilter.takeIf { it != "all" }) },
                        isOffline = true,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                is UiState.Success -> {
                    val entries = state.data
                    val clientOptions = remember(entries) {
                        entries.mapNotNull { it.client.takeIf { name -> name.isNotBlank() } }
                            .distinct()
                            .take(6)
                    }
                    LaunchedEffect(clientOptions) {
                        if (clientFilter != "all" && clientFilter !in clientOptions) {
                            clientFilter = "all"
                        }
                    }
                    val filtered = entries.filter { entry ->
                        val statusOk = when (statusFilter) {
                            "blocked" -> entry.blocked
                            "processed" -> !entry.blocked
                            else -> true
                        }
                        val clientOk = clientFilter == "all" || entry.client == clientFilter
                        statusOk && clientOk
                    }

                    if (clientOptions.isNotEmpty()) {
                        ClientFilterRow(
                            options = clientOptions,
                            selected = clientFilter,
                            onSelect = { clientFilter = it }
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    if (filtered.isEmpty()) {
                        AdGuardEmptyState(
                            text = stringResource(R.string.adguard_no_query_entries),
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { entry ->
                                QueryLogRow(entry = entry, onAllow = { viewModel.allowQuery(entry.domain) })
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryLogRow(entry: AdGuardQueryLogEntry, onAllow: () -> Unit) {
    val accent = if (entry.blocked) StatusRed else StatusGreen

    AdGuardGlassCard(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.18f), modifier = Modifier.size(36.dp)) {
                Icon(
                    imageVector = if (entry.blocked) Icons.Default.Warning else Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.domain, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(entry.client, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (!entry.reason.isNullOrBlank()) {
                    Text(entry.reason ?: "", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (entry.blocked) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = StatusGreen.copy(alpha = 0.16f),
                    modifier = Modifier.clickable(onClick = onAllow)
                ) {
                    Text(
                        text = stringResource(R.string.adguard_allow),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        color = StatusGreen,
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

@Composable
private fun ClientFilterRow(
    options: List<String>,
    selected: String,
    onSelect: (String) -> Unit
) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = selected == "all",
                onClick = { onSelect("all") },
                label = { Text(stringResource(R.string.adguard_all_clients)) }
            )
        }
        items(options, key = { it }) { client ->
            FilterChip(
                selected = selected == client,
                onClick = { onSelect(client) },
                label = { Text(client, maxLines = 1, overflow = TextOverflow.Ellipsis) }
            )
        }
    }
}
