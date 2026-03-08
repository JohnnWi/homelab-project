package com.homelab.app.ui.pihole

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.pihole.PiholeTopClient
import com.homelab.app.data.remote.dto.pihole.PiholeTopItem
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.util.ServiceType
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PiholeDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToDomains: () -> Unit,
    viewModel: PiholeViewModel = hiltViewModel()
) {
    val stats by viewModel.stats.collectAsStateWithLifecycle()
    val blocking by viewModel.blocking.collectAsStateWithLifecycle()
    val topBlocked by viewModel.topBlocked.collectAsStateWithLifecycle()
    val topDomains by viewModel.topDomains.collectAsStateWithLifecycle()
    val topClients by viewModel.topClients.collectAsStateWithLifecycle()
    val history by viewModel.history.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val isToggling by viewModel.isToggling.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(Unit) {
        viewModel.fetchAll()
    }

    LaunchedEffect(error) {
        error?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearError()
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.service_pihole), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchAll() }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && stats == null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ServiceType.PIHOLE.primaryColor)
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Blocking Card
                item {
                    val isBlocking = blocking?.isEnabled == true
                    BlockingCard(
                        isBlocking = isBlocking,
                        isToggling = isToggling,
                        onToggle = { timer -> viewModel.toggleBlocking(timer) }
                    )
                }

                // Gravity info
                if (stats != null) {
                    item { StatsOverview(stats = stats!!) }
                    item { QueryActivitySection(stats = stats!!) }
                    item { GravitySection(stats = stats!!) }
                }

                item {
                    DomainManagementLink(onClick = onNavigateToDomains)
                }

                if (topBlocked.isNotEmpty()) {
                    item {
                        TopListSection(
                            title = stringResource(R.string.pihole_top_blocked),
                            items = topBlocked,
                            rankColor = ServiceType.PIHOLE.primaryColor
                        )
                    }
                }

                if (topDomains.isNotEmpty()) {
                    item {
                        TopDomainsSection(topDomains = topDomains)
                    }
                }

                if (topClients.isNotEmpty()) {
                    item {
                        TopClientsSection(topClients = topClients)
                    }
                }
            }
        }
    }
}

@Composable
private fun BlockingCard(
    isBlocking: Boolean,
    isToggling: Boolean,
    onToggle: (timer: Int?) -> Unit
) {
    var showDialog by remember { mutableStateOf(false) }

    if (showDialog) {
        AlertDialog(
            onDismissRequest = { showDialog = false },
            title = { Text(stringResource(R.string.pihole_blocking_disabled_desc)) },
            text = {
                Column {
                    TextButton(onClick = { onToggle(null); showDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pihole_disable_permanently), color = MaterialTheme.colorScheme.error)
                    }
                    TextButton(onClick = { onToggle(3600); showDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pihole_disable_1h))
                    }
                    TextButton(onClick = { onToggle(300); showDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pihole_disable_5m))
                    }
                    TextButton(onClick = { onToggle(60); showDialog = false }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.pihole_disable_1m))
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showDialog = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.98f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)
    )

    val runColor = StatusGreen
    val stopColor = StatusRed
    val color = if (isBlocking) runColor else stopColor

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clickable(
                interactionSource = interactionSource, 
                indication = null, 
                enabled = !isToggling,
                onClick = {
                    haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                    if (isBlocking) {
                        showDialog = true
                    } else {
                        onToggle(null)
                    }
                }
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Icon
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = color.copy(alpha = 0.1f),
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    if (isBlocking) Icons.Default.Shield else Icons.Default.GppBad,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.padding(14.dp)
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            // Text
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.pihole_blocking_title), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold))
                Text(if (isBlocking) stringResource(R.string.pihole_status_active) else stringResource(R.string.pihole_status_disabled), style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold), color = color)
                Text(if (isBlocking) stringResource(R.string.pihole_blocking_active_desc) else stringResource(R.string.pihole_blocking_disabled_desc), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Spacer(modifier = Modifier.width(14.dp))

            if (isToggling) {
                CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = color)
            } else {
                Surface(
                    shape = CircleShape,
                    color = color
                ) {
                    Text(
                        text = if (isBlocking) stringResource(R.string.pihole_blocking_on) else stringResource(R.string.pihole_blocking_off),
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DomainManagementLink(onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(10.dp), color = ServiceType.PIHOLE.primaryColor.copy(alpha = 0.1f), modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ListAlt, contentDescription = null, tint = ServiceType.PIHOLE.primaryColor, modifier = Modifier.padding(8.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                stringResource(R.string.pihole_domain_management),
                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier.weight(1f)
            )
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StatsOverview(stats: com.homelab.app.data.remote.dto.pihole.PiholeStats) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.pihole_overview_title),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Search, iconBg = ServiceType.PIHOLE.primaryColor, value = formatNum(stats.queries.total), label = stringResource(R.string.pihole_total_queries))
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.PanTool, iconBg = StatusRed, value = formatNum(stats.queries.blocked), label = stringResource(R.string.pihole_blocked))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.BarChart, iconBg = StatusOrange, value = String.format("%.1f%%", stats.queries.percent_blocked), label = stringResource(R.string.pihole_percentage))
            StatCard(modifier = Modifier.weight(1f), icon = Icons.Default.Language, iconBg = StatusBlue, value = formatNum(stats.queries.unique_domains), label = stringResource(R.string.pihole_domains))
        }
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, icon: androidx.compose.ui.graphics.vector.ImageVector, iconBg: Color, value: String, label: String) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = RoundedCornerShape(10.dp), color = iconBg.copy(alpha = 0.1f), modifier = Modifier.size(36.dp)) {
                Icon(icon, contentDescription = null, tint = iconBg, modifier = Modifier.padding(8.dp))
            }
            Text(text = value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), maxLines = 1)
            Text(text = label, style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
    }
}

@Composable
private fun QueryActivitySection(stats: com.homelab.app.data.remote.dto.pihole.PiholeStats) {
    val maxQuery = maxOf(stats.queries.total, 1)

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = stringResource(R.string.pihole_query_activity),
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ActivityBar(label = stringResource(R.string.pihole_blocked), value = stats.queries.blocked, max = maxQuery, color = StatusRed)
                ActivityBar(label = stringResource(R.string.pihole_cached), value = stats.queries.cached, max = maxQuery, color = StatusGreen)
                ActivityBar(label = stringResource(R.string.pihole_forwarded), value = stats.queries.forwarded, max = maxQuery, color = StatusBlue)
            }
        }
    }
}

@Composable
private fun ActivityBar(label: String, value: Int, max: Int, color: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(color))
                Spacer(modifier = Modifier.width(8.dp))
                Text(text = label, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium))
            }
            Text(text = formatNum(value), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        val pct = value.toFloat() / max.toFloat()
        Surface(
            shape = RoundedCornerShape(3.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(6.dp)
        ) {
            Row {
                Box(modifier = Modifier.fillMaxHeight().fillMaxWidth(pct).background(color))
            }
        }
    }
}

@Composable
private fun GravitySection(stats: com.homelab.app.data.remote.dto.pihole.PiholeStats) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(shape = RoundedCornerShape(12.dp), color = ServiceType.PIHOLE.primaryColor.copy(alpha = 0.1f), modifier = Modifier.size(44.dp)) {
                Icon(Icons.Default.Storage, contentDescription = null, tint = ServiceType.PIHOLE.primaryColor, modifier = Modifier.padding(10.dp))
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column {
                Text(stringResource(R.string.pihole_gravity_domains), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatNum(stats.gravity.domains_being_blocked), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold))
            }
            Spacer(modifier = Modifier.weight(1f))
            if (stats.gravity.last_update > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(4.dp))
                    val date = Date(stats.gravity.last_update * 1000)
                    val formatter = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
                    Text(formatter.format(date), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun TopListSection(title: String, items: List<PiholeTopItem>, rankColor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = title, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Column {
                items.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(8.dp), color = rankColor.copy(alpha = 0.1f), modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = rankColor)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(item.domain, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(formatNum(item.count), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (idx < items.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopDomainsSection(topDomains: List<PiholeTopItem>) {
    val total = topDomains.sumOf { it.count }
    val maxCount = maxOf(1, topDomains.maxOfOrNull { it.count } ?: 1)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
            Text(text = stringResource(R.string.pihole_top_queries), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
            Surface(shape = RoundedCornerShape(8.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Text(stringResource(R.string.pihole_total_suffix).format(formatNum(total)), style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp))
            }
        }
        
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                topDomains.forEachIndexed { idx, item ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(8.dp), color = StatusGreen.copy(alpha = 0.1f), modifier = Modifier.size(28.dp)) {
                            Box(contentAlignment = Alignment.Center) {
                                Text("${idx + 1}", style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold), color = StatusGreen)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            val pct = item.count.toFloat() / maxCount.toFloat()
                            Surface(shape = RoundedCornerShape(6.dp), color = StatusGreen.copy(alpha = 0.1f), modifier = Modifier.fillMaxWidth(pct).height(24.dp)) {}
                            Text(item.domain, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = 4.dp))
                        }
                        
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(formatNum(item.count), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (idx < topDomains.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun TopClientsSection(topClients: List<PiholeTopClient>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = stringResource(R.string.pihole_top_clients), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurfaceVariant)
        
        Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                topClients.forEachIndexed { idx, client ->
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(shape = RoundedCornerShape(8.dp), color = StatusBlue.copy(alpha = 0.1f), modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Person, contentDescription = null, tint = StatusBlue, modifier = Modifier.padding(6.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            val name = client.name.ifEmpty { client.ip }
                            Text(name, style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (client.name.isNotEmpty()) {
                                Text(client.ip, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(formatNum(client.count), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (idx < topClients.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

private fun formatNum(n: Int): String {
    return NumberFormat.getInstance(Locale.getDefault()).format(n)
}
