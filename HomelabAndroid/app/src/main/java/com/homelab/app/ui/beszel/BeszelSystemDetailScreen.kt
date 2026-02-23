package com.homelab.app.ui.beszel

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.util.ServiceType
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.StatusRed
import com.homelab.app.ui.theme.StatusOrange
import com.homelab.app.ui.theme.StatusBlue
import com.homelab.app.ui.theme.StatusPurple
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemInfo
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
import com.homelab.app.data.remote.dto.beszel.BeszelContainer
import androidx.compose.ui.res.stringResource
import com.homelab.app.R
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BeszelSystemDetailScreen(
    systemId: String,
    onNavigateBack: () -> Unit,
    viewModel: BeszelViewModel = hiltViewModel()
) {
    val system by viewModel.selectedSystem.collectAsStateWithLifecycle()
    val records by viewModel.records.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    
    LaunchedEffect(systemId) {
        viewModel.fetchSystemDetail(systemId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(system?.name ?: stringResource(R.string.beszel_system_details), fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.fetchSystemDetail(systemId) }) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && system == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = ServiceType.BESZEL.primaryColor)
            }
        } else if (system == null) {
            Box(modifier = Modifier.fillMaxSize().padding(paddingValues), contentAlignment = Alignment.Center) {
                Text(error ?: stringResource(R.string.beszel_system_not_found), color = MaterialTheme.colorScheme.error)
            }
        } else {
            val s = system!!
            val info = s.info
            val latestStats = records.firstOrNull()?.stats

            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Header Card
                item { HeaderCard(s) }

                if (info != null) {
                    // System Info
                    item { SystemInfoSection(info) }

                    // Resources
                    item {
                        ResourcesSection(
                            cpu = info.cpuValue,
                            mp = info.mpValue,
                            dp = info.dpValue,
                            cpuHistory = records.takeLast(30).map { it.stats.cpuValue },
                            memHistory = records.takeLast(30).map { it.stats.mpValue }
                        )
                    }

                    // Containers
                    val containers = latestStats?.dc ?: emptyList()
                    if (containers.isNotEmpty()) {
                        item { ContainersSection(containers) }
                    }

                    // Uptime
                    item { UptimeCard(info.uValue) }
                }
            }
        }
    }
}

@Composable
private fun HeaderCard(system: BeszelSystem) {
    val isUp = system.isOnline
    val statusColor = if (isUp) StatusGreen else StatusRed

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(18.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = statusColor.copy(alpha = 0.1f),
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        if (isUp) Icons.Default.Wifi else Icons.Default.WifiOff,
                        contentDescription = null,
                        tint = statusColor,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(system.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("${system.host}:${system.portValue ?: 80}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Surface(
                shape = RoundedCornerShape(14.dp),
                color = statusColor.copy(alpha = 0.1f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Text(
                        if (isUp) stringResource(R.string.beszel_online) else stringResource(R.string.beszel_offline),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = statusColor
                    )
                }
            }
        }
    }
}

@Composable
private fun SystemInfoSection(info: BeszelSystemInfo) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(icon = Icons.Default.Info, title = stringResource(R.string.beszel_info_title))
        
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                info.os?.let { if (it.isNotEmpty()) InfoRow(stringResource(R.string.beszel_os), it) }
                info.k?.let { if (it.isNotEmpty()) { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant); InfoRow(stringResource(R.string.beszel_kernel), it) } }
                info.h?.let { if (it.isNotEmpty()) { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant); InfoRow(stringResource(R.string.beszel_hostname), it) } }
                info.cm?.let { if (it.isNotEmpty()) { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant); InfoRow(stringResource(R.string.beszel_cpu), it) } }
                info.c?.let { if (it > 0) { HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant); InfoRow(stringResource(R.string.beszel_cores), "$it") } }
            }
        }
    }
}

@Composable
private fun ResourcesSection(
    cpu: Double, 
    mp: Double, 
    dp: Double, 
    cpuHistory: List<Double> = emptyList(),
    memHistory: List<Double> = emptyList()
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(icon = Icons.Default.Layers, title = stringResource(R.string.beszel_resources_title))

        ResourceCard(
            icon = Icons.Default.Memory,
            iconColor = ServiceType.BESZEL.primaryColor,
            title = stringResource(R.string.beszel_cpu),
            percent = cpu,
            history = cpuHistory
        )

        ResourceCard(
            icon = Icons.Default.Dns,
            iconColor = StatusPurple,
            title = stringResource(R.string.beszel_memory),
            percent = mp,
            history = memHistory
        )

        ResourceCard(
            icon = Icons.Default.Storage,
            iconColor = StatusOrange,
            title = stringResource(R.string.beszel_disk),
            percent = dp
        )
    }
}

@Composable
private fun ResourceCard(
    icon: ImageVector,
    iconColor: Color,
    title: String,
    percent: Double,
    detailLeft: String? = null,
    detailRight: String? = null,
    history: List<Double> = emptyList()
) {
    val progressColor = when {
        percent > 90 -> StatusRed
        percent > 70 -> StatusOrange
        else -> StatusGreen
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = iconColor.copy(alpha = 0.1f),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, tint = iconColor, modifier = Modifier.size(20.dp))
                    }
                }

                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text(String.format("%.1f%%", percent), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = progressColor)
            }

            // Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (isThemeDark()) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surfaceVariant)
            ) {
                val animatedProgress by animateFloatAsState(
                    targetValue = (percent.toFloat() / 100f).coerceIn(0f, 1f),
                    animationSpec = spring(stiffness = Spring.StiffnessLow),
                    label = "resource_progress"
                )
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(animatedProgress)
                        .background(Brush.horizontalGradient(listOf(progressColor.copy(alpha = 0.7f), progressColor)))
                )
            }

            if (detailLeft != null && detailRight != null) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(detailLeft, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(detailRight, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            
            if (history.isNotEmpty()) {
                SmoothLineGraph(data = history, graphColor = iconColor)
            }
        }
    }
}

@Composable
private fun SmoothLineGraph(data: List<Double>, graphColor: Color) {
    if (data.size < 2) return
    
    val maxVal = (data.maxOrNull() ?: 1.0).coerceAtLeast(1.0)
    val minVal = (data.minOrNull() ?: 0.0).coerceAtMost(maxVal - 0.1) // Ensure small gap
    val range = (maxVal - minVal).coerceAtLeast(0.1) // Prevent division by zero when stats are stagnant
    
    // Scale up animation
    val animationProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "graph_appear"
    )

    Column(modifier = Modifier.fillMaxWidth().padding(top = 12.dp)) {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.5.dp)
        Spacer(modifier = Modifier.height(16.dp))
        
        androidx.compose.foundation.Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
        ) {
            val width = size.width
            val height = size.height
            val stepX = width / (data.size - 1).coerceAtLeast(1)

            val path = androidx.compose.ui.graphics.Path()
            val fillPath = androidx.compose.ui.graphics.Path()

            var previousX = 0f
            var previousY = height - ((data.first() - minVal) / range * height).toFloat() * animationProgress

            path.moveTo(previousX, previousY)
            fillPath.moveTo(0f, height)
            fillPath.lineTo(0f, previousY)

            for (i in 1 until data.size) {
                val x = i * stepX
                val y = height - ((data[i] - minVal) / range * height).toFloat() * animationProgress

                val controlX1 = previousX + (x - previousX) / 2f
                val controlY1 = previousY
                val controlX2 = previousX + (x - previousX) / 2f
                val controlY2 = y

                path.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)
                fillPath.cubicTo(controlX1, controlY1, controlX2, controlY2, x, y)

                previousX = x
                previousY = y
            }

            fillPath.lineTo(width, height)
            fillPath.close()

            // Draw Area
            drawPath(
                path = fillPath,
                brush = Brush.verticalGradient(
                    colors = listOf(graphColor.copy(alpha = 0.4f), Color.Transparent),
                    startY = 0f,
                    endY = height
                )
            )

            // Draw Line
            drawPath(
                path = path,
                color = graphColor,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = 2.dp.toPx(),
                    cap = androidx.compose.ui.graphics.StrokeCap.Round,
                    join = androidx.compose.ui.graphics.StrokeJoin.Round
                )
            )
        }
    }
}



@Composable
private fun ContainersSection(containers: List<BeszelContainer>) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        SectionHeader(icon = Icons.Default.Inventory2, title = stringResource(R.string.beszel_containers_title).format(containers.count()))

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
        ) {
            Column {
                containers.forEachIndexed { index, container ->
                    Row(
                        modifier = Modifier.padding(14.dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = ServiceType.BESZEL.primaryColor.copy(alpha = 0.08f),
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.Inventory2, contentDescription = null, tint = ServiceType.BESZEL.primaryColor, modifier = Modifier.size(16.dp))
                            }
                        }

                        Text(container.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))

                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            ContainerStat(icon = Icons.Default.Memory, value = String.format("%.1f%%", container.cpuValue))
                            ContainerStat(icon = Icons.Default.Dns, value = formatMB(container.mValue))
                        }
                    }
                    if (index < containers.size - 1) {
                        HorizontalDivider(modifier = Modifier.padding(start = 56.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ContainerStat(icon: ImageVector, value: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(10.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
private fun UptimeCard(seconds: Double) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Schedule, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Column {
                Text(stringResource(R.string.beszel_uptime), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(formatUptimeHours(seconds), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun SectionHeader(icon: ImageVector, title: String) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, tint = ServiceType.BESZEL.primaryColor, modifier = Modifier.size(16.dp))
        Text(title, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 16.dp)
        )
    }
}

// --- Formatters ---

private fun formatMB(valMB: Double): String {
    if (valMB == 0.0) return "0 MB"
    if (valMB > 10_000_000) return formatBytes(valMB)
    val gb = valMB / 1024.0
    if (gb >= 1.0) return String.format("%.2f GB", gb)
    return String.format("%.0f MB", valMB)
}

private fun formatGB(valGB: Double): String {
    if (valGB == 0.0) return "0 GB"
    if (valGB > 10_000_000) return formatBytes(valGB)
    if (valGB >= 1000.0) return String.format("%.2f TB", valGB / 1000.0)
    if (valGB >= 1.0) return String.format("%.1f GB", valGB)
    return String.format("%.0f MB", valGB * 1024)
}

private fun formatBytes(bytes: Double): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    val digitGroups = (Math.log10(bytes) / Math.log10(1024.0)).toInt()
    return String.format("%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
}

private fun formatNetRate(valBytesPerSec: Double): String {
    if (valBytesPerSec == 0.0) return "0 B/s"
    return "${formatBytes(valBytesPerSec)}/s"
}

private fun formatUptimeHours(seconds: Double): String {
    val d = (seconds / 86400).toInt()
    val h = (seconds % 86400 / 3600).toInt()
    if (d > 0) return "${d}d ${h}h"
    val m = (seconds % 3600 / 60).toInt()
    if (h > 0) return "${h}h ${m}m"
    return "${m}m"
}

