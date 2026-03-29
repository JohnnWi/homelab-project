package com.homelab.app.ui.media

import android.content.ClipData
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import com.homelab.app.R
import com.homelab.app.data.repository.MediaArrAction
import com.homelab.app.data.repository.MediaArrActionResult
import com.homelab.app.data.repository.MediaArrDownloadItem
import com.homelab.app.data.repository.MediaArrHistoryItem
import com.homelab.app.data.repository.MediaArrMetric
import com.homelab.app.data.repository.MediaArrRequestOption
import com.homelab.app.data.repository.MediaArrRequestSelection
import com.homelab.app.data.repository.MediaArrSearchResultItem
import com.homelab.app.data.repository.MediaArrSnapshot
import com.homelab.app.data.repository.QbittorrentTorrentItem
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import coil3.compose.AsyncImage
import coil3.compose.SubcomposeAsyncImage
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

private const val MEDIA_TAILSCALE_ICON_URL = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/tailscale.png"
private const val QBITTORRENT_REFRESH_INTERVAL_MS = 10_000L

@Composable
fun MediaArrScreen(
    onNavigateToLogin: (ServiceType, String?) -> Unit,
    onNavigateToService: (ServiceType, String) -> Unit,
    viewModel: MediaArrViewModel = hiltViewModel()
) {
    val instancesByType by viewModel.instancesByType.collectAsStateWithLifecycle()
    val reachability by viewModel.reachability.collectAsStateWithLifecycle()
    val pinging by viewModel.pinging.collectAsStateWithLifecycle()
    val hiddenServices by viewModel.hiddenServices.collectAsStateWithLifecycle()
    val mediaOrder by viewModel.mediaServiceOrder.collectAsStateWithLifecycle()
    val tutorialDismissed by viewModel.tutorialDismissed.collectAsStateWithLifecycle()
    val tailscaleConnected by viewModel.isTailscaleConnected.collectAsStateWithLifecycle()
    val cardPreviewState by viewModel.cardPreviewState.collectAsStateWithLifecycle()
    val homeCyberpunkCardsEnabled by viewModel.homeCyberpunkCardsEnabled.collectAsStateWithLifecycle()
    var showReorderDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.refresh()
    }

    val configuredTypes = mediaOrder.filter { instancesByType[it].orEmpty().isNotEmpty() }
    val unconfiguredTypes = mediaOrder.filter { instancesByType[it].orEmpty().isEmpty() }
    val firstUnconfiguredType = unconfiguredTypes.firstOrNull()
    val connectedMediaCount = configuredTypes.sumOf { instancesByType[it].orEmpty().size }
    val hasUnreachable = configuredTypes
        .flatMap { instancesByType[it].orEmpty() }
        .any { reachability[it.id] == false }

    val context = LocalContext.current

    // Build grid entries: each service type becomes either a disconnected card or one card per instance
    val gridEntries = mediaOrder.flatMap { type ->
        val instances = instancesByType[type].orEmpty()
        if (instances.isEmpty()) {
            listOf(MediaGridEntry.Disconnected(type))
        } else {
            instances.map { MediaGridEntry.Connected(type, it) }
        }
    }

    Scaffold(containerColor = MaterialTheme.colorScheme.background) { paddingValues ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp, top = 16.dp)
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.nav_media),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = RoundedCornerShape(999.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = "$connectedMediaCount",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(onClick = { showReorderDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = stringResource(R.string.home_reorder_services)
                            )
                        }
                    }
                }
            }

            if (!tutorialDismissed) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.42f)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = stringResource(R.string.media_tutorial_title),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Text(
                                text = stringResource(R.string.media_tutorial_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                Text(text = stringResource(R.string.media_tutorial_step_1), style = MaterialTheme.typography.bodySmall)
                                Text(text = stringResource(R.string.media_tutorial_step_2), style = MaterialTheme.typography.bodySmall)
                                Text(text = stringResource(R.string.media_tutorial_step_3), style = MaterialTheme.typography.bodySmall)
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                FilledTonalButton(
                                    onClick = {
                                        firstUnconfiguredType?.let { type -> onNavigateToLogin(type, null) }
                                    },
                                    modifier = Modifier.weight(1f),
                                    enabled = firstUnconfiguredType != null
                                ) {
                                    Text(
                                        text = stringResource(R.string.media_tutorial_action_configure),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.dismissTutorial() },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text(
                                        text = stringResource(R.string.media_tutorial_action_dismiss),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (tailscaleConnected || hasUnreachable) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.42f),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                val intent = Intent(Intent.ACTION_VIEW, "tailscale://app".toUri())
                                val fallback = Intent(Intent.ACTION_VIEW, "https://play.google.com/store/apps/details?id=com.tailscale.ipn".toUri())
                                try {
                                    context.startActivity(intent)
                                } catch (_: Exception) {
                                    context.startActivity(fallback)
                                }
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = MaterialTheme.colorScheme.surfaceContainer,
                                modifier = Modifier.size(40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    SubcomposeAsyncImage(
                                        model = MEDIA_TAILSCALE_ICON_URL,
                                        contentDescription = stringResource(R.string.tailscale_open),
                                        modifier = Modifier.size(24.dp),
                                        contentScale = ContentScale.Fit,
                                        loading = {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(14.dp),
                                                strokeWidth = 1.8.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        },
                                        error = {
                                            Icon(
                                                imageVector = if (tailscaleConnected) Icons.Default.CheckCircle else Icons.Default.Warning,
                                                contentDescription = null,
                                                tint = if (tailscaleConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.tertiary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (tailscaleConnected) stringResource(R.string.tailscale_connected) else stringResource(R.string.media_tailscale_needed_title),
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = if (tailscaleConnected) stringResource(R.string.media_tailscale_connected_desc) else stringResource(R.string.media_tailscale_needed_desc),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(imageVector = Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                        }
                    }
                }
            }

            if (gridEntries.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.surfaceContainer
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(22.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = stringResource(R.string.media_empty_title),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.media_empty_body),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            FilledTonalButton(onClick = {
                                firstUnconfiguredType?.let { type -> onNavigateToLogin(type, null) }
                            }, enabled = firstUnconfiguredType != null) {
                                Text(stringResource(R.string.settings_add_instance))
                            }
                        }
                    }
                }
            }

            gridItems(gridEntries, key = { it.key }) { entry ->
                when (entry) {
                    is MediaGridEntry.Disconnected -> {
                        MediaServiceGridCard(
                            type = entry.type,
                            label = mediaServiceDisplayName(entry.type),
                            isConnected = false,
                            useCyberpunkCards = homeCyberpunkCardsEnabled,
                            instanceId = null,
                            reachable = null,
                            pinging = false,
                            previewState = null,
                            onRequestPreview = null,
                            onRefresh = null,
                            onClick = { onNavigateToLogin(entry.type, null) }
                        )
                    }
                    is MediaGridEntry.Connected -> {
                        MediaServiceGridCard(
                            type = entry.type,
                            label = entry.instance.label,
                            isConnected = true,
                            useCyberpunkCards = homeCyberpunkCardsEnabled,
                            instanceId = entry.instance.id,
                            reachable = reachability[entry.instance.id],
                            pinging = pinging[entry.instance.id] == true,
                            previewState = cardPreviewState[entry.instance.id],
                            onRequestPreview = { viewModel.requestCardPreview(entry.instance.id) },
                            onRefresh = { viewModel.refreshInstance(entry.instance.id) },
                            onClick = { onNavigateToService(entry.type, entry.instance.id) }
                        )
                    }
                }
            }
        }
    }

    if (showReorderDialog) {
        MediaServiceOrderDialog(
            serviceOrder = viewModel.serviceOrder.collectAsStateWithLifecycle().value.filter { it.isArrStack },
            hiddenServices = hiddenServices,
            onMoveUp = { type -> viewModel.moveMediaService(type, -1) },
            onMoveDown = { type -> viewModel.moveMediaService(type, 1) },
            onToggleVisibility = { type -> viewModel.toggleServiceVisibility(type) },
            onDismiss = { showReorderDialog = false }
        )
    }
}

private sealed class MediaGridEntry(val key: String) {
    class Disconnected(val type: ServiceType) : MediaGridEntry("disconnected-${type.name}")
    class Connected(val type: ServiceType, val instance: ServiceInstance) : MediaGridEntry("connected-${instance.id}")
}

@Composable
private fun MediaServiceOrderDialog(
    serviceOrder: List<ServiceType>,
    hiddenServices: Set<String>,
    onMoveUp: (ServiceType) -> Unit,
    onMoveDown: (ServiceType) -> Unit,
    onToggleVisibility: (ServiceType) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.home_reorder_services)) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                serviceOrder.forEachIndexed { index, type ->
                    val isHidden = hiddenServices.contains(type.name)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = mediaServiceDisplayName(type),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (isHidden) {
                                Text(
                                    text = stringResource(R.string.settings_hidden_badge),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        IconButton(onClick = { onToggleVisibility(type) }) {
                            Icon(
                                imageVector = if (isHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = stringResource(
                                    if (isHidden) R.string.settings_show_service_generic else R.string.settings_hide_service_generic
                                )
                            )
                        }
                        IconButton(
                            onClick = { onMoveUp(type) },
                            enabled = index > 0
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.settings_move_up)
                            )
                        }
                        IconButton(
                            onClick = { onMoveDown(type) },
                            enabled = index < serviceOrder.lastIndex
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.settings_move_down)
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun MediaServiceGridCard(
    type: ServiceType,
    label: String,
    isConnected: Boolean,
    useCyberpunkCards: Boolean,
    instanceId: String?,
    reachable: Boolean?,
    pinging: Boolean,
    previewState: MediaArrCardPreviewUiState?,
    onRequestPreview: (() -> Unit)?,
    onRefresh: (() -> Unit)?,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val cardColor = if (useCyberpunkCards) {
        if (isDarkTheme) Color(0xFF10161F) else Color(0xFFFBFCFF)
    } else {
        if (isConnected) type.primaryColor.copy(alpha = 0.08f) else MaterialTheme.colorScheme.surfaceContainerLow
    }
    val cardBorder = if (useCyberpunkCards) {
        BorderStroke(
            1.25.dp,
            type.primaryColor.copy(alpha = if (isDarkTheme) 0.82f else 0.58f)
        )
    } else {
        BorderStroke(
            1.dp,
            type.primaryColor.copy(alpha = if (isConnected) 0.2f else 0.08f)
        )
    }
    val brandColor = type.primaryColor
    val cardBrush = remember(brandColor, useCyberpunkCards, isDarkTheme, isConnected) {
        if (useCyberpunkCards || isConnected) {
            val startAlpha = when {
                useCyberpunkCards && isDarkTheme -> 0.13f
                useCyberpunkCards -> 0.075f
                isDarkTheme -> 0.095f
                else -> 0.06f
            }
            Brush.linearGradient(
                colors = listOf(
                    brandColor.copy(alpha = startAlpha),
                    Color.Transparent
                ),
                start = Offset(0f, 0f),
                end = Offset(580f, 520f)
            )
        } else {
            null
        }
    }

    LaunchedEffect(instanceId, reachable) {
        if (instanceId != null && isConnected && reachable == true) {
            onRequestPreview?.invoke()
        }
    }

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = cardColor,
        border = cardBorder
    ) {
        Box {
            if (cardBrush != null) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(cardBrush)
                )
            }

            Column(
                modifier = Modifier
                    .padding(14.dp)
                    .height(194.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    ServiceIcon(type = type, size = 40.dp, iconSize = 24.dp, cornerRadius = 12.dp)

                    Column(
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (isConnected && reachable == false && onRefresh != null) {
                            IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh),
                                    tint = type.primaryColor
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                MediaCardStatusChip(
                    isConnected = isConnected,
                    pinging = pinging,
                    reachable = reachable,
                    accentColor = type.primaryColor
                )

                if (isConnected) {
                    when {
                        previewState?.isLoading == true && previewState.preview == null -> {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(3.dp),
                                color = type.primaryColor,
                                trackColor = type.primaryColor.copy(alpha = 0.16f)
                            )
                        }

                        previewState?.preview != null -> {
                            previewState.preview?.let { preview ->
                                preview.headline?.takeIf { it.isNotBlank() }?.let { headline ->
                                    Text(
                                        text = localizedHighlight(headline),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    preview.metrics.take(2).forEach { metric ->
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = localizedMetricLabel(metric.label),
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis,
                                                modifier = Modifier.weight(1f)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = localizedMetricValue(
                                                    MediaArrMetric(
                                                        label = metric.label,
                                                        value = metric.value
                                                    )
                                                ),
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                }

                                preview.metrics.getOrNull(2)?.let { extraMetric ->
                                    MediaCardMetricPill(
                                        label = localizedMetricLabel(extraMetric.label),
                                        value = localizedMetricValue(
                                            MediaArrMetric(
                                                label = extraMetric.label,
                                                value = extraMetric.value
                                            )
                                        ),
                                        accent = type.primaryColor
                                    )
                                }
                            }
                        }

                        previewState?.hasError == true && reachable == true -> {
                            Text(
                                text = stringResource(R.string.no_data),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(1f, fill = false))
            }
        }
    }
}

@Composable
private fun MediaCardStatusChip(
    isConnected: Boolean,
    pinging: Boolean,
    reachable: Boolean?,
    accentColor: Color
) {
    val statusText = when {
        !isConnected -> stringResource(R.string.login_connect)
        reachable == true -> stringResource(R.string.home_status_online)
        pinging -> stringResource(R.string.home_verifying)
        reachable == false -> stringResource(R.string.home_status_offline)
        else -> stringResource(R.string.home_verifying)
    }
    val statusColor = when {
        !isConnected -> MaterialTheme.colorScheme.onSurfaceVariant
        reachable == true -> accentColor
        pinging -> MaterialTheme.colorScheme.tertiary
        reachable == false -> accentColor
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(999.dp),
        color = statusColor.copy(alpha = 0.14f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            if (isConnected && pinging && reachable != true) {
                CircularProgressIndicator(
                    modifier = Modifier.size(8.dp),
                    strokeWidth = 1.5.dp,
                    color = statusColor
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(statusColor, shape = RoundedCornerShape(3.dp))
                )
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = statusColor,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MediaCardMetricPill(
    label: String,
    value: String,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = accent.copy(alpha = 0.14f),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun MediaContentSearchPanel(
    serviceType: ServiceType,
    serviceLabel: String,
    query: String,
    onQueryChange: (String) -> Unit,
    actionsEnabled: Boolean,
    isSearching: Boolean,
    canClear: Boolean,
    onSearch: () -> Unit,
    onClear: () -> Unit
) {
    val accent = serviceType.primaryColor
    val accentContent = if (accent.luminance() < 0.45f) Color.White else Color(0xFF101114)
    MediaAccentSection(
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        borderAlpha = 0.18f,
        contentPadding = PaddingValues(10.dp)
    ) {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(15.dp),
                singleLine = true,
                placeholder = {
                    Text(
                        stringResource(R.string.media_content_search_placeholder, serviceLabel),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        tint = accent
                    )
                },
                trailingIcon = {
                    if (query.isNotBlank()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = stringResource(R.string.media_content_search_clear),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TextFieldDefaults.colors(
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                    disabledIndicatorColor = Color.Transparent,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    disabledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                    cursorColor = accent
                )
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Button(
                    onClick = onSearch,
                    enabled = actionsEnabled && !isSearching && query.trim().length >= 2,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accent,
                        contentColor = accentContent,
                        disabledContainerColor = accent.copy(alpha = 0.28f),
                        disabledContentColor = accentContent.copy(alpha = 0.6f)
                    )
                ) {
                    if (isSearching) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.media_content_search_button),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                FilledTonalButton(
                    onClick = onClear,
                    enabled = actionsEnabled && !isSearching && canClear,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = accent.copy(alpha = 0.16f),
                        contentColor = accent,
                        disabledContainerColor = accent.copy(alpha = 0.08f),
                        disabledContentColor = accent.copy(alpha = 0.55f)
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.media_content_search_clear),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
    }
}

@Composable
private fun MediaOverviewChip(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    tone: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.secondaryContainer
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = tone,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun MediaInstanceRow(
    instance: ServiceInstance,
    reachable: Boolean?,
    pinging: Boolean,
    onOpen: () -> Unit,
    onEdit: () -> Unit
) {
    val statusText = when {
        pinging -> stringResource(R.string.home_verifying)
        reachable == true -> stringResource(R.string.home_status_online)
        reachable == false -> stringResource(R.string.home_status_offline)
        else -> stringResource(R.string.no_data)
    }

    val statusColor = when {
        pinging -> MaterialTheme.colorScheme.tertiary
        reachable == true -> MaterialTheme.colorScheme.primary
        reachable == false -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = instance.label,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = instance.url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onOpen, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.media_open_dashboard))
                }
                OutlinedButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.settings_services_edit))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaServiceDashboardScreen(
    serviceType: ServiceType,
    onNavigateBack: () -> Unit,
    viewModel: MediaServiceDashboardViewModel = hiltViewModel()
) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()
    val instance by viewModel.instance.collectAsStateWithLifecycle()
    val isLoading by viewModel.isLoading.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    val actionMessage by viewModel.lastActionMessage.collectAsStateWithLifecycle()
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle()
    val isSearching by viewModel.isSearching.collectAsStateWithLifecycle()
    val searchError by viewModel.searchError.collectAsStateWithLifecycle()
    val lastSearchQuery by viewModel.lastSearchQuery.collectAsStateWithLifecycle()
    val pendingRequestConfiguration by viewModel.pendingRequestConfiguration.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val actionMessageText = actionMessage?.let { actionResultLabel(it) }
    LaunchedEffect(Unit) {
        viewModel.load()
    }

    LaunchedEffect(serviceType) {
        if (serviceType == ServiceType.QBITTORRENT) {
            while (isActive) {
                delay(QBITTORRENT_REFRESH_INTERVAL_MS)
                viewModel.load()
            }
        }
    }

    LaunchedEffect(actionMessageText) {
        actionMessageText?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeActionMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ServiceIcon(
                            type = serviceType,
                            size = 26.dp,
                            iconSize = 16.dp,
                            cornerRadius = 8.dp
                        )
                        Text(
                            text = mediaServiceDisplayName(serviceType),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.load() }, enabled = !isLoading) {
                        Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.refresh))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        if (isLoading && snapshot == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = serviceType.primaryColor)
            }
            return@Scaffold
        }

        if (error != null && snapshot == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                    Text(text = error.orEmpty(), color = MaterialTheme.colorScheme.error)
                    FilledTonalButton(onClick = { viewModel.load() }) {
                        Text(stringResource(R.string.retry))
                    }
                }
            }
            return@Scaffold
        }

        val current = snapshot
        if (current == null) return@Scaffold

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (isLoading) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = serviceType.primaryColor
                    )
                }

                MediaServiceDashboardBody(
                    snapshot = current,
                    instance = instance,
                    error = error,
                    onAction = {
                        if (!isLoading) viewModel.runAction(it)
                    },
                    onQbTorrentAction = { hash, name, action ->
                        if (!isLoading) {
                            viewModel.runQbTorrentAction(hash = hash, name = name, action = action)
                        }
                    },
                    onJellyseerrRequestAction = { requestId, title, approve ->
                        if (!isLoading) {
                            viewModel.runJellyseerrRequestAction(requestId = requestId, title = title, approve = approve)
                        }
                    },
                    onFlaresolverrDestroySession = { sessionId ->
                        if (!isLoading) {
                            viewModel.destroyFlaresolverrSession(sessionId)
                        }
                    },
                    onRetry = { if (!isLoading) viewModel.load() },
                    actionsEnabled = !isLoading,
                    searchResults = searchResults,
                    isSearching = isSearching,
                    searchError = searchError,
                    lastSearchQuery = lastSearchQuery,
                    pendingRequestConfiguration = pendingRequestConfiguration,
                    onSearch = { query -> viewModel.search(query) },
                    onClearSearch = { viewModel.clearSearch() },
                    onRequestSearchResult = { result ->
                        if (!isLoading) viewModel.requestSearchResult(result)
                    },
                    onConfirmRequestConfiguration = { selection ->
                        if (!isLoading) viewModel.confirmRequestConfiguration(selection)
                    },
                    onDismissRequestConfiguration = { viewModel.dismissPendingRequestConfiguration() },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun MediaServiceDashboardBody(
    snapshot: MediaArrSnapshot,
    instance: ServiceInstance?,
    error: String?,
    onAction: (MediaArrAction) -> Unit,
    onQbTorrentAction: (String, String?, MediaArrAction) -> Unit,
    onJellyseerrRequestAction: (Int, String?, Boolean) -> Unit,
    onFlaresolverrDestroySession: (String) -> Unit,
    onRetry: () -> Unit,
    actionsEnabled: Boolean,
    searchResults: List<MediaArrSearchResultItem>,
    isSearching: Boolean,
    searchError: String?,
    lastSearchQuery: String,
    pendingRequestConfiguration: MediaServiceDashboardViewModel.PendingRequestConfiguration?,
    onSearch: (String) -> Unit,
    onClearSearch: () -> Unit,
    onRequestSearchResult: (MediaArrSearchResultItem) -> Unit,
    onConfirmRequestConfiguration: (MediaArrRequestSelection) -> Unit,
    onDismissRequestConfiguration: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val clipboardScope = rememberCoroutineScope()
    val listPreviewCount = 4
    var qbFilter by rememberSaveable(snapshot.serviceLabel) { mutableStateOf(QbTorrentFilter.ALL.name) }
    var searchQuery by rememberSaveable(snapshot.serviceLabel) { mutableStateOf("") }
    var contentSearchQuery by rememberSaveable(snapshot.serviceLabel) { mutableStateOf(lastSearchQuery) }
    var jellyseerrRequestsExpanded by rememberSaveable("${snapshot.serviceLabel}-requests-expanded") { mutableStateOf(false) }
    var libraryExpanded by rememberSaveable("${snapshot.serviceLabel}-library-expanded") { mutableStateOf(false) }
    var downloadsExpanded by rememberSaveable("${snapshot.serviceLabel}-downloads-expanded") { mutableStateOf(false) }
    var recentHistoryExpanded by rememberSaveable("${snapshot.serviceLabel}-history-expanded") { mutableStateOf(false) }
    var highlightsExpanded by rememberSaveable("${snapshot.serviceLabel}-highlights-expanded") { mutableStateOf(false) }
    var warningsExpanded by rememberSaveable("${snapshot.serviceLabel}-warnings-expanded") { mutableStateOf(false) }
    val selectedFilter = remember(qbFilter) {
        runCatching { QbTorrentFilter.valueOf(qbFilter) }.getOrElse { QbTorrentFilter.ALL }
    }
    val filteredQbItems = remember(snapshot.qbittorrentItems, selectedFilter, searchQuery) {
        qbFilterAndSort(snapshot.qbittorrentItems, selectedFilter, searchQuery)
    }
    val supportsContentSearch = remember(snapshot.serviceType) {
        snapshot.serviceType in setOf(
            ServiceType.RADARR,
            ServiceType.SONARR,
            ServiceType.LIDARR,
            ServiceType.JELLYSEERR,
            ServiceType.PROWLARR
        )
    }
    var selectedSearchResult by remember(snapshot.serviceLabel) {
        mutableStateOf<MediaArrSearchResultItem?>(null)
    }
    LaunchedEffect(lastSearchQuery, snapshot.serviceLabel) {
        if (contentSearchQuery != lastSearchQuery) {
            contentSearchQuery = lastSearchQuery
        }
    }

    val isArrService = snapshot.serviceType in setOf(ServiceType.RADARR, ServiceType.SONARR, ServiceType.LIDARR)
    val isGenericMediaService = snapshot.serviceType in setOf(
        ServiceType.JELLYSEERR,
        ServiceType.PROWLARR,
        ServiceType.BAZARR,
        ServiceType.GLUETUN,
        ServiceType.FLARESOLVERR
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        if (snapshot.serviceType != ServiceType.QBITTORRENT) {
            item {
                MediaDashboardHeaderCard(snapshot = snapshot)
            }
        }

        if (isGenericMediaService &&
            snapshot.serviceType != ServiceType.GLUETUN &&
            snapshot.details.isNotEmpty()
        ) {
            item {
                MediaServiceDetailsCard(details = snapshot.details)
            }
        }

        if (snapshot.serviceType == ServiceType.QBITTORRENT) {
            item {
                QbittorrentOverviewSection(snapshot = snapshot)
            }

            item {
                QbittorrentFilterBar(
                    selectedFilter = selectedFilter,
                    onSelect = { qbFilter = it.name }
                )
            }

            item {
                TextField(
                    value = searchQuery,
                    onValueChange = { value -> searchQuery = value },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.media_qb_search_placeholder)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    colors = TextFieldDefaults.colors(
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        disabledIndicatorColor = Color.Transparent
                    )
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.media_qb_torrents_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        QbActionIconButton(
                            icon = Icons.Default.Refresh,
                            tint = MaterialTheme.colorScheme.primary,
                            onClick = { onAction(MediaArrAction.QBITTORRENT_FORCE_RECHECK) },
                            enabled = actionsEnabled
                        )
                        QbActionIconButton(
                            icon = Icons.Default.PlayArrow,
                            tint = Color(0xFF3BCB5A),
                            onClick = { onAction(MediaArrAction.QBITTORRENT_RESUME_ALL) },
                            enabled = actionsEnabled
                        )
                        QbActionIconButton(
                            icon = Icons.Default.Pause,
                            tint = Color(0xFFE0AE2B),
                            onClick = { onAction(MediaArrAction.QBITTORRENT_PAUSE_ALL) },
                            enabled = actionsEnabled
                        )
                    }
                }
            }

            if (filteredQbItems.isEmpty()) {
                item {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = stringResource(R.string.media_qb_no_results),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(filteredQbItems, key = { it.hash }) { torrent ->
                    QbTorrentRow(
                        torrent = torrent,
                        actionsEnabled = actionsEnabled,
                        onAction = { action ->
                            onQbTorrentAction(torrent.hash, torrent.name, action)
                        }
                    )
                }
            }
        } else {
            if (snapshot.serviceType == ServiceType.JELLYSEERR) {
                item {
                    JellyseerrOverviewCard(
                        snapshot = snapshot,
                        enabled = actionsEnabled,
                        onAction = onAction
                    )
                }
            } else if (snapshot.serviceType == ServiceType.PROWLARR) {
                item {
                    ProwlarrOverviewCard(
                        snapshot = snapshot,
                        accent = snapshot.serviceType.primaryColor,
                        enabled = actionsEnabled,
                        onAction = onAction
                    )
                }
            } else if (snapshot.serviceType == ServiceType.BAZARR) {
                item {
                    BazarrOverviewCard(snapshot = snapshot)
                }
            } else if (snapshot.serviceType == ServiceType.GLUETUN) {
                item {
                    GluetunOverviewCard(
                        snapshot = snapshot,
                        accent = snapshot.serviceType.primaryColor,
                        enabled = actionsEnabled,
                        onAction = onAction
                    )
                }
            } else if (snapshot.serviceType == ServiceType.FLARESOLVERR) {
                item {
                    FlaresolverrOverviewCard(
                        snapshot = snapshot,
                        accent = snapshot.serviceType.primaryColor,
                        enabled = actionsEnabled,
                        onAction = onAction,
                        onFlaresolverrDestroySession = onFlaresolverrDestroySession
                    )
                }
            } else if (!isArrService && snapshot.metrics.isNotEmpty()) {
                item {
                    MediaMetricGrid(
                        metrics = snapshot.metrics,
                        accent = snapshot.serviceType.primaryColor
                    )
                }
            }

            if (snapshot.actions.isNotEmpty() && snapshot.serviceType !in setOf(
                    ServiceType.JELLYSEERR,
                    ServiceType.PROWLARR,
                    ServiceType.GLUETUN,
                    ServiceType.FLARESOLVERR
                )
            ) {
                item {
                    MediaActionGrid(
                        actions = snapshot.actions,
                        accent = snapshot.serviceType.primaryColor,
                        enabled = actionsEnabled,
                        onAction = onAction
                    )
                }
            }

            if (supportsContentSearch) {
                item {
                    MediaContentSearchPanel(
                        serviceType = snapshot.serviceType,
                        serviceLabel = mediaServiceDisplayName(snapshot.serviceType),
                        query = contentSearchQuery,
                        onQueryChange = { value -> contentSearchQuery = value },
                        actionsEnabled = actionsEnabled,
                        isSearching = isSearching,
                        canClear = lastSearchQuery.isNotBlank() || contentSearchQuery.isNotBlank(),
                        onSearch = { onSearch(contentSearchQuery) },
                        onClear = {
                            contentSearchQuery = ""
                            onClearSearch()
                        }
                    )
                }

                if (isSearching) {
                    item {
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp),
                            color = snapshot.serviceType.primaryColor,
                            trackColor = snapshot.serviceType.primaryColor.copy(alpha = 0.16f)
                        )
                    }
                }

                if (searchError != null) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = searchError,
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer,
                                maxLines = 3,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                } else if (lastSearchQuery.isNotBlank() && !isSearching && searchResults.isEmpty()) {
                    item {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerLow,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = stringResource(R.string.media_content_search_no_results, lastSearchQuery),
                                modifier = Modifier.padding(10.dp),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                items(searchResults, key = { "${it.id}-${it.title}" }) { result ->
                    MediaContentSearchResultRow(
                        result = result,
                        onClick = { selectedSearchResult = result }
                    )
                }
            }

            if (snapshot.downloadItems.isNotEmpty() && isArrService) {
                val visibleDownloads = if (downloadsExpanded) snapshot.downloadItems else snapshot.downloadItems.take(listPreviewCount)
                val remainingDownloads = (snapshot.downloadItems.size - visibleDownloads.size).coerceAtLeast(0)
                item {
                    Text(
                        text = stringResource(R.string.media_metric_downloading),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(visibleDownloads, key = { it.id }) { item ->
                    MediaDownloadRow(item = item, accent = snapshot.serviceType.primaryColor)
                }

                if (remainingDownloads > 0 || downloadsExpanded) {
                    item {
                        MediaExpandButton(
                            expanded = downloadsExpanded,
                            remaining = remainingDownloads,
                            onToggle = { downloadsExpanded = !downloadsExpanded }
                        )
                    }
                }
            }

            if (snapshot.serviceType == ServiceType.JELLYSEERR && snapshot.jellyseerrRequests.isNotEmpty()) {
                val visibleRequests = if (jellyseerrRequestsExpanded) snapshot.jellyseerrRequests else snapshot.jellyseerrRequests.take(listPreviewCount)
                val remainingRequests = (snapshot.jellyseerrRequests.size - visibleRequests.size).coerceAtLeast(0)
                item {
                    Text(
                        text = stringResource(R.string.media_jellyseerr_requests_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(visibleRequests, key = { it.id }) { request ->
                    JellyseerrRequestRow(
                        title = request.title,
                        status = request.status,
                        requestedBy = request.requestedBy,
                        requestedAt = request.requestedAt,
                        isPending = request.isPending,
                        actionsEnabled = actionsEnabled,
                        onApprove = { onJellyseerrRequestAction(request.id, request.title, true) },
                        onDecline = { onJellyseerrRequestAction(request.id, request.title, false) }
                    )
                }

                if (remainingRequests > 0 || jellyseerrRequestsExpanded) {
                    item {
                        MediaExpandButton(
                            expanded = jellyseerrRequestsExpanded,
                            remaining = remainingRequests,
                            onToggle = { jellyseerrRequestsExpanded = !jellyseerrRequestsExpanded }
                        )
                    }
                }
            }

            if (snapshot.libraryItems.isNotEmpty()) {
                val visibleLibraryItems = if (libraryExpanded) snapshot.libraryItems else snapshot.libraryItems.take(listPreviewCount)
                val remainingLibraryItems = (snapshot.libraryItems.size - visibleLibraryItems.size).coerceAtLeast(0)
                item {
                    Text(
                        text = localizedLibraryTitle(snapshot.libraryTitle),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(visibleLibraryItems, key = { item -> item.id }) { item ->
                    MediaContentSearchResultRow(
                        result = item,
                        onClick = { selectedSearchResult = item }
                    )
                }

                if (remainingLibraryItems > 0 || libraryExpanded) {
                    item {
                        MediaExpandButton(
                            expanded = libraryExpanded,
                            remaining = remainingLibraryItems,
                            onToggle = { libraryExpanded = !libraryExpanded }
                        )
                    }
                }
            }

            if (snapshot.recentHistoryItems.isNotEmpty() && isArrService) {
                val visibleHistory = if (recentHistoryExpanded) snapshot.recentHistoryItems else snapshot.recentHistoryItems.take(listPreviewCount)
                val remainingHistory = (snapshot.recentHistoryItems.size - visibleHistory.size).coerceAtLeast(0)
                item {
                    Text(
                        text = stringResource(R.string.media_recent_history_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(visibleHistory, key = { it.id }) { item ->
                    MediaHistoryRow(item = item)
                }

                if (remainingHistory > 0 || recentHistoryExpanded) {
                    item {
                        MediaExpandButton(
                            expanded = recentHistoryExpanded,
                            remaining = remainingHistory,
                            onToggle = { recentHistoryExpanded = !recentHistoryExpanded }
                        )
                    }
                }
            }

            if (snapshot.highlights.isNotEmpty() && snapshot.serviceType == ServiceType.PROWLARR) {
                val visibleHighlights = if (highlightsExpanded) snapshot.highlights else snapshot.highlights.take(listPreviewCount)
                val remainingHighlights = (snapshot.highlights.size - visibleHighlights.size).coerceAtLeast(0)
                item {
                    Text(
                        text = stringResource(R.string.media_recent_history_title),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                items(visibleHighlights) { value ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = localizedHighlight(value),
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }

                if (remainingHighlights > 0 || highlightsExpanded) {
                    item {
                        MediaExpandButton(
                            expanded = highlightsExpanded,
                            remaining = remainingHighlights,
                            onToggle = { highlightsExpanded = !highlightsExpanded }
                        )
                    }
                }
            }

            if (snapshot.warnings.isNotEmpty() && snapshot.serviceType == ServiceType.PROWLARR) {
                val visibleWarnings = if (warningsExpanded) snapshot.warnings else snapshot.warnings.take(listPreviewCount)
                val remainingWarnings = (snapshot.warnings.size - visibleWarnings.size).coerceAtLeast(0)
                item {
                    Text(
                        text = when {
                            snapshot.serviceType == ServiceType.PROWLARR -> localizedMetricLabel("Issues")
                            else -> stringResource(R.string.media_warnings_title)
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                items(visibleWarnings) { value ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.42f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = value,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                if (remainingWarnings > 0 || warningsExpanded) {
                    item {
                        MediaExpandButton(
                            expanded = warningsExpanded,
                            remaining = remainingWarnings,
                            onToggle = { warningsExpanded = !warningsExpanded }
                        )
                    }
                }
            }

            if (isGenericMediaService) {
                item {
                    MediaServiceFooterCard(
                        instance = instance,
                        onRefresh = onRetry,
                        onCopy = { label, value ->
                            clipboardScope.launch {
                                clipboard.setClipEntry(ClipEntry(ClipData.newPlainText(label, value)))
                                Toast.makeText(
                                    context,
                                    context.getString(R.string.copied_to_clipboard),
                                    Toast.LENGTH_SHORT
                                ).show()
                            }
                        },
                        onOpen = { url ->
                            runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, url.toUri())) }
                        }
                    )
                }
            }
        }

        if (error != null) {
            item {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                        Text(
                            text = error,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(onClick = onRetry, enabled = actionsEnabled) {
                            Text(stringResource(R.string.retry))
                        }
                    }
                }
            }
        }
    }

    selectedSearchResult?.let { result ->
        MediaContentSearchResultDialog(
            result = result,
            onDismiss = { selectedSearchResult = null },
            onRequest = {
                selectedSearchResult = null
                onRequestSearchResult(result)
            }
        )
    }

    pendingRequestConfiguration?.let { pending ->
        MediaRequestConfigurationDialog(
            title = pending.configuration.title,
            configuration = pending.configuration,
            onDismiss = onDismissRequestConfiguration,
            onConfirm = onConfirmRequestConfiguration
        )
    }
}

@Composable
private fun MediaDashboardHeaderCard(snapshot: MediaArrSnapshot) {
    val accent = snapshot.serviceType.primaryColor
    val isCompact = snapshot.serviceType in setOf(
        ServiceType.RADARR,
        ServiceType.SONARR,
        ServiceType.LIDARR,
        ServiceType.QBITTORRENT
    )

    MediaAccentSection(
        accent = accent,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        borderAlpha = 0.22f,
        contentPadding = PaddingValues(0.dp)
    ) {
        if (isCompact) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceIcon(
                    type = snapshot.serviceType,
                    size = 34.dp,
                    iconSize = 20.dp,
                    cornerRadius = 11.dp
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = snapshot.serviceLabel,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = snapshot.version ?: stringResource(R.string.no_data),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                snapshot.status?.takeIf { it.isNotBlank() }?.let { status ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = status.replaceFirstChar { it.uppercase() },
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ServiceIcon(
                    type = snapshot.serviceType,
                    size = 56.dp,
                    iconSize = 32.dp,
                    cornerRadius = 14.dp
                )
                Text(
                    text = snapshot.serviceLabel,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                mediaServiceSubtitle(snapshot.serviceType)?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = Color(0xFF19A34A).copy(alpha = 0.18f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(Color(0xFF44D15F), RoundedCornerShape(5.dp))
                        )
                        Text(
                            text = stringResource(R.string.home_status_online),
                            style = MaterialTheme.typography.labelMedium,
                            color = Color(0xFF44D15F),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaServiceDetailsCard(details: List<MediaArrMetric>) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            details.forEachIndexed { index, detail ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = localizedMetricLabel(detail.label),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.widthIn(min = 82.dp, max = 104.dp),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(14.dp))
                    Text(
                        text = localizedMetricValue(detail),
                        style = if (detail.label == "Commit") {
                            MaterialTheme.typography.bodySmall
                        } else {
                            MaterialTheme.typography.bodyMedium
                        },
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (index != details.lastIndex) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.16f))
                }
            }
        }
    }
}

@Composable
private fun MediaMetricBadge(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = accent.copy(alpha = 0.12f),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = localizedMetricLabel(label),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleSmall,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun MediaActionPillButton(
    label: String,
    accent: Color,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val pillColor = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.42f)
    } else {
        accent.copy(alpha = 0.08f)
    }
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = pillColor,
        border = BorderStroke(1.dp, accent.copy(alpha = if (isDarkTheme) 0.12f else 0.20f)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun JellyseerrOverviewCard(
    snapshot: MediaArrSnapshot,
    enabled: Boolean,
    onAction: (MediaArrAction) -> Unit
) {
    val accent = snapshot.serviceType.primaryColor
    MediaAccentSection(accent = accent, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = localizedMetricLabel("Requests"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                snapshot.version?.let { version ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "v$version",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaMetricBadge("Requests", snapshot.metric("Requests")?.value ?: "0", accent, Modifier.weight(1f))
                MediaMetricBadge("Pending", snapshot.metric("Pending")?.value ?: "0", Color(0xFFE0AE2B), Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaMetricBadge("Approved", snapshot.metric("Approved")?.value ?: "0", Color(0xFF59A5FF), Modifier.weight(1f))
                MediaMetricBadge("Available", snapshot.metric("Available")?.value ?: "0", Color(0xFF3BCB5A), Modifier.weight(1f))
            }

            val approvePending = snapshot.actions.contains(MediaArrAction.JELLYSEERR_APPROVE_PENDING)
            val declinePending = snapshot.actions.contains(MediaArrAction.JELLYSEERR_DECLINE_PENDING)
            if (approvePending || declinePending) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (approvePending) {
                        MediaActionPillButton(
                            label = actionLabel(MediaArrAction.JELLYSEERR_APPROVE_PENDING),
                            accent = accent,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        ) { onAction(MediaArrAction.JELLYSEERR_APPROVE_PENDING) }
                    }
                    if (declinePending) {
                        MediaActionPillButton(
                            label = actionLabel(MediaArrAction.JELLYSEERR_DECLINE_PENDING),
                            accent = accent,
                            enabled = enabled,
                            modifier = Modifier.weight(1f)
                        ) { onAction(MediaArrAction.JELLYSEERR_DECLINE_PENDING) }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaActionPillButton(
                    label = actionCompactLabel(MediaArrAction.JELLYSEERR_RUN_RECENT_SCAN),
                    accent = accent,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { onAction(MediaArrAction.JELLYSEERR_RUN_RECENT_SCAN) }
                MediaActionPillButton(
                    label = actionCompactLabel(MediaArrAction.JELLYSEERR_RUN_FULL_SCAN),
                    accent = accent,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { onAction(MediaArrAction.JELLYSEERR_RUN_FULL_SCAN) }
            }
    }
}

@Composable
private fun ProwlarrOverviewCard(
    snapshot: MediaArrSnapshot,
    accent: Color,
    enabled: Boolean,
    onAction: (MediaArrAction) -> Unit
) {
    MediaAccentSection(accent = accent, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = localizedMetricLabel("Indexers"),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                snapshot.version?.let { version ->
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                    ) {
                        Text(
                            text = "v$version",
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaMetricBadge("Indexers", snapshot.metric("Indexers")?.value ?: "0", Color(0xFF10E4B0), Modifier.weight(1f))
                MediaMetricBadge("Apps", snapshot.metric("Applications")?.value ?: snapshot.metric("Apps")?.value ?: "0", Color(0xFF59A5FF), Modifier.weight(1f))
                MediaMetricBadge("Issues", snapshot.metric("Health")?.value ?: snapshot.metric("Issues")?.value ?: "0", Color(0xFFE0AE2B), Modifier.weight(1f))
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaActionPillButton(
                    label = actionCompactLabel(MediaArrAction.PROWLARR_TEST_INDEXERS),
                    accent = accent,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { onAction(MediaArrAction.PROWLARR_TEST_INDEXERS) }
                MediaActionPillButton(
                    label = actionCompactLabel(MediaArrAction.PROWLARR_SYNC_APPS),
                    accent = accent,
                    enabled = enabled,
                    modifier = Modifier.weight(1f)
                ) { onAction(MediaArrAction.PROWLARR_SYNC_APPS) }
            }

            MediaActionPillButton(
                label = actionCompactLabel(MediaArrAction.PROWLARR_HEALTH_CHECK),
                accent = accent,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) { onAction(MediaArrAction.PROWLARR_HEALTH_CHECK) }
    }
}

@Composable
private fun BazarrOverviewCard(snapshot: MediaArrSnapshot) {
    val accent = snapshot.serviceType.primaryColor
    MediaAccentSection(accent = accent, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.media_subtitles_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            val metrics = snapshot.metrics.filter { it.label in setOf("Movies", "Providers", "Wanted", "Missing", "Health", "Tasks") }
            metrics.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    row.forEach { metric ->
                        MediaMetricBadge(
                            label = metric.label,
                            value = localizedMetricValue(metric),
                            accent = accent,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }

            if (snapshot.highlights.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = stringResource(R.string.media_service_title),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    snapshot.highlights.take(3).forEach { task ->
                        Text(
                            text = task,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (snapshot.warnings.isNotEmpty()) {
                snapshot.warnings.take(3).forEach { warning ->
                    Text(
                        text = warning,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (snapshot.metrics.isEmpty() && snapshot.highlights.isEmpty() && snapshot.warnings.isEmpty()) {
                Text(
                    text = stringResource(R.string.no_data),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
    }
}

@Composable
private fun GluetunOverviewCard(
    snapshot: MediaArrSnapshot,
    accent: Color,
    enabled: Boolean,
    onAction: (MediaArrAction) -> Unit
) {
    MediaAccentSection(accent = accent, modifier = Modifier.fillMaxWidth()) {
            Text(
                text = stringResource(R.string.media_vpn_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )

            val ordered = listOf("Status", "Public IP", "Country", "Server", "Provider", "Forwarded Port")
            ordered.forEach { label ->
                snapshot.metrics.firstOrNull { it.label.equals(label, ignoreCase = true) }?.let { metric ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = localizedMetricLabel(metric.label),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = localizedMetricValue(metric),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            MediaActionPillButton(
                label = actionLabel(MediaArrAction.GLUETUN_RESTART_VPN),
                accent = accent,
                enabled = enabled,
                modifier = Modifier.fillMaxWidth()
            ) { onAction(MediaArrAction.GLUETUN_RESTART_VPN) }
    }
}

@Composable
private fun FlaresolverrOverviewCard(
    snapshot: MediaArrSnapshot,
    accent: Color,
    enabled: Boolean,
    onAction: (MediaArrAction) -> Unit,
    onFlaresolverrDestroySession: (String) -> Unit
) {
    var sessionsExpanded by rememberSaveable(snapshot.serviceLabel) { mutableStateOf(false) }
    MediaAccentSection(accent = accent, modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.media_service_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                OutlinedButton(onClick = { onAction(MediaArrAction.FLARESOLVERR_CREATE_SESSION) }, enabled = enabled) {
                    Text(stringResource(R.string.media_action_flaresolverr_create), maxLines = 1)
                }
            }

            snapshot.metrics.forEach { metric ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    Text(
                        text = localizedMetricLabel(metric.label),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        text = localizedMetricValue(metric),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (snapshot.flaresolverrSessions.isNotEmpty()) {
                val visibleSessions = if (sessionsExpanded) snapshot.flaresolverrSessions else snapshot.flaresolverrSessions.take(3)
                Text(
                    text = stringResource(R.string.media_session_ids_title),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                visibleSessions.forEach { sessionId ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = sessionId,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        OutlinedButton(
                            onClick = { onFlaresolverrDestroySession(sessionId) },
                            enabled = enabled
                        ) {
                            Text(stringResource(R.string.delete), maxLines = 1)
                        }
                    }
                }
                if (snapshot.flaresolverrSessions.size > 3) {
                    MediaExpandButton(
                        expanded = sessionsExpanded,
                        remaining = snapshot.flaresolverrSessions.size - visibleSessions.size,
                        onToggle = { sessionsExpanded = !sessionsExpanded }
                    )
                }
            }
    }
}

@Composable
private fun MediaAccentSection(
    accent: Color,
    modifier: Modifier = Modifier,
    shape: RoundedCornerShape = RoundedCornerShape(16.dp),
    borderAlpha: Float = 0.14f,
    contentPadding: PaddingValues = PaddingValues(12.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val brush = remember(accent, isDarkTheme) {
        Brush.linearGradient(
            colors = listOf(
                accent.copy(alpha = if (isDarkTheme) 0.13f else 0.075f),
                Color.Transparent
            ),
            start = Offset(0f, 0f),
            end = Offset(580f, 520f)
        )
    }
    Surface(
        shape = shape,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = BorderStroke(1.dp, accent.copy(alpha = borderAlpha)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(brush)
                .padding(contentPadding)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                content = content
            )
        }
    }
}

@Composable
private fun MediaServiceFooterCard(
    instance: ServiceInstance?,
    onRefresh: () -> Unit,
    onCopy: (String, String) -> Unit,
    onOpen: (String) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        if (instance == null) {
            Text(
                text = stringResource(R.string.service_instances_empty),
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                MediaFooterInfoRow(stringResource(R.string.media_url_label), instance.url)
                instance.fallbackUrl?.takeIf { it.isNotBlank() }?.let {
                    MediaFooterInfoRow(stringResource(R.string.media_fallback_url_label), it)
                }
                instance.apiKey?.takeIf { it.isNotBlank() }?.let {
                    MediaFooterInfoRow(stringResource(R.string.login_api_key_label), maskedSecret(it))
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { onOpen(instance.url) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.media_open_service), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = { onCopy(instance.label, instance.url) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.copy), maxLines = 1)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    instance.fallbackUrl?.takeIf { it.isNotBlank() }?.let {
                        OutlinedButton(
                            onClick = { onOpen(it) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.media_open_fallback), maxLines = 1)
                        }
                    }
                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier.weight(1f)
                    ) {
                        Text(stringResource(R.string.refresh), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaFooterInfoRow(title: String, value: String) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun MediaExpandButton(
    expanded: Boolean,
    remaining: Int,
    onToggle: () -> Unit
) {
    TextButton(
        onClick = onToggle,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = if (expanded) {
                    stringResource(R.string.media_show_less)
                } else {
                    stringResource(R.string.media_show_more_count, remaining)
                },
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun MediaDownloadRow(
    item: MediaArrDownloadItem,
    accent: Color
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Download,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                item.trailingLabel?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }

            item.progress?.let { progress ->
                LinearProgressIndicator(
                    progress = { progress.toFloat() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp),
                    color = accent,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                item.progressLabel?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item.supporting?.takeIf { it.isNotBlank() }?.let {
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
}

@Composable
private fun MediaHistoryRow(item: MediaArrHistoryItem) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            item.subtitle?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            item.supporting?.takeIf { it.isNotBlank() }?.let {
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

private fun maskedSecret(value: String): String {
    if (value.length <= 6) return "••••••"
    return buildString {
        append(value.take(4))
        append("•".repeat((value.length - 6).coerceAtLeast(6)))
        append(value.takeLast(2))
    }
}

@Composable
private fun MediaMetricGrid(
    metrics: List<MediaArrMetric>,
    accent: Color
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        metrics.chunked(2).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                row.forEach { metric ->
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = localizedMetricLabel(metric.label),
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = localizedMetricValue(metric),
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = accent
                            )
                            metric.supporting?.takeIf { it.isNotBlank() }?.let { supporting ->
                                Text(
                                    text = supporting,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
                if (row.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun MediaActionGrid(
    actions: List<MediaArrAction>,
    accent: Color,
    enabled: Boolean,
    onAction: (MediaArrAction) -> Unit
) {
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val actionContainer = if (isDarkTheme) {
        Color.Black.copy(alpha = 0.44f)
    } else {
        accent.copy(alpha = 0.08f)
    }
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            actions.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    row.forEach { action ->
                        Surface(
                            onClick = { onAction(action) },
                            enabled = enabled,
                            shape = RoundedCornerShape(999.dp),
                            color = actionContainer,
                            border = BorderStroke(1.dp, accent.copy(alpha = if (isDarkTheme) 0.12f else 0.20f)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = actionCompactLabel(action),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = accent,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                    if (row.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun QbittorrentOverviewSection(snapshot: MediaArrSnapshot) {
    val download = snapshot.metric("Download")?.let { localizedMetricValue(it) } ?: stringResource(R.string.no_data)
    val upload = snapshot.metric("Upload")?.let { localizedMetricValue(it) } ?: stringResource(R.string.no_data)
    val dhtNodes = snapshot.metric("DHT Nodes")?.let { localizedMetricValue(it) } ?: "0"
    val altSpeed = snapshot.metric("Alt Speed")?.let { localizedMetricValue(it) } ?: stringResource(R.string.no_data)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.media_connection_label),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
            ) {
                Text(
                    text = snapshot.status?.replaceFirstChar { it.uppercase() } ?: stringResource(R.string.no_data),
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QbOverviewCard(
                title = localizedMetricLabel("Download"),
                value = download,
                icon = "arrow.down.circle.fill",
                tint = Color(0xFF3BCB5A),
                compact = true,
                modifier = Modifier.weight(1f)
            )
            QbOverviewCard(
                title = localizedMetricLabel("Upload"),
                value = upload,
                icon = "arrow.up.circle.fill",
                tint = Color(0xFF59A5FF),
                compact = true,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            QbOverviewCard(
                title = localizedMetricLabel("DHT Nodes"),
                value = dhtNodes,
                icon = "point.3.connected.trianglepath.dotted",
                tint = Color(0xFF59A5FF),
                compact = true,
                modifier = Modifier.weight(1f)
            )
            QbOverviewCard(
                title = localizedMetricLabel("Alt Speed"),
                value = altSpeed,
                icon = "gauge.with.needle",
                tint = Color(0xFF3BCB5A),
                compact = true,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun QbOverviewCard(
    title: String,
    value: String,
    icon: String,
    tint: Color,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .background(tint.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (icon) {
                            "arrow.down.circle.fill" -> "↓"
                            "arrow.up.circle.fill" -> "↑"
                            "point.3.connected.trianglepath.dotted" -> "⟟"
                            else -> "◴"
                        },
                        color = tint,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = value,
                style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleLarge,
                color = tint,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun QbittorrentFilterBar(
    selectedFilter: QbTorrentFilter,
    onSelect: (QbTorrentFilter) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QbTorrentFilter.values().forEach { filter ->
                Surface(
                    onClick = { onSelect(filter) },
                    shape = RoundedCornerShape(999.dp),
                    color = if (selectedFilter == filter) {
                        MaterialTheme.colorScheme.secondaryContainer
                    } else {
                        Color.Transparent
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 7.dp, horizontal = 2.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = qbFilterLabel(filter),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun QbActionIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    onClick: () -> Unit,
    enabled: Boolean
) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(999.dp),
        color = tint.copy(alpha = 0.16f)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

private fun MediaArrSnapshot.metric(label: String): MediaArrMetric? {
    return metrics.firstOrNull { it.label.equals(label, ignoreCase = true) }
}

@Composable
private fun MediaContentSearchResultRow(
    result: MediaArrSearchResultItem,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top
        ) {
            MediaSearchArtwork(
                posterUrl = result.posterUrl,
                posterFallbackUrls = result.posterFallbackUrls,
                title = result.title,
                width = 56.dp,
                height = 84.dp,
                corner = 10.dp
            )

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(verticalAlignment = Alignment.Top) {
                    Text(
                        text = result.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    result.status?.takeIf { it.isNotBlank() }?.let { status ->
                        Spacer(modifier = Modifier.width(8.dp))
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = localizedSearchStatus(status),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                result.subtitle?.takeIf { it.isNotBlank() }?.let { subtitle ->
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                result.supporting?.takeIf { it.isNotBlank() }?.let { supporting ->
                    Text(
                        text = supporting,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                result.details.firstOrNull()?.let { detail ->
                    Text(
                        text = "${localizedMetricLabel(detail.label)}: ${detail.value}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun MediaSearchArtwork(
    posterUrl: String?,
    posterFallbackUrls: List<String>,
    title: String,
    width: androidx.compose.ui.unit.Dp,
    height: androidx.compose.ui.unit.Dp,
    corner: androidx.compose.ui.unit.Dp
) {
    val shape = RoundedCornerShape(corner)
    val candidates = remember(posterUrl, posterFallbackUrls) {
        buildList {
            if (!posterUrl.isNullOrBlank()) add(posterUrl)
            posterFallbackUrls.forEach { candidate ->
                if (candidate.isNotBlank() && !contains(candidate)) add(candidate)
            }
        }
    }
    var activeIndex by remember(candidates) { mutableStateOf(0) }
    val activePoster = candidates.getOrNull(activeIndex)
    if (!activePoster.isNullOrBlank()) {
        AsyncImage(
            model = activePoster,
            contentDescription = title,
            contentScale = ContentScale.Crop,
            onError = {
                if (activeIndex < candidates.lastIndex) {
                    activeIndex += 1
                }
            },
            modifier = Modifier
                .width(width)
                .height(height)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, shape)
        )
    } else {
        Surface(
            shape = shape,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            modifier = Modifier
                .width(width)
                .height(height)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MediaContentSearchResultDialog(
    result: MediaArrSearchResultItem,
    onDismiss: () -> Unit,
    onRequest: () -> Unit
) {
    val context = LocalContext.current
    val canRequest = result.requestTarget != null
    val hasDetailsUrl = !result.detailsUrl.isNullOrBlank()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = result.title,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    MediaSearchArtwork(
                        posterUrl = result.posterUrl,
                        posterFallbackUrls = result.posterFallbackUrls,
                        title = result.title,
                        width = 74.dp,
                        height = 108.dp,
                        corner = 10.dp
                    )

                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        result.subtitle?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        result.supporting?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        result.status?.takeIf { it.isNotBlank() }?.let {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            ) {
                                Text(
                                    text = localizedSearchStatus(it),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                if (result.details.isNotEmpty()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            result.details.take(6).forEach { detail ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = localizedMetricLabel(detail.label),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.widthIn(min = 78.dp, max = 92.dp)
                                    )
                                    Text(
                                        text = detail.value,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        modifier = Modifier.weight(1f),
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }

                if (hasDetailsUrl) {
                    OutlinedButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, result.detailsUrl!!.toUri()))
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.media_content_open_details))
                    }
                }
            }
        },
        confirmButton = {
            if (canRequest) {
                Button(onClick = onRequest) {
                    Text(stringResource(R.string.media_content_request_button))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun MediaRequestConfigurationDialog(
    title: String,
    configuration: com.homelab.app.data.repository.MediaArrRequestConfiguration,
    onDismiss: () -> Unit,
    onConfirm: (MediaArrRequestSelection) -> Unit
) {
    var selectedQuality by remember(configuration) { mutableStateOf(configuration.qualityProfiles.singleOrNull()) }
    var selectedRoot by remember(configuration) { mutableStateOf(configuration.rootFolders.singleOrNull()) }
    var selectedLanguage by remember(configuration) { mutableStateOf(configuration.languageProfiles.singleOrNull()) }
    var selectedMetadata by remember(configuration) { mutableStateOf(configuration.metadataProfiles.singleOrNull()) }

    val canConfirm = (configuration.qualityProfiles.isEmpty() || selectedQuality != null) &&
        (configuration.rootFolders.isEmpty() || selectedRoot != null) &&
        (configuration.languageProfiles.isEmpty() || selectedLanguage != null) &&
        (configuration.metadataProfiles.isEmpty() || selectedMetadata != null)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = stringResource(R.string.media_request_configuration_title, title),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                item {
                    Text(
                        text = stringResource(R.string.media_request_configuration_message),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (configuration.qualityProfiles.isNotEmpty()) {
                    item {
                        MediaRequestOptionSelector(
                            label = stringResource(R.string.media_request_quality_profile),
                            options = configuration.qualityProfiles,
                            selected = selectedQuality,
                            onSelected = { selectedQuality = it }
                        )
                    }
                }
                if (configuration.rootFolders.isNotEmpty()) {
                    item {
                        MediaRequestOptionSelector(
                            label = stringResource(R.string.media_request_root_folder),
                            options = configuration.rootFolders,
                            selected = selectedRoot,
                            onSelected = { selectedRoot = it }
                        )
                    }
                }
                if (configuration.languageProfiles.isNotEmpty()) {
                    item {
                        MediaRequestOptionSelector(
                            label = stringResource(R.string.media_request_language_profile),
                            options = configuration.languageProfiles,
                            selected = selectedLanguage,
                            onSelected = { selectedLanguage = it }
                        )
                    }
                }
                if (configuration.metadataProfiles.isNotEmpty()) {
                    item {
                        MediaRequestOptionSelector(
                            label = stringResource(R.string.media_request_metadata_profile),
                            options = configuration.metadataProfiles,
                            selected = selectedMetadata,
                            onSelected = { selectedMetadata = it }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirm(
                        MediaArrRequestSelection(
                            qualityProfile = selectedQuality,
                            rootFolder = selectedRoot,
                            languageProfile = selectedLanguage,
                            metadataProfile = selectedMetadata
                        )
                    )
                },
                enabled = canConfirm
            ) {
                Text(stringResource(R.string.media_content_request_button))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun MediaRequestOptionSelector(
    label: String,
    options: List<MediaArrRequestOption>,
    selected: MediaArrRequestOption?,
    onSelected: (MediaArrRequestOption) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        options.forEach { option ->
            val isSelected = selected?.key == option.key
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f)
                } else {
                    MaterialTheme.colorScheme.surfaceContainerLow
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.24f)
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelected(option) }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = option.label,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    if (isSelected) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun JellyseerrRequestRow(
    title: String,
    status: String,
    requestedBy: String?,
    requestedAt: String?,
    isPending: Boolean,
    actionsEnabled: Boolean,
    onApprove: () -> Unit,
    onDecline: () -> Unit
) {
    val statusColor = jellyseerrStatusColor(status)
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = statusColor.copy(alpha = 0.14f)
                ) {
                    Text(
                        text = status,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        color = statusColor,
                        fontWeight = FontWeight.Bold
                    )
                }
                requestedBy?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = it,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            requestedAt?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = compactIsoDate(it),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (isPending) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(
                        onClick = onApprove,
                        modifier = Modifier.weight(1f),
                        enabled = actionsEnabled
                    ) {
                        Text(stringResource(R.string.confirm), maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = onDecline,
                        modifier = Modifier.weight(1f),
                        enabled = actionsEnabled
                    ) {
                        Text(stringResource(R.string.delete), maxLines = 1)
                    }
                }
            }
        }
    }
}

@Composable
private fun jellyseerrStatusColor(status: String): androidx.compose.ui.graphics.Color {
    val normalized = status.lowercase(Locale.ROOT)
    return when {
        normalized.contains("pending") -> MaterialTheme.colorScheme.tertiary
        normalized.contains("approved") || normalized.contains("processing") -> MaterialTheme.colorScheme.secondary
        normalized.contains("available") -> MaterialTheme.colorScheme.primary
        normalized.contains("declined") -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

private fun compactIsoDate(value: String): String {
    val normalized = value.trim()
    if (normalized.length < 16) return normalized
    return normalized
        .replace("T", " ")
        .replace("Z", "")
        .take(16)
}

private enum class QbTorrentFilter {
    ALL,
    ACTIVE,
    DONE,
    PAUSED
}

private fun qbFilterAndSort(
    items: List<QbittorrentTorrentItem>,
    filter: QbTorrentFilter,
    query: String
): List<QbittorrentTorrentItem> {
    val normalizedQuery = query.trim().lowercase(Locale.ROOT)
    return items
        .asSequence()
        .filter { item ->
            when (filter) {
                QbTorrentFilter.ALL -> true
                QbTorrentFilter.ACTIVE -> item.isDownloading || item.isUploading
                QbTorrentFilter.DONE -> item.progress >= 0.999 && !item.isDownloading
                QbTorrentFilter.PAUSED -> item.isPaused
            }
        }
        .filter { item ->
            normalizedQuery.isBlank() ||
                item.name.lowercase(Locale.ROOT).contains(normalizedQuery) ||
                item.hash.lowercase(Locale.ROOT).contains(normalizedQuery)
        }
        .sortedWith(
            compareBy<QbittorrentTorrentItem> { it.stateRank }
                .thenByDescending { it.progress }
                .thenBy { it.name.lowercase(Locale.ROOT) }
        )
        .toList()
}

@Composable
private fun qbFilterLabel(filter: QbTorrentFilter): String {
    return when (filter) {
        QbTorrentFilter.ALL -> stringResource(R.string.media_qb_filter_all)
        QbTorrentFilter.ACTIVE -> stringResource(R.string.media_qb_filter_active)
        QbTorrentFilter.DONE -> stringResource(R.string.media_qb_filter_done)
        QbTorrentFilter.PAUSED -> stringResource(R.string.media_qb_filter_paused)
    }
}

@Composable
private fun QbTorrentRow(
    torrent: QbittorrentTorrentItem,
    actionsEnabled: Boolean,
    onAction: (MediaArrAction) -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val statusColor = when {
        torrent.isError -> MaterialTheme.colorScheme.error
        torrent.isPaused -> MaterialTheme.colorScheme.tertiary
        torrent.isDownloading -> MaterialTheme.colorScheme.primary
        torrent.isUploading -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.primary
    }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .background(statusColor, RoundedCornerShape(5.dp))
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = torrent.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Box {
                    IconButton(onClick = { menuExpanded = true }, enabled = actionsEnabled) {
                        Icon(Icons.Default.MoreVert, contentDescription = null)
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        DropdownMenuItem(
                            text = {
                                Text(
                                    if (torrent.isPaused) {
                                        stringResource(R.string.media_action_qb_resume_torrent)
                                    } else {
                                        stringResource(R.string.media_action_qb_pause_torrent)
                                    }
                                )
                            },
                            enabled = actionsEnabled,
                            leadingIcon = {
                                Icon(
                                    imageVector = if (torrent.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                                    contentDescription = null
                                )
                            },
                            onClick = {
                                menuExpanded = false
                                onAction(
                                    if (torrent.isPaused) {
                                        MediaArrAction.QBITTORRENT_RESUME_TORRENT
                                    } else {
                                        MediaArrAction.QBITTORRENT_PAUSE_TORRENT
                                    }
                                )
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.media_action_qb_recheck_torrent)) },
                            enabled = actionsEnabled,
                            onClick = {
                                menuExpanded = false
                                onAction(MediaArrAction.QBITTORRENT_RECHECK_TORRENT)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.media_action_qb_reannounce_torrent)) },
                            enabled = actionsEnabled,
                            onClick = {
                                menuExpanded = false
                                onAction(MediaArrAction.QBITTORRENT_REANNOUNCE_TORRENT)
                            }
                        )
                        HorizontalDivider()
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.media_action_qb_delete_torrent)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            enabled = actionsEnabled,
                            onClick = {
                                menuExpanded = false
                                onAction(MediaArrAction.QBITTORRENT_DELETE_TORRENT)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.media_action_qb_delete_torrent_with_data)) },
                            leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                            enabled = actionsEnabled,
                            onClick = {
                                menuExpanded = false
                                onAction(MediaArrAction.QBITTORRENT_DELETE_TORRENT_WITH_DATA)
                            }
                        )
                    }
                }
            }

            Text(
                text = qbStateLabel(torrent.state),
                style = MaterialTheme.typography.labelSmall,
                color = statusColor,
                fontWeight = FontWeight.Bold
            )

            LinearProgressIndicator(
                progress = { torrent.progress.coerceIn(0.0, 1.0).toFloat() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = statusColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "${bytesLabel(torrent.downloadedBytes.toDouble())} / ${bytesLabel(torrent.totalSizeBytes.toDouble())}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(torrent.progress.coerceIn(0.0, 1.0) * 100.0).roundToInt()}%",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = stringResource(
                        R.string.media_qb_speed_pair,
                        speedLabel(torrent.downloadSpeedBytes),
                        speedLabel(torrent.uploadSpeedBytes)
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val etaText = if (torrent.etaSeconds > 0) {
                    formatEta(torrent.etaSeconds)
                } else {
                    stringResource(R.string.no_data)
                }
                Text(
                    text = stringResource(R.string.media_qb_eta_value, etaText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val ratioText = torrent.ratio?.let { String.format(Locale.getDefault(), "%.2f", it) }
                    ?: stringResource(R.string.no_data)
                Text(
                    text = stringResource(R.string.media_qb_ratio_value, ratioText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                val peersText = if (torrent.seeds != null && torrent.leechers != null) {
                    "${torrent.seeds}/${torrent.leechers}"
                } else {
                    stringResource(R.string.no_data)
                }
                Text(
                    text = stringResource(R.string.media_qb_peers_value, peersText),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (!torrent.category.isNullOrBlank() || !torrent.tags.isNullOrBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    torrent.category?.takeIf { it.isNotBlank() }?.let {
                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ) {
                            Text(
                                text = it,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }
                    torrent.tags?.takeIf { it.isNotBlank() }?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.align(Alignment.CenterVertically)
                        )
                    }
                }
            }
        }
    }
}

private val QbittorrentTorrentItem.stateRank: Int
    get() = when {
        isError -> 0
        isDownloading -> 1
        isUploading -> 2
        isPaused -> 3
        else -> 4
    }

private val QbittorrentTorrentItem.isDownloading: Boolean
    get() {
        val normalized = state.lowercase(Locale.ROOT)
        return normalized.contains("downloading") || normalized in setOf("forceddl", "metadl", "stalleddl", "queueddl")
    }

private val QbittorrentTorrentItem.isUploading: Boolean
    get() {
        val normalized = state.lowercase(Locale.ROOT)
        return normalized.contains("upload") || normalized in setOf("forcedup", "stalledup", "queuedup")
    }

private val QbittorrentTorrentItem.isPaused: Boolean
    get() {
        val normalized = state.lowercase(Locale.ROOT)
        return normalized.startsWith("paused") || normalized.startsWith("stopped")
    }

private val QbittorrentTorrentItem.isError: Boolean
    get() {
        val normalized = state.lowercase(Locale.ROOT)
        return normalized.contains("error") || normalized.contains("missingfiles")
    }

@Composable
private fun qbStateLabel(state: String): String {
    val normalized = state.lowercase(Locale.ROOT)
    val downloadingStates = setOf("downloading", "forceddl", "metadl", "queueddl")
    val seedingStates = setOf("uploading", "forcedup", "queuedup")
    val stalledStates = setOf("stalleddl", "stalledup")
    return when {
        normalized.isBlank() -> stringResource(R.string.media_qb_state_unknown)
        downloadingStates.contains(normalized) -> stringResource(R.string.media_qb_state_downloading)
        seedingStates.contains(normalized) -> stringResource(R.string.media_qb_state_seeding)
        normalized.startsWith("paused") -> stringResource(R.string.media_qb_state_paused)
        normalized.startsWith("stopped") -> stringResource(R.string.media_qb_state_paused)
        stalledStates.contains(normalized) -> stringResource(R.string.media_qb_state_stalled)
        normalized.contains("checking") || normalized == "allocating" -> stringResource(R.string.home_verifying)
        normalized.contains("error") -> stringResource(R.string.media_qb_state_error)
        normalized.contains("missingfiles") -> stringResource(R.string.media_qb_state_error)
        else -> state
    }
}

@Composable
private fun localizedSearchStatus(status: String): String {
    return when (status.lowercase(Locale.ROOT)) {
        "in library" -> stringResource(R.string.media_search_status_in_library)
        "monitored" -> stringResource(R.string.media_search_status_monitored)
        "unmonitored" -> stringResource(R.string.media_search_status_unmonitored)
        "ended" -> stringResource(R.string.media_search_status_ended)
        "pending" -> stringResource(R.string.media_search_status_pending)
        "approved" -> stringResource(R.string.media_search_status_approved)
        "available" -> stringResource(R.string.media_search_status_available)
        "processing" -> stringResource(R.string.media_search_status_processing)
        else -> status
    }
}

private fun speedLabel(bytesPerSecond: Long): String {
    if (bytesPerSecond <= 0) return "0 B/s"
    val units = arrayOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = bytesPerSecond.toDouble()
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

private fun bytesLabel(bytes: Double): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes
    var unitIndex = 0
    while (value >= 1024 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex += 1
    }
    return String.format(Locale.getDefault(), "%.1f %s", value, units[unitIndex])
}

private fun formatEta(seconds: Long): String {
    if (seconds <= 0) return "--"
    val minutes = seconds / 60
    if (minutes < 60) return "${minutes}m"
    val hours = minutes / 60
    if (hours < 24) return "${hours}h ${minutes % 60}m"
    val days = hours / 24
    return "${days}d ${hours % 24}h"
}

@Composable
private fun mediaServiceDisplayName(type: ServiceType): String {
    return when (type) {
        ServiceType.RADARR -> stringResource(R.string.service_radarr)
        ServiceType.SONARR -> stringResource(R.string.service_sonarr)
        ServiceType.LIDARR -> stringResource(R.string.service_lidarr)
        ServiceType.QBITTORRENT -> stringResource(R.string.service_qbittorrent)
        ServiceType.JELLYSEERR -> stringResource(R.string.service_jellyseerr)
        ServiceType.PROWLARR -> stringResource(R.string.service_prowlarr)
        ServiceType.BAZARR -> stringResource(R.string.service_bazarr)
        ServiceType.GLUETUN -> stringResource(R.string.service_gluetun)
        ServiceType.FLARESOLVERR -> stringResource(R.string.service_flaresolverr)
        else -> type.displayName
    }
}

@Composable
private fun mediaServiceSubtitle(type: ServiceType): String? {
    return when (type) {
        ServiceType.JELLYSEERR -> stringResource(R.string.media_service_desc_jellyseerr)
        ServiceType.PROWLARR -> stringResource(R.string.media_service_desc_prowlarr)
        ServiceType.BAZARR -> stringResource(R.string.media_service_desc_bazarr)
        ServiceType.GLUETUN -> stringResource(R.string.media_service_desc_gluetun)
        ServiceType.FLARESOLVERR -> stringResource(R.string.media_service_desc_flaresolverr)
        else -> null
    }
}

@Composable
private fun actionLabel(action: MediaArrAction): String {
    return when (action) {
        MediaArrAction.QBITTORRENT_PAUSE_ALL -> stringResource(R.string.media_action_qb_pause_all)
        MediaArrAction.QBITTORRENT_RESUME_ALL -> stringResource(R.string.media_action_qb_resume_all)
        MediaArrAction.QBITTORRENT_TOGGLE_ALT_SPEED -> stringResource(R.string.media_action_qb_toggle_alt_speed)
        MediaArrAction.QBITTORRENT_FORCE_RECHECK -> stringResource(R.string.media_action_qb_recheck)
        MediaArrAction.QBITTORRENT_REANNOUNCE -> stringResource(R.string.media_action_qb_reannounce)
        MediaArrAction.QBITTORRENT_PAUSE_TORRENT -> stringResource(R.string.media_action_qb_pause_torrent)
        MediaArrAction.QBITTORRENT_RESUME_TORRENT -> stringResource(R.string.media_action_qb_resume_torrent)
        MediaArrAction.QBITTORRENT_RECHECK_TORRENT -> stringResource(R.string.media_action_qb_recheck_torrent)
        MediaArrAction.QBITTORRENT_REANNOUNCE_TORRENT -> stringResource(R.string.media_action_qb_reannounce_torrent)
        MediaArrAction.QBITTORRENT_DELETE_TORRENT -> stringResource(R.string.media_action_qb_delete_torrent)
        MediaArrAction.QBITTORRENT_DELETE_TORRENT_WITH_DATA -> stringResource(R.string.media_action_qb_delete_torrent_with_data)
        MediaArrAction.RADARR_SEARCH_MISSING -> stringResource(R.string.media_action_radarr_search_missing)
        MediaArrAction.RADARR_RSS_SYNC -> stringResource(R.string.media_action_radarr_rss)
        MediaArrAction.RADARR_REFRESH_INDEX -> stringResource(R.string.media_action_radarr_refresh)
        MediaArrAction.RADARR_RESCAN -> stringResource(R.string.media_action_radarr_rescan)
        MediaArrAction.RADARR_DOWNLOADED_SCAN -> stringResource(R.string.media_action_radarr_downloaded_scan)
        MediaArrAction.RADARR_HEALTH_CHECK -> stringResource(R.string.media_action_radarr_health_check)
        MediaArrAction.SONARR_SEARCH_MISSING -> stringResource(R.string.media_action_sonarr_search_missing)
        MediaArrAction.SONARR_RSS_SYNC -> stringResource(R.string.media_action_sonarr_rss)
        MediaArrAction.SONARR_REFRESH_INDEX -> stringResource(R.string.media_action_sonarr_refresh)
        MediaArrAction.SONARR_RESCAN -> stringResource(R.string.media_action_sonarr_rescan)
        MediaArrAction.SONARR_DOWNLOADED_SCAN -> stringResource(R.string.media_action_sonarr_downloaded_scan)
        MediaArrAction.SONARR_HEALTH_CHECK -> stringResource(R.string.media_action_sonarr_health_check)
        MediaArrAction.LIDARR_SEARCH_MISSING -> stringResource(R.string.media_action_lidarr_search_missing)
        MediaArrAction.LIDARR_RSS_SYNC -> stringResource(R.string.media_action_lidarr_rss)
        MediaArrAction.LIDARR_REFRESH_INDEX -> stringResource(R.string.media_action_lidarr_refresh)
        MediaArrAction.LIDARR_RESCAN -> stringResource(R.string.media_action_lidarr_rescan)
        MediaArrAction.LIDARR_DOWNLOADED_SCAN -> stringResource(R.string.media_action_lidarr_downloaded_scan)
        MediaArrAction.LIDARR_HEALTH_CHECK -> stringResource(R.string.media_action_lidarr_health_check)
        MediaArrAction.JELLYSEERR_APPROVE_PENDING -> stringResource(R.string.media_action_jellyseerr_approve_pending)
        MediaArrAction.JELLYSEERR_DECLINE_PENDING -> stringResource(R.string.media_action_jellyseerr_decline_pending)
        MediaArrAction.JELLYSEERR_APPROVE_REQUEST -> stringResource(R.string.media_action_jellyseerr_approve_request)
        MediaArrAction.JELLYSEERR_DECLINE_REQUEST -> stringResource(R.string.media_action_jellyseerr_decline_request)
        MediaArrAction.JELLYSEERR_RUN_RECENT_SCAN -> stringResource(R.string.media_action_jellyseerr_recent_scan)
        MediaArrAction.JELLYSEERR_RUN_FULL_SCAN -> stringResource(R.string.media_action_jellyseerr_full_scan)
        MediaArrAction.PROWLARR_TEST_INDEXERS -> stringResource(R.string.media_action_prowlarr_test)
        MediaArrAction.PROWLARR_SYNC_APPS -> stringResource(R.string.media_action_prowlarr_sync)
        MediaArrAction.PROWLARR_HEALTH_CHECK -> stringResource(R.string.media_action_prowlarr_health_check)
        MediaArrAction.GLUETUN_RESTART_VPN -> stringResource(R.string.media_action_gluetun_restart_vpn)
        MediaArrAction.FLARESOLVERR_CREATE_SESSION -> stringResource(R.string.media_action_flaresolverr_create)
        MediaArrAction.FLARESOLVERR_DESTROY_SESSION -> stringResource(R.string.media_action_flaresolverr_destroy)
        MediaArrAction.RADARR_ADD_CONTENT -> stringResource(R.string.media_action_radarr_add_content)
        MediaArrAction.SONARR_ADD_CONTENT -> stringResource(R.string.media_action_sonarr_add_content)
        MediaArrAction.LIDARR_ADD_CONTENT -> stringResource(R.string.media_action_lidarr_add_content)
        MediaArrAction.JELLYSEERR_REQUEST_CONTENT -> stringResource(R.string.media_action_jellyseerr_request_content)
    }
}

@Composable
private fun actionCompactLabel(action: MediaArrAction): String {
    return when (action) {
        MediaArrAction.RADARR_SEARCH_MISSING,
        MediaArrAction.SONARR_SEARCH_MISSING,
        MediaArrAction.LIDARR_SEARCH_MISSING -> stringResource(R.string.media_action_short_search_missing)
        MediaArrAction.RADARR_RSS_SYNC,
        MediaArrAction.SONARR_RSS_SYNC,
        MediaArrAction.LIDARR_RSS_SYNC -> stringResource(R.string.media_action_short_rss_sync)
        MediaArrAction.RADARR_REFRESH_INDEX,
        MediaArrAction.SONARR_REFRESH_INDEX,
        MediaArrAction.LIDARR_REFRESH_INDEX -> stringResource(R.string.media_action_short_refresh_index)
        MediaArrAction.RADARR_RESCAN,
        MediaArrAction.SONARR_RESCAN,
        MediaArrAction.LIDARR_RESCAN -> stringResource(R.string.media_action_short_rescan)
        MediaArrAction.RADARR_DOWNLOADED_SCAN,
        MediaArrAction.SONARR_DOWNLOADED_SCAN,
        MediaArrAction.LIDARR_DOWNLOADED_SCAN -> stringResource(R.string.media_action_short_downloaded_scan)
        MediaArrAction.RADARR_HEALTH_CHECK,
        MediaArrAction.SONARR_HEALTH_CHECK,
        MediaArrAction.LIDARR_HEALTH_CHECK,
        MediaArrAction.PROWLARR_HEALTH_CHECK -> stringResource(R.string.media_action_short_health_check)
        MediaArrAction.PROWLARR_TEST_INDEXERS -> stringResource(R.string.media_action_short_test_indexers)
        MediaArrAction.PROWLARR_SYNC_APPS -> stringResource(R.string.media_action_short_sync_apps)
        MediaArrAction.JELLYSEERR_RUN_RECENT_SCAN -> stringResource(R.string.media_action_short_recent_scan)
        MediaArrAction.JELLYSEERR_RUN_FULL_SCAN -> stringResource(R.string.media_action_short_full_scan)
        else -> actionLabel(action)
    }
}

@Composable
private fun actionResultLabel(result: MediaArrActionResult): String {
    val base = actionLabel(result.action)
    val detail = result.detail
    return if (detail.isNullOrBlank()) {
        stringResource(R.string.media_action_completed, base)
    } else {
        stringResource(R.string.media_action_completed_with_detail, base, detail)
    }
}

@Composable
private fun localizedMetricLabel(label: String): String {
    return when (label) {
        "Torrents" -> stringResource(R.string.media_metric_torrents)
        "Downloading" -> stringResource(R.string.media_metric_downloading)
        "Seeding" -> stringResource(R.string.media_metric_seeding)
        "Queued/Stalled" -> stringResource(R.string.media_metric_queued_stalled)
        "Download" -> stringResource(R.string.media_metric_download_speed)
        "Upload" -> stringResource(R.string.media_metric_upload_speed)
        "Alt Speed" -> stringResource(R.string.media_metric_alt_speed)
        "Free Space" -> stringResource(R.string.media_metric_free_space)
        "DHT Nodes" -> stringResource(R.string.media_metric_dht_nodes)
        "All-time Download" -> stringResource(R.string.media_metric_all_time_download)
        "Movies" -> stringResource(R.string.media_metric_movies)
        "Series" -> stringResource(R.string.media_metric_series)
        "Monitored" -> stringResource(R.string.media_metric_monitored)
        "Queue" -> stringResource(R.string.media_metric_queue)
        "Health Issues" -> stringResource(R.string.media_metric_health_issues)
        "Upcoming (14d)" -> stringResource(R.string.media_metric_upcoming)
        "Upcoming" -> stringResource(R.string.media_metric_upcoming)
        "Albums" -> stringResource(R.string.media_metric_albums)
        "Requests" -> stringResource(R.string.media_metric_requests)
        "Pending" -> stringResource(R.string.media_metric_pending)
        "Approved" -> stringResource(R.string.media_metric_approved)
        "Available" -> stringResource(R.string.media_metric_available)
        "Indexers" -> stringResource(R.string.media_metric_indexers)
        "Apps" -> stringResource(R.string.media_metric_apps)
        "Issues" -> stringResource(R.string.media_metric_unhealthy)
        "Applications" -> stringResource(R.string.media_metric_applications)
        "Health" -> stringResource(R.string.media_metric_health)
        "Unhealthy" -> stringResource(R.string.media_metric_unhealthy)
        "Wanted" -> stringResource(R.string.media_metric_wanted)
        "Missing" -> stringResource(R.string.media_metric_missing)
        "Providers" -> stringResource(R.string.media_metric_providers)
        "Tasks" -> stringResource(R.string.media_metric_tasks)
        "Version" -> stringResource(R.string.media_version_label)
        "Branch" -> stringResource(R.string.media_branch_label)
        "Package" -> stringResource(R.string.media_package_label)
        "Commit" -> stringResource(R.string.media_commit_label)
        "DB" -> stringResource(R.string.media_db_label)
        "Status" -> stringResource(R.string.media_metric_status)
        "IP" -> stringResource(R.string.media_ip_label)
        "Server" -> stringResource(R.string.media_server_label)
        "Public IP" -> stringResource(R.string.media_metric_public_ip)
        "Forwarded Port" -> stringResource(R.string.media_metric_forwarded_port)
        "Provider" -> stringResource(R.string.media_metric_provider)
        "Country" -> stringResource(R.string.media_metric_country)
        "Sessions" -> stringResource(R.string.media_metric_sessions)
        "Message" -> stringResource(R.string.media_metric_message)
        "User Agent" -> stringResource(R.string.media_user_agent_label)
        else -> label
    }
}

@Composable
private fun localizedMetricValue(metric: MediaArrMetric): String {
    if (metric.label == "Alt Speed") {
        return when (metric.value.uppercase()) {
            "ON" -> "ON"
            "OFF" -> "OFF"
            else -> metric.value
        }
    }
    return when (metric.value.uppercase()) {
        "N/A" -> stringResource(R.string.no_data)
        "UNKNOWN" -> stringResource(R.string.no_data)
        else -> metric.value
    }
}

@Composable
private fun localizedLibraryTitle(title: String?): String {
    return when (title) {
        "Latest Additions" -> stringResource(R.string.media_library_latest_additions)
        "Latest Albums" -> stringResource(R.string.media_library_latest_albums)
        null -> stringResource(R.string.media_library_latest_additions)
        else -> title
    }
}

@Composable
private fun localizedHighlight(value: String): String {
    return when {
        value.startsWith("Connection: ") -> stringResource(R.string.media_highlight_connection, value.removePrefix("Connection: "))
        value.startsWith("DHT nodes: ") -> stringResource(R.string.media_highlight_dht_nodes, value.removePrefix("DHT nodes: "))
        value.startsWith("All-time download: ") -> stringResource(
            R.string.media_highlight_all_time_download,
            value.removePrefix("All-time download: ")
        )
        value.startsWith("Server: ") -> stringResource(R.string.media_highlight_server, value.removePrefix("Server: "))
        value.startsWith("Country: ") -> stringResource(R.string.media_highlight_country, value.removePrefix("Country: "))
        else -> value
    }
}
