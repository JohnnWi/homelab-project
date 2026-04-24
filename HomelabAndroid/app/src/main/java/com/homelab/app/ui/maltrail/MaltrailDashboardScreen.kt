package com.homelab.app.ui.maltrail

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.MaltrailCountPoint
import com.homelab.app.data.repository.MaltrailDashboardData
import com.homelab.app.data.repository.MaltrailEvent
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.common.ErrorScreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.text.NumberFormat

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaltrailDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: MaltrailViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    var selectedEvent by remember { mutableStateOf<MaltrailEvent?>(null) }
    val accent = ServiceType.MALTRAIL.primaryColor

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.service_maltrail),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchDashboard(forceLoading = false) }, enabled = !isRefreshing) {
                        if (isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = accent)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(maltrailPageBrush(accent))
                .padding(paddingValues)
        ) {
            when (val state = uiState) {
                UiState.Loading, UiState.Idle -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = accent)
                    }
                }
                is UiState.Error -> {
                    ErrorScreen(
                        message = state.message,
                        onRetry = state.retryAction ?: { viewModel.fetchDashboard(forceLoading = true) }
                    )
                }
                UiState.Offline -> {
                    ErrorScreen(
                        message = stringResource(R.string.error_network),
                        onRetry = { viewModel.fetchDashboard(forceLoading = true) },
                        isOffline = true
                    )
                }
                is UiState.Success -> {
                    MaltrailContent(
                        data = state.data,
                        instances = instances,
                        selectedInstanceId = viewModel.instanceId,
                        selectedDate = selectedDate ?: state.data.selectedDate,
                        onInstanceSelected = {
                            viewModel.setPreferredInstance(it.id)
                            onNavigateToInstance(it.id)
                        },
                        onDateSelected = viewModel::selectDate,
                        onEventSelected = { selectedEvent = it }
                    )
                }
            }
        }
    }

    selectedEvent?.let { event ->
        ModalBottomSheet(onDismissRequest = { selectedEvent = null }) {
            MaltrailEventSheet(
                event = event,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

@Composable
private fun MaltrailContent(
    data: MaltrailDashboardData,
    instances: List<ServiceInstance>,
    selectedInstanceId: String,
    selectedDate: String,
    onInstanceSelected: (ServiceInstance) -> Unit,
    onDateSelected: (String) -> Unit,
    onEventSelected: (MaltrailEvent) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServiceInstancePicker(
                instances = instances,
                selectedInstanceId = selectedInstanceId,
                onInstanceSelected = onInstanceSelected,
                label = stringResource(R.string.maltrail_instance_label)
            )
        }

        item {
            MaltrailHero(data = data)
        }

        item {
            MaltrailCountsCard(
                counts = data.counts,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected
            )
        }

        item {
            SectionHeader(
                icon = Icons.Default.Dns,
                title = stringResource(R.string.maltrail_events),
                count = data.events.size
            )
        }

        if (data.events.isEmpty()) {
            item { EmptyMaltrailCard() }
        } else {
            items(data.events, key = { it.id }) { event ->
                MaltrailEventCard(event = event, onClick = { onEventSelected(event) })
            }
        }
    }
}

@Composable
private fun MaltrailHero(data: MaltrailDashboardData) {
    val accent = ServiceType.MALTRAIL.primaryColor
    DashboardCard(
        borderColor = accent.copy(alpha = 0.22f)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ServiceIcon(type = ServiceType.MALTRAIL, size = 58.dp, iconSize = 38.dp, cornerRadius = 16.dp)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.service_maltrail),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.service_maltrail_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MaltrailMetric(
                icon = Icons.Default.Warning,
                value = formatNumber(data.latestCount),
                label = stringResource(R.string.maltrail_latest_day),
                color = accent,
                modifier = Modifier.weight(1f)
            )
            MaltrailMetric(
                icon = Icons.Default.Security,
                value = formatNumber(data.totalFindings),
                label = stringResource(R.string.maltrail_total_findings),
                color = StatusRed,
                modifier = Modifier.weight(1f)
            )
            MaltrailMetric(
                icon = Icons.Default.Dns,
                value = formatNumber(data.events.size),
                label = stringResource(R.string.maltrail_events),
                color = StatusOrange,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun MaltrailCountsCard(
    counts: List<MaltrailCountPoint>,
    selectedDate: String,
    onDateSelected: (String) -> Unit
) {
    val accent = ServiceType.MALTRAIL.primaryColor
    val maxCount = counts.maxOfOrNull { it.count }?.coerceAtLeast(1) ?: 1
    DashboardCard {
        SectionHeader(
            icon = Icons.Default.Security,
            title = stringResource(R.string.maltrail_daily_counts),
            count = counts.size
        )

        if (counts.isEmpty()) {
            Text(
                text = stringResource(R.string.maltrail_no_counts),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(horizontal = 2.dp)
            ) {
                items(counts.take(14), key = { it.timestamp }) { point ->
                    AssistChip(
                        onClick = { onDateSelected(point.apiDate) },
                        label = {
                            Text(
                                text = point.displayDate,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        leadingIcon = if (point.apiDate == selectedDate) {
                            { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(18.dp)) }
                        } else {
                            null
                        }
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                counts.take(7).forEach { point ->
                    MaltrailCountRow(
                        point = point,
                        maxCount = maxCount,
                        selected = point.apiDate == selectedDate,
                        accent = accent,
                        onClick = { onDateSelected(point.apiDate) }
                    )
                }
            }
        }
    }
}

@Composable
private fun MaltrailCountRow(
    point: MaltrailCountPoint,
    maxCount: Int,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    val surface = if (selected) accent.copy(alpha = 0.10f).compositeOver(MaterialTheme.colorScheme.surface) else Color.Transparent
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = surface
    ) {
        Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = point.displayDate,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = formatNumber(point.count),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(modifier = Modifier.height(7.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(7.dp)
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f), RoundedCornerShape(50))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((point.count.toFloat() / maxCount.toFloat()).coerceIn(0.04f, 1f))
                        .height(7.dp)
                        .background(accent, RoundedCornerShape(50))
                )
            }
        }
    }
}

@Composable
private fun MaltrailEventCard(event: MaltrailEvent, onClick: () -> Unit) {
    val accent = severityColor(event)
    DashboardCard(
        modifier = Modifier.clickable(onClick = onClick),
        borderColor = accent.copy(alpha = 0.22f)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = accent.copy(alpha = 0.14f).compositeOver(MaterialTheme.colorScheme.surface)
            ) {
                Icon(
                    imageVector = Icons.Default.Warning,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(22.dp)
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = event.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                event.route.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    event.protocolName?.takeIf { it.isNotBlank() }?.let { TinyChip(it) }
                    event.severity?.takeIf { it.isNotBlank() }?.let { TinyChip(it) }
                    event.sensor?.takeIf { it.isNotBlank() }?.let { TinyChip(it) }
                }
            }
        }
    }
}

@Composable
private fun MaltrailEventSheet(event: MaltrailEvent, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = 620.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        item {
            Text(
                text = stringResource(R.string.maltrail_event_details),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        item { DetailRow(stringResource(R.string.maltrail_source), event.source) }
        item { DetailRow(stringResource(R.string.maltrail_destination), event.destination) }
        item { DetailRow(stringResource(R.string.maltrail_trail), event.trail) }
        item { DetailRow(stringResource(R.string.maltrail_sensor), event.sensor) }
        item { DetailRow(stringResource(R.string.maltrail_protocol), event.protocolName) }
        item { DetailRow(stringResource(R.string.maltrail_severity), event.severity) }

        if (event.rawFields.isNotEmpty()) {
            item {
                HorizontalDivider()
                Text(
                    text = stringResource(R.string.maltrail_raw_fields),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
            items(event.rawFields.toList(), key = { it.first }) { (key, value) ->
                DetailRow(key, value)
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        SelectionContainer {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String, count: Int) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = ServiceType.MALTRAIL.primaryColor, modifier = Modifier.size(20.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatNumber(count),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MaltrailMetric(
    icon: ImageVector,
    value: String,
    label: String,
    color: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = color.copy(alpha = 0.11f).compositeOver(MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, color.copy(alpha = 0.18f))
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun TinyChip(text: String) {
    Surface(
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyMaltrailCard() {
    DashboardCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text = stringResource(R.string.maltrail_no_events),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun DashboardCard(
    modifier: Modifier = Modifier,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = if (MaterialTheme.colorScheme.surface.luminance() < 0.5f) 0.92f else 0.98f),
        border = BorderStroke(1.dp, borderColor),
        tonalElevation = 1.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            content = content
        )
    }
}

@Composable
private fun maltrailPageBrush(accent: Color): Brush {
    val background = MaterialTheme.colorScheme.background
    return Brush.verticalGradient(
        colors = listOf(
            accent.copy(alpha = if (background.luminance() < 0.5f) 0.18f else 0.10f).compositeOver(background),
            background,
            background
        )
    )
}

@Composable
private fun severityColor(event: MaltrailEvent): Color {
    return when {
        event.normalizedSeverity.contains("high") || event.normalizedSeverity.contains("critical") -> StatusRed
        event.normalizedSeverity.contains("medium") || event.normalizedSeverity.contains("warn") -> StatusOrange
        else -> ServiceType.MALTRAIL.primaryColor
    }
}

private fun formatNumber(value: Int): String = NumberFormat.getInstance().format(value)
