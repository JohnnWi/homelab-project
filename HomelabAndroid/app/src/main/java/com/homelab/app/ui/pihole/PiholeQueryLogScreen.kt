package com.homelab.app.ui.pihole

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.background
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.IconButton
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
import com.homelab.app.data.remote.dto.pihole.PiholeQueryLogEntry
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import com.homelab.app.ui.common.ErrorScreen
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class QueryLogStatusFilter(val labelRes: Int) {
    ALL(R.string.all),
    BLOCKED(R.string.pihole_filter_blocked),
    ALLOWED(R.string.pihole_filter_allowed)
}

private const val ALL_CLIENT = "__all__"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiholeQueryLogScreen(
    onNavigateBack: () -> Unit,
    viewModel: PiholeViewModel = hiltViewModel()
) {
    val queriesState by viewModel.queriesState.collectAsStateWithLifecycle()
    val queryLog = (queriesState as? UiState.Success)?.data ?: emptyList()

    var searchText by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(QueryLogStatusFilter.ALL) }
    var selectedClient by remember { mutableStateOf(ALL_CLIENT) }
    var clientMenuExpanded by remember { mutableStateOf(false) }

    val clients = remember(queryLog) {
        listOf(ALL_CLIENT) + queryLog.map { it.client }.filter { it.isNotBlank() && it != "unknown" }.distinct().sorted()
    }
    LaunchedEffect(clients) {
        if (!clients.contains(selectedClient)) {
            selectedClient = ALL_CLIENT
        }
    }

    val filtered = remember(queryLog, searchText, statusFilter, selectedClient) {
        queryLog.filter { entry ->
            val matchesStatus = when (statusFilter) {
                QueryLogStatusFilter.ALL -> true
                QueryLogStatusFilter.BLOCKED -> entry.isBlocked
                QueryLogStatusFilter.ALLOWED -> !entry.isBlocked
            }
            val matchesClient = selectedClient == ALL_CLIENT || entry.client == selectedClient
            val q = searchText.trim().lowercase()
            val matchesSearch = q.isEmpty() || entry.domain.lowercase().contains(q) || entry.client.lowercase().contains(q)
            matchesStatus && matchesClient && matchesSearch
        }
    }

    LaunchedEffect(Unit) {
        while (true) {
            viewModel.fetchRecentQueries()
            delay(5000)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.pihole_query_log), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        }
    ) { padding ->
        when (val state = queriesState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ServiceType.PIHOLE.primaryColor)
                }
            }
            is UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { viewModel.fetchRecentQueries() },
                    modifier = Modifier.padding(padding)
                )
            }
            is UiState.Offline -> {
                ErrorScreen(
                    message = "",
                    onRetry = { viewModel.fetchRecentQueries() },
                    isOffline = true,
                    modifier = Modifier.padding(padding)
                )
            }
            is UiState.Success -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.pihole_filter_search)) },
                        singleLine = true
                    )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QueryLogStatusFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = statusFilter == filter,
                        onClick = { statusFilter = filter },
                        label = { Text(stringResource(filter.labelRes)) }
                    )
                }
            }

            Box {
                AssistChip(
                    onClick = { clientMenuExpanded = true },
                    label = { Text(stringResource(R.string.pihole_filter_client, if (selectedClient == ALL_CLIENT) stringResource(R.string.all) else selectedClient)) }
                )
                DropdownMenu(
                    expanded = clientMenuExpanded,
                    onDismissRequest = { clientMenuExpanded = false }
                ) {
                    clients.forEach { client ->
                        DropdownMenuItem(
                            text = { Text(if (client == ALL_CLIENT) stringResource(R.string.all) else client) },
                            onClick = {
                                selectedClient = client
                                clientMenuExpanded = false
                            }
                        )
                    }
                }
            }

                    if (filtered.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(
                                text = stringResource(R.string.pihole_no_query_results),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(bottom = 16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            items(filtered, key = { it.id }) { entry ->
                                QueryLogRow(entry = entry)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QueryLogRow(entry: PiholeQueryLogEntry) {
    val statusColor = if (entry.isBlocked) StatusRed else StatusGreen

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = entry.domain,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = entry.client,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = localizedStatus(entry.status),
                    style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = statusColor
                )
                Text(
                    text = formatUnix(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = StatusBlue
                )
            }
        }
    }
}

private fun formatUnix(ts: Long): String {
    val formatter = SimpleDateFormat("dd MMM HH:mm:ss", Locale.getDefault())
    return formatter.format(Date(ts * 1000))
}

@Composable
private fun localizedStatus(raw: String): String {
    return when (raw.lowercase()) {
        "blocked" -> stringResource(R.string.pihole_filter_blocked)
        "allowed" -> stringResource(R.string.pihole_filter_allowed)
        "cached" -> stringResource(R.string.pihole_cached)
        else -> raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() }
    }
}
