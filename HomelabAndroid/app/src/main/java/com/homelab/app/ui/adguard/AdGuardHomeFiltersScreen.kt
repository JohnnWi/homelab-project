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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.automirrored.filled.ListAlt
import androidx.compose.material.icons.automirrored.filled.Rule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.adguard.AdGuardFilter
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdGuardHomeFiltersScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdGuardHomeViewModel = hiltViewModel()
) {
    val filtersState by viewModel.filtersState.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<AdGuardFilter?>(null) }
    var pendingWhitelist by remember { mutableStateOf(false) }
    var editingFilter by remember { mutableStateOf<AdGuardFilter?>(null) }
    var editingWhitelist by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.fetchFilters()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adguard_filter_lists), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = stringResource(R.string.adguard_add_list))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        when (val state = filtersState) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                ErrorScreen(
                    message = state.message,
                    onRetry = { state.retryAction?.invoke() ?: viewModel.fetchFilters() },
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Offline -> {
                ErrorScreen(
                    message = "",
                    onRetry = { viewModel.fetchFilters() },
                    isOffline = true,
                    modifier = Modifier.padding(paddingValues)
                )
            }
            is UiState.Success -> {
                val status = state.data
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        FiltersSummary(status)
                    }
                    item {
                        AdGuardSectionHeader(title = stringResource(R.string.adguard_blocklists))
                    }
                    items(status.filters, key = { it.id }) { filter ->
                        val dismissState = rememberSwipeToDismissBoxState()
                        val isActive = dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                            dismissState.targetValue != SwipeToDismissBoxValue.Settled
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = filter
                                pendingWhitelist = false
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = { AdGuardDeleteBackground(isActive = isActive) },
                            enableDismissFromStartToEnd = false
                        ) {
                            FilterRow(
                                filter = filter,
                                whitelist = false,
                                onToggle = { enabled -> viewModel.toggleFilter(filter, false, enabled) },
                                onEdit = {
                                    editingFilter = filter
                                    editingWhitelist = false
                                }
                            )
                        }
                    }

                    item {
                        AdGuardSectionHeader(title = stringResource(R.string.adguard_allowlists))
                    }
                    items(status.whitelistFilters, key = { it.id }) { filter ->
                        val dismissState = rememberSwipeToDismissBoxState()
                        val isActive = dismissState.currentValue != SwipeToDismissBoxValue.Settled ||
                            dismissState.targetValue != SwipeToDismissBoxValue.Settled
                        LaunchedEffect(dismissState.currentValue) {
                            if (dismissState.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                pendingDelete = filter
                                pendingWhitelist = true
                                dismissState.snapTo(SwipeToDismissBoxValue.Settled)
                            }
                        }
                        SwipeToDismissBox(
                            state = dismissState,
                            backgroundContent = { AdGuardDeleteBackground(isActive = isActive) },
                            enableDismissFromStartToEnd = false
                        ) {
                            FilterRow(
                                filter = filter,
                                whitelist = true,
                                onToggle = { enabled -> viewModel.toggleFilter(filter, true, enabled) },
                                onEdit = {
                                    editingFilter = filter
                                    editingWhitelist = true
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddSheet) {
        AddFilterSheet(
            onDismiss = { showAddSheet = false },
            onAdd = { name, url, whitelist ->
                viewModel.addFilter(name, url, whitelist)
                showAddSheet = false
            }
        )
    }

    if (pendingDelete != null) {
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.adguard_delete_filter)) },
            text = { Text(pendingDelete?.name.orEmpty()) },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete?.let { viewModel.removeFilter(it, pendingWhitelist) }
                    pendingDelete = null
                }) { Text(stringResource(R.string.delete)) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    if (editingFilter != null) {
        EditFilterDialog(
            filter = editingFilter!!,
            whitelist = editingWhitelist,
            onDismiss = { editingFilter = null },
            onSave = { name, url, enabled ->
                viewModel.editFilter(editingFilter!!, name, url, editingWhitelist, enabled)
                editingFilter = null
            }
        )
    }
}

@Composable
private fun FiltersSummary(status: com.homelab.app.data.remote.dto.adguard.AdGuardFilteringStatus) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AdGuardSectionHeader(title = stringResource(R.string.adguard_overview))
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.fillMaxWidth().heightIn(min = 200.dp, max = 280.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                SummaryCard(icon = Icons.Default.FilterAlt, color = StatusBlue, value = status.filters.size.toString(), label = stringResource(R.string.adguard_blocklists))
            }
            item {
                val enabledCount = status.filters.count { it.enabled }
                SummaryCard(icon = Icons.Default.CheckCircle, color = StatusGreen, value = enabledCount.toString(), label = stringResource(R.string.adguard_enabled))
            }
            item {
                SummaryCard(icon = Icons.AutoMirrored.Filled.Rule, color = StatusOrange, value = status.whitelistFilters.size.toString(), label = stringResource(R.string.adguard_allowlists))
            }
            item {
                SummaryCard(icon = Icons.AutoMirrored.Filled.ListAlt, color = StatusRed, value = status.userRules.size.toString(), label = stringResource(R.string.adguard_user_rules_label))
            }
        }
    }
}

@Composable
private fun SummaryCard(icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color, value: String, label: String) {
    AdGuardGlassCard(shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = color.copy(alpha = 0.18f), modifier = Modifier.size(34.dp)) {
                Icon(icon, contentDescription = label, tint = color, modifier = Modifier.padding(7.dp))
            }
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Text(text = label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun FilterRow(
    filter: AdGuardFilter,
    whitelist: Boolean,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit
) {
    val badgeColor = if (whitelist) StatusGreen else StatusRed
    AdGuardGlassCard(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).clickable(onClick = onEdit)) {
                Text(filter.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(filter.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    text = if (filter.enabled) stringResource(R.string.adguard_enabled) else stringResource(R.string.adguard_disabled),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (filter.enabled) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(shape = RoundedCornerShape(8.dp), color = badgeColor.copy(alpha = 0.16f)) {
                Text(
                    text = if (whitelist) stringResource(R.string.adguard_allow) else stringResource(R.string.adguard_blocked_label),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                    color = badgeColor
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            Switch(checked = filter.enabled, onCheckedChange = onToggle)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddFilterSheet(
    onDismiss: () -> Unit,
    onAdd: (String, String, Boolean) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    var whitelist by remember { mutableStateOf(false) }

    val presets = remember { listOf(
        FilterPreset("AdGuard DNS Filter", "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt"),
        FilterPreset("AdGuard Tracking Protection", "https://filters.adtidy.org/extension/chromium/filters/3.txt"),
        FilterPreset("AdGuard Mobile Ads", "https://filters.adtidy.org/extension/chromium/filters/11.txt"),
        FilterPreset("AdAway", "https://adaway.org/hosts.txt"),
        FilterPreset("StevenBlack Hosts", "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts"),
        FilterPreset("OISD", "https://big.oisd.nl/"),
        FilterPreset("EasyList", "https://easylist.to/easylist/easylist.txt"),
        FilterPreset("EasyPrivacy", "https://easylist.to/easylist/easyprivacy.txt"),
        FilterPreset("uBlock Filters", "https://filters.adtidy.org/extension/ublock/filters/2.txt"),
        FilterPreset("uBlock Privacy", "https://filters.adtidy.org/extension/ublock/filters/3.txt"),
        FilterPreset("uBlock Badware", "https://filters.adtidy.org/extension/ublock/filters/50.txt"),
        FilterPreset("Peter Lowe", "https://pgl.yoyo.org/adservers/serverlist.php?hostformat=hosts&showintro=0&mimetype=plaintext"),
        FilterPreset("MalwareDomains", "https://mirror1.malwaredomains.com/files/justdomains"),
        FilterPreset("URLHaus", "https://urlhaus.abuse.ch/downloads/hostfile/"),
        FilterPreset("HaGeZi Multi PRO", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/pro.txt"),
        FilterPreset("HaGeZi Light", "https://raw.githubusercontent.com/hagezi/dns-blocklists/main/hosts/light.txt"),
        FilterPreset("1Hosts (Lite)", "https://badmojr.github.io/1Hosts/Lite/hosts.txt"),
        FilterPreset("Dan Pollock", "https://someonewhocares.org/hosts/hosts"),
        FilterPreset("NoCoin", "https://raw.githubusercontent.com/hoshsadiq/adblock-nocoin-list/master/hosts.txt"),
        FilterPreset("Phishing Army", "https://phishing.army/download/phishing_army_blocklist.txt"),
        FilterPreset("DuckDuckGo Tracker Radar", "https://raw.githubusercontent.com/duckduckgo/tracker-radar/main/build-data/generated/hostnames/hosts.txt")
    ) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .imePadding(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.adguard_custom_list), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !whitelist, onClick = { whitelist = false }, label = { Text(stringResource(R.string.adguard_blocklist)) })
                FilterChip(selected = whitelist, onClick = { whitelist = true }, label = { Text(stringResource(R.string.adguard_allowlist)) })
            }
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.adguard_name)) }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.adguard_list_url)) }, modifier = Modifier.fillMaxWidth())
            TextButton(
                onClick = { if (name.isNotBlank() && url.isNotBlank()) onAdd(name.trim(), url.trim(), whitelist) },
                enabled = name.isNotBlank() && url.isNotBlank()
            ) {
                Text(stringResource(R.string.adguard_add_list))
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(stringResource(R.string.adguard_suggested_lists), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.heightIn(min = 280.dp, max = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(presets) { preset ->
                    AdGuardGlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(110.dp)
                            .clickable { onAdd(preset.name, preset.url, whitelist) },
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text(preset.name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(preset.url, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EditFilterDialog(
    filter: AdGuardFilter,
    whitelist: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, String, Boolean) -> Unit
) {
    var name by remember(filter) { mutableStateOf(filter.name) }
    var url by remember(filter) { mutableStateOf(filter.url) }
    var enabled by remember(filter) { mutableStateOf(filter.enabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.adguard_edit_filter)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.adguard_name)) }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.adguard_url)) }, modifier = Modifier.fillMaxWidth())
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                    Text(if (enabled) stringResource(R.string.adguard_enabled) else stringResource(R.string.adguard_disabled))
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name.trim(), url.trim(), enabled) }, enabled = name.isNotBlank() && url.isNotBlank()) {
                Text(stringResource(R.string.save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

private data class FilterPreset(val name: String, val url: String)
