package com.homelab.app.ui.wakapi

import androidx.compose.foundation.clickable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.remote.dto.wakapi.WakapiDailySummariesResponse
import com.homelab.app.data.remote.dto.wakapi.WakapiGrandTotal
import com.homelab.app.data.remote.dto.wakapi.WakapiStatItem
import com.homelab.app.data.remote.dto.wakapi.WakapiSummaryResponse
import com.homelab.app.data.repository.WakapiSummaryFilter
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun WakapiDashboardScreen(
    viewModel: WakapiViewModel = hiltViewModel(),
    onNavigateBack: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToInstance: (String) -> Unit
) {
    val state by viewModel.summaryState.collectAsStateWithLifecycle()
    val activity by viewModel.activityState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val activeInterval by viewModel.selectedInterval.collectAsStateWithLifecycle()
    val activeFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()

    val currentInstance = instances.find { it.id == viewModel.instanceId }
    val label = currentInstance?.label?.takeIf { it.isNotBlank() } ?: ServiceType.WAKAPI.displayName

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onNavigateToSettings) {
                        Icon(
                            Icons.Default.SettingsApplications,
                            contentDescription = stringResource(R.string.settings_title)
                        )
                    }
                    IconButton(onClick = { viewModel.refreshAll(forceLoading = true) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { viewModel.refreshAll(forceLoading = true) },
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when (val s = state) {
                is UiState.Loading -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = s.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(24.dp)
                        )
                    }
                }
                is UiState.Success -> {
                    WakapiContent(
                        response = s.data,
                        activity = activity,
                        activeInterval = activeInterval,
                        activeFilter = activeFilter,
                        onIntervalSelected = viewModel::setInterval,
                        onClearFilter = viewModel::clearFilter,
                        onApplyFilter = viewModel::setFilter,
                        instances = instances,
                        activeInstanceId = viewModel.instanceId,
                        onInstanceSelected = {
                            viewModel.setPreferredInstance(it.id)
                            onNavigateToInstance(it.id)
                        }
                    )
                }
                is UiState.Idle, is UiState.Offline -> Unit
            }
        }
    }
}

@Composable
private fun WakapiContent(
    response: WakapiSummaryResponse,
    activity: WakapiDailySummariesResponse?,
    activeInterval: String,
    activeFilter: WakapiSummaryFilter?,
    onIntervalSelected: (String) -> Unit,
    onClearFilter: () -> Unit,
    onApplyFilter: (WakapiSummaryFilter) -> Unit,
    instances: List<com.homelab.app.domain.model.ServiceInstance>,
    activeInstanceId: String,
    onInstanceSelected: (com.homelab.app.domain.model.ServiceInstance) -> Unit
) {
    val activitySnapshot = remember(activity) {
        activity?.let(::buildWakapiActivitySnapshot)
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            ServiceInstancePicker(
                instances = instances,
                selectedInstanceId = activeInstanceId,
                onInstanceSelected = onInstanceSelected,
                label = stringResource(R.string.service_wakapi)
            )
        }

        item {
            WakapiIntervalSelector(
                activeInterval = activeInterval,
                onIntervalSelected = onIntervalSelected
            )
        }

        if (activeFilter != null) {
            item {
                ActiveFilterCard(filter = activeFilter, onClearFilter = onClearFilter)
            }
        }

        item {
            WakapiGrandTotalCard(
                grandTotal = response.effectiveGrandTotal(),
                activeInterval = activeInterval,
                activeFilter = activeFilter
            )
        }

        activitySnapshot?.let { snapshot ->
            item { WakapiActivityTrendCard(snapshot = snapshot, activity = activity) }
            item { WakapiActivityHeatmapCard(snapshot = snapshot) }
        }

        val languages = response.languages.orEmpty()
        if (languages.isNotEmpty()) {
            item {
                WakapiStatsCard(
                    title = stringResource(R.string.wakapi_section_languages),
                    icon = Icons.Default.Code,
                    items = languages,
                    filterDimension = WakapiSummaryFilter.Dimension.LANGUAGE,
                    activeFilter = activeFilter,
                    onApplyFilter = onApplyFilter
                )
            }
        }

        val projects = response.projects.orEmpty()
        if (projects.isNotEmpty()) {
            item {
                WakapiStatsCard(
                    title = stringResource(R.string.wakapi_section_projects),
                    icon = Icons.Default.SettingsApplications,
                    items = projects,
                    filterDimension = WakapiSummaryFilter.Dimension.PROJECT,
                    activeFilter = activeFilter,
                    onApplyFilter = onApplyFilter
                )
            }
        }

        val editors = response.editors.orEmpty()
        if (editors.isNotEmpty()) {
            item {
                WakapiStatsCard(
                    title = stringResource(R.string.wakapi_section_editors),
                    icon = Icons.Default.Computer,
                    items = editors,
                    filterDimension = WakapiSummaryFilter.Dimension.EDITOR,
                    activeFilter = activeFilter,
                    onApplyFilter = onApplyFilter
                )
            }
        }

        val machines = response.machines.orEmpty()
        if (machines.isNotEmpty()) {
            item {
                WakapiStatsCard(
                    title = stringResource(R.string.wakapi_section_machines),
                    icon = Icons.Default.Dns,
                    items = machines,
                    filterDimension = WakapiSummaryFilter.Dimension.MACHINE,
                    activeFilter = activeFilter,
                    onApplyFilter = onApplyFilter
                )
            }
        }

        val operatingSystems = response.operatingSystems.orEmpty()
        if (operatingSystems.isNotEmpty()) {
            item {
                WakapiStatsCard(
                    title = stringResource(R.string.wakapi_section_operating_systems),
                    icon = Icons.Default.Computer,
                    items = operatingSystems,
                    filterDimension = WakapiSummaryFilter.Dimension.OPERATING_SYSTEM,
                    activeFilter = activeFilter,
                    onApplyFilter = onApplyFilter
                )
            }
        }

        val labels = response.labels.orEmpty()
        if (labels.isNotEmpty()) {
            item {
                WakapiStatsCard(
                    title = stringResource(R.string.wakapi_section_labels),
                    icon = Icons.Default.Code,
                    items = labels,
                    filterDimension = WakapiSummaryFilter.Dimension.LABEL,
                    activeFilter = activeFilter,
                    onApplyFilter = onApplyFilter
                )
            }
        }

        val categories = response.categories.orEmpty()
        if (categories.isNotEmpty()) {
            item {
                WakapiStatsCard(
                    title = stringResource(R.string.wakapi_section_categories),
                    icon = Icons.Default.Timer,
                    items = categories
                )
            }
        }

        val branches = response.branches.orEmpty()
        if (branches.isNotEmpty()) {
            item {
                WakapiStatsCard(
                    title = stringResource(R.string.wakapi_section_branches),
                    icon = Icons.Default.Code,
                    items = branches
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun WakapiIntervalSelector(
    activeInterval: String,
    onIntervalSelected: (String) -> Unit
) {
    val options = listOf(
        "today" to stringResource(R.string.wakapi_interval_today),
        "yesterday" to stringResource(R.string.wakapi_interval_yesterday),
        "last_7_days" to stringResource(R.string.wakapi_interval_last_7_days),
        "last_30_days" to stringResource(R.string.wakapi_interval_last_30_days),
        "last_6_months" to stringResource(R.string.wakapi_interval_last_6_months),
        "last_year" to stringResource(R.string.wakapi_interval_last_year),
        "all_time" to stringResource(R.string.wakapi_interval_all_time)
    )

    FlowRow(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { (value, label) ->
            FilterChip(
                selected = activeInterval == value,
                onClick = { onIntervalSelected(value) },
                label = {
                    Text(
                        text = label,
                        fontWeight = if (activeInterval == value) FontWeight.Bold else FontWeight.Normal
                    )
                }
            )
        }
    }
}

@Composable
private fun ActiveFilterCard(
    filter: WakapiSummaryFilter,
    onClearFilter: () -> Unit
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.wakapi_active_filter),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = filter.value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(onClick = onClearFilter) {
                Text(stringResource(R.string.wakapi_clear_filter))
            }
        }
    }
}

@Composable
private fun WakapiGrandTotalCard(
    grandTotal: WakapiGrandTotal,
    activeInterval: String,
    activeFilter: WakapiSummaryFilter?
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.wakapi_total_time_coded),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = grandTotal.resolvedText,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Icon(
                    imageVector = Icons.Default.Timer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 12.dp)
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WakapiSummaryPill(
                    title = stringResource(R.string.wakapi_interval_label),
                    value = intervalLabel(activeInterval)
                )
                activeFilter?.let {
                    WakapiSummaryPill(
                        title = stringResource(R.string.wakapi_active_filter),
                        value = it.value
                    )
                }
            }
        }
    }
}

@Composable
private fun WakapiSummaryPill(title: String, value: String) {
    Surface(
        shape = MaterialTheme.shapes.medium,
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun WakapiActivityTrendCard(
    snapshot: WakapiActivitySnapshot,
    activity: WakapiDailySummariesResponse?
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.wakapi_recent_activity),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.wakapi_activity_last_30_days),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                ) {
                    Text(
                        text = activity?.dailyAverage?.textIncludingOtherLanguage
                            ?: activity?.dailyAverage?.text
                            ?: formatDuration(snapshot.averageSeconds),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            WakapiRecentActivityChart(snapshot = snapshot)

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                WakapiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.wakapi_average_per_day),
                    value = formatDuration(snapshot.averageSeconds)
                )
                WakapiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.wakapi_active_days),
                    value = snapshot.activeDays.toString(),
                    caption = stringResource(R.string.jellystat_window_days, snapshot.recentPoints.size)
                )
                WakapiMetricCard(
                    modifier = Modifier.weight(1f),
                    title = stringResource(R.string.wakapi_best_day),
                    value = snapshot.bestDay?.let { formatDuration(it.totalSeconds) }
                        ?: stringResource(R.string.wakapi_no_recent_activity),
                    caption = snapshot.bestDay?.let { formatAxisDate(it.date) }
                )
            }
        }
    }
}

@Composable
private fun WakapiRecentActivityChart(snapshot: WakapiActivitySnapshot) {
    val accent = MaterialTheme.colorScheme.primary
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val maxHours = maxOf(
        snapshot.recentPoints.maxOfOrNull { it.totalHours } ?: 0.0,
        snapshot.averageHours,
        1.0
    )
    val bestDate = snapshot.bestDay?.date

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
        ) {
            val count = snapshot.recentPoints.size.coerceAtLeast(1)
            val gap = 4.dp.toPx()
            val barWidth = ((size.width - gap * (count - 1)) / count).coerceAtLeast(2f)
            val averageY = size.height - ((snapshot.averageHours / maxHours) * size.height).toFloat()

            if (snapshot.averageHours > 0) {
                drawLine(
                    color = muted.copy(alpha = 0.35f),
                    start = Offset(0f, averageY),
                    end = Offset(size.width, averageY),
                    strokeWidth = 2.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(12f, 8f))
                )
            }

            snapshot.recentPoints.forEachIndexed { index, point ->
                val barHeight = ((point.totalHours / maxHours) * size.height).toFloat()
                val left = index * (barWidth + gap)
                drawRoundRect(
                    color = if (point.date == bestDate) accent else accent.copy(alpha = 0.36f),
                    topLeft = Offset(left, size.height - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8.dp.toPx(), 8.dp.toPx())
                )
            }
        }

        val labels = remember(snapshot.recentPoints) {
            val points = snapshot.recentPoints
            listOfNotNull(
                points.firstOrNull()?.date?.let(::formatAxisDate),
                points.getOrNull(points.lastIndex / 2)?.date?.let(::formatAxisDate),
                points.lastOrNull()?.date?.let(::formatAxisDate)
            )
        }

        if (labels.isNotEmpty()) {
            Row(modifier = Modifier.fillMaxWidth()) {
                labels.forEachIndexed { index, label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall,
                        color = muted,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (index == 1) {
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WakapiMetricCard(
    modifier: Modifier = Modifier,
    title: String,
    value: String,
    caption: String? = null
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.55f)
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            caption?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun WakapiActivityHeatmapCard(snapshot: WakapiActivitySnapshot) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = stringResource(R.string.wakapi_activity_heatmap_title),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.wakapi_heatmap_last_20_weeks),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                snapshot.heatmapWeeks.forEach { week ->
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        week.forEach { day ->
                            Box(
                                modifier = Modifier
                                    .width(11.dp)
                                    .height(11.dp)
                                    .background(
                                        color = wakapiHeatmapColors()[day.level],
                                        shape = MaterialTheme.shapes.extraSmall
                                    )
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                Text(
                    text = stringResource(R.string.gitea_heatmap_less),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(6.dp))
                wakapiHeatmapColors().forEach { color ->
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .width(10.dp)
                            .height(10.dp)
                            .background(color, MaterialTheme.shapes.extraSmall)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.gitea_heatmap_more),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WakapiStatsCard(
    title: String,
    icon: ImageVector,
    items: List<WakapiStatItem>,
    filterDimension: WakapiSummaryFilter.Dimension? = null,
    activeFilter: WakapiSummaryFilter? = null,
    onApplyFilter: ((WakapiSummaryFilter) -> Unit)? = null
) {
    val sectionTotalSeconds = items.sumOf { it.resolvedTotalSeconds }

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            items.take(10).forEachIndexed { index, item ->
                val itemName = item.displayName ?: stringResource(R.string.wakapi_unknown)
                val appliedDimension = filterDimension
                val isFilterable = appliedDimension != null && onApplyFilter != null && item.displayName != null
                val isActiveFilter =
                    activeFilter?.dimension == filterDimension && activeFilter?.value == item.displayName

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .then(
                            if (isFilterable) {
                                Modifier.clickable {
                                    onApplyFilter?.invoke(
                                        WakapiSummaryFilter(
                                            dimension = appliedDimension ?: return@clickable,
                                            value = itemName
                                        )
                                    )
                                }
                            } else {
                                Modifier
                            }
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = itemName,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = item.resolvedText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = "${item.resolvedPercent(sectionTotalSeconds).roundToInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isFilterable) {
                        IconButton(
                            onClick = {
                                onApplyFilter?.invoke(
                                    WakapiSummaryFilter(
                                        dimension = appliedDimension ?: return@IconButton,
                                        value = itemName
                                    )
                                )
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = stringResource(R.string.wakapi_apply_filter),
                                tint = if (isActiveFilter) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                }

                if (index < items.take(10).size - 1) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 4.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                }
            }
        }
    }
}

private data class WakapiActivityPoint(
    val date: LocalDate,
    val totalSeconds: Double
) {
    val totalHours: Double
        get() = totalSeconds / 3600.0
}

private data class WakapiHeatmapCell(
    val level: Int,
    val totalSeconds: Double
)

private data class WakapiActivitySnapshot(
    val recentPoints: List<WakapiActivityPoint>,
    val averageSeconds: Double,
    val activeDays: Int,
    val bestDay: WakapiActivityPoint?,
    val heatmapWeeks: List<List<WakapiHeatmapCell>>
) {
    val averageHours: Double
        get() = averageSeconds / 3600.0
}

@Composable
private fun wakapiHeatmapColors() = listOf(
    MaterialTheme.colorScheme.surfaceContainerHighest,
    MaterialTheme.colorScheme.primary.copy(alpha = 0.22f),
    MaterialTheme.colorScheme.primary.copy(alpha = 0.42f),
    MaterialTheme.colorScheme.primary.copy(alpha = 0.62f),
    MaterialTheme.colorScheme.primary.copy(alpha = 0.82f)
)

@Composable
private fun intervalLabel(interval: String): String = when (interval) {
    "today" -> stringResource(R.string.wakapi_interval_today)
    "yesterday" -> stringResource(R.string.wakapi_interval_yesterday)
    "last_7_days" -> stringResource(R.string.wakapi_interval_last_7_days)
    "last_30_days" -> stringResource(R.string.wakapi_interval_last_30_days)
    "last_6_months" -> stringResource(R.string.wakapi_interval_last_6_months)
    "last_year" -> stringResource(R.string.wakapi_interval_last_year)
    "all_time" -> stringResource(R.string.wakapi_interval_all_time)
    else -> interval
}

private fun buildWakapiActivitySnapshot(response: WakapiDailySummariesResponse): WakapiActivitySnapshot? {
    val points = response.data.mapNotNull { summary ->
        val rawDate = summary.range?.start ?: summary.range?.date ?: summary.range?.end
        parseWakapiDate(rawDate)?.let { date ->
            WakapiActivityPoint(date = date, totalSeconds = summary.totalSeconds.coerceAtLeast(0.0))
        }
    }.sortedBy { it.date }

    if (points.isEmpty()) return null

    val recentPoints = points.takeLast(30)
    val averageSeconds = if (recentPoints.isEmpty()) {
        0.0
    } else {
        recentPoints.sumOf { it.totalSeconds } / recentPoints.size
    }
    val activeDays = recentPoints.count { it.totalSeconds > 0.0 }
    val bestDay = recentPoints.maxByOrNull { it.totalSeconds }

    return WakapiActivitySnapshot(
        recentPoints = recentPoints,
        averageSeconds = averageSeconds,
        activeDays = activeDays,
        bestDay = bestDay,
        heatmapWeeks = buildWakapiHeatmapWeeks(points)
    )
}

private fun buildWakapiHeatmapWeeks(points: List<WakapiActivityPoint>): List<List<WakapiHeatmapCell>> {
    val totalsByDay = points.groupBy { it.date }
        .mapValues { (_, dayPoints) -> dayPoints.sumOf { it.totalSeconds } }
    val maxTotal = maxOf(1.0, totalsByDay.values.maxOrNull() ?: 0.0)
    val today = LocalDate.now()
    val dayOfWeek = today.dayOfWeek.value % 7
    val weeksToShow = 20
    val totalDays = weeksToShow * 7 + dayOfWeek + 1
    val startDate = today.minusDays((totalDays - 1).toLong())

    val weeks = mutableListOf<List<WakapiHeatmapCell>>()
    var currentWeek = mutableListOf<WakapiHeatmapCell>()

    repeat(totalDays) { offset ->
        val date = startDate.plusDays(offset.toLong())
        val total = totalsByDay[date] ?: 0.0
        val ratio = if (total > 0) total / maxTotal else 0.0
        val level = when {
            total <= 0 -> 0
            ratio <= 0.25 -> 1
            ratio <= 0.5 -> 2
            ratio <= 0.75 -> 3
            else -> 4
        }

        currentWeek.add(WakapiHeatmapCell(level = level, totalSeconds = total))
        if (currentWeek.size == 7) {
            weeks.add(currentWeek.toList())
            currentWeek = mutableListOf()
        }
    }

    if (currentWeek.isNotEmpty()) {
        weeks.add(currentWeek.toList())
    }

    return weeks
}

private fun parseWakapiDate(value: String?): LocalDate? {
    if (value.isNullOrBlank()) return null
    return runCatching { OffsetDateTime.parse(value).toLocalDate() }.getOrNull()
        ?: runCatching {
            Instant.parse(value).atZone(ZoneId.systemDefault()).toLocalDate()
        }.getOrNull()
        ?: runCatching { LocalDate.parse(value) }.getOrNull()
}

private fun formatDuration(totalSeconds: Double): String {
    val seconds = totalSeconds.roundToInt().coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    return if (hours > 0) "${hours}h ${minutes}m" else "${minutes}m"
}

private fun formatAxisDate(date: LocalDate): String =
    date.format(DateTimeFormatter.ofPattern("MMM d"))
