package com.homelab.app.ui.adguard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.util.UiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdGuardHomeBlockedServicesScreen(
    onNavigateBack: () -> Unit,
    viewModel: AdGuardHomeViewModel = hiltViewModel()
) {
    val state by viewModel.blockedServicesState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.fetchBlockedServices()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.adguard_blocked_services), fontWeight = FontWeight.Bold) },
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
        when (val ui = state) {
            is UiState.Loading, is UiState.Idle -> {
                Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            is UiState.Error -> {
                ErrorScreen(message = ui.message, onRetry = { ui.retryAction?.invoke() }, modifier = Modifier.padding(paddingValues))
            }
            is UiState.Offline -> {
                ErrorScreen(message = "", onRetry = { viewModel.fetchBlockedServices() }, isOffline = true, modifier = Modifier.padding(paddingValues))
            }
            is UiState.Success -> {
                val data = ui.data
                val grouped = data.services.groupBy { it.groupId ?: "other" }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(paddingValues)
                        .padding(horizontal = 16.dp),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    grouped.keys.sorted().forEach { groupId ->
                        item {
                            val groupName = data.groups[groupId]
                                ?: if (groupId == "other") stringResource(R.string.adguard_group_other) else groupId
                            AdGuardSectionHeader(title = groupName)
                        }
                        items(grouped[groupId].orEmpty(), key = { it.id }) { service ->
                            BlockedServiceRow(
                                name = service.name,
                                enabled = data.blockedIds.contains(service.id),
                                onToggle = { enabled ->
                                    val updated = data.blockedIds.toMutableSet()
                                    if (enabled) updated.add(service.id) else updated.remove(service.id)
                                    viewModel.updateBlockedServices(updated)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockedServiceRow(name: String, enabled: Boolean, onToggle: (Boolean) -> Unit) {
    AdGuardGlassCard(shape = RoundedCornerShape(16.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = StatusRed.copy(alpha = 0.15f), modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Block, contentDescription = null, tint = StatusRed, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
            Switch(checked = enabled, onCheckedChange = onToggle)
        }
    }
}
