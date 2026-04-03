package com.homelab.app.ui.home

import android.content.ActivityNotFoundException
import android.content.Intent
import java.text.NumberFormat
import java.util.Locale
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.core.net.toUri
import androidx.lifecycle.repeatOnLifecycle
import com.homelab.app.R
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.theme.backgroundColor
import com.homelab.app.ui.theme.fallbackIcon
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import coil3.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.isActive

@OptIn(ExperimentalMaterial3Api::class, kotlinx.coroutines.FlowPreview::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToService: (ServiceType, String) -> Unit,
    onNavigateToLogin: (ServiceType, String?) -> Unit
) {
    val reachability by viewModel.reachability.collectAsStateWithLifecycle()
    val pinging by viewModel.pinging.collectAsStateWithLifecycle()
    val connectedCount by viewModel.connectedCount.collectAsStateWithLifecycle()
    val isTailscaleConnected by viewModel.isTailscaleConnected.collectAsStateWithLifecycle()
    val hiddenServices by viewModel.hiddenServices.collectAsStateWithLifecycle()
    val serviceOrder by viewModel.serviceOrder.collectAsStateWithLifecycle()
    val instancesByType by viewModel.instancesByType.collectAsStateWithLifecycle()
    val preferredInstanceIds by viewModel.preferredInstanceIdByType.collectAsStateWithLifecycle()
    val instanceSummaries by viewModel.instanceSummaries.collectAsStateWithLifecycle()
    val summaryLoading by viewModel.summaryLoading.collectAsStateWithLifecycle()

    var showReorderDialog by rememberSaveable { mutableStateOf(false) }

    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) {
        lifecycleOwner.lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            while (isActive) {
                viewModel.checkAllReachability()
                viewModel.fetchSummaryData()
                delay(300_000L)
            }
        }
    }


    LaunchedEffect(Unit) {
        snapshotFlow {
            // Construct refresh key INSIDE snapshotFlow so Compose tracks all dependencies
            serviceOrder.map { type ->
                val id = preferredInstanceIds[type]
                "${type.name}:$id"
            }.joinToString("|")
        }
        .distinctUntilChanged()
        .debounce(1500L)
        .collect {
            viewModel.fetchSummaryData()
        }
    }

    val visibleTypes = serviceOrder.filter { it.isHomeService && !hiddenServices.contains(it.name) }
    val hasConfiguredPangolin = instancesByType[ServiceType.PANGOLIN].orEmpty().isNotEmpty()
    val hasUnreachableInstance = visibleTypes
        .flatMap { instancesByType[it].orEmpty() }
        .any { instance ->
            val r = reachability[instance.id]
            val s = instanceSummaries[instance.id]
            val isPinging = pinging[instance.id] == true
            val isSumLoading = s == null && summaryLoading
            val resolvedReachable = when {
                s != null -> true
                r == false && (isSumLoading || isPinging) -> null
                r == true -> true
                r == false -> false
                else -> null
            }
            resolvedReachable == false
        }
    val showVpnShortcut = (isTailscaleConnected || hasUnreachableInstance) &&
        !(hasConfiguredPangolin && isTailscaleConnected)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
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
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.primaryContainer,
                            shape = CircleShape
                        ) {
                            Text(
                                text = "$connectedCount",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        FilledTonalIconButton(onClick = { showReorderDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.SwapVert,
                                contentDescription = stringResource(R.string.home_reorder_services)
                            )
                        }
                    }
                }
            }

            if (showVpnShortcut) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    TailscaleCard(isConnected = isTailscaleConnected)
                }
            }

            visibleTypes.forEach { type ->
                val instances = instancesByType[type].orEmpty()

                if (instances.isEmpty()) {
                    item {
                        ConnectInstanceCard(
                            type = type,
    
                            onClick = { onNavigateToLogin(type, null) }
                        )
                    }
                } else {
                    items(instances, key = { it.id }) { instance ->
                        InstanceCard(
                            type = type,
    
                            instance = instance,
                            isPreferred = instance.id == preferredInstanceIds[type],
                            isReachable = reachability[instance.id],
                            isPinging = pinging[instance.id] == true,
                            summary = instanceSummaries[instance.id],
                            isSummaryLoading = instanceSummaries[instance.id] == null && summaryLoading,
                            onOpen = { onNavigateToService(type, instance.id) },
                            onRefresh = { viewModel.checkReachability(instance.id, force = true) }
                        )
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Text(
                    text = stringResource(R.string.home_summary_count).format(connectedCount),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    if (showReorderDialog) {
        ServiceOrderDialog(
            serviceOrder = serviceOrder.filter { it.isHomeService },
            hiddenServices = hiddenServices,
            onMoveUp = { type -> viewModel.moveService(type, -1) },
            onMoveDown = { type -> viewModel.moveService(type, 1) },
            onToggleVisibility = { type -> viewModel.toggleServiceVisibility(type) },
            onDismiss = { showReorderDialog = false }
        )
    }
}



@Composable
private fun InstanceCard(
    type: ServiceType,
    instance: ServiceInstance,
    isPreferred: Boolean,
    isReachable: Boolean?,
    isPinging: Boolean,
    summary: HomeViewModel.InstanceSummary?,
    isSummaryLoading: Boolean,
    onOpen: () -> Unit,
    onRefresh: () -> Unit
) {
    val resolvedReachable = when {
        summary != null -> true
        isReachable == false && (isSummaryLoading || isPinging) -> null
        isReachable == true -> true
        isReachable == false -> false
        else -> null
    }
    val statusAccent = when (resolvedReachable) {
        true -> StatusGreen
        false -> Color(0xFFEF5350)
        null -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val statusBackground = when (resolvedReachable) {
        true -> StatusGreen.copy(alpha = 0.15f)
        false -> Color(0xFFEF5350).copy(alpha = 0.15f)
        null -> MaterialTheme.colorScheme.surfaceVariant
    }
    val statusLabel = when (resolvedReachable) {
        true -> stringResource(R.string.home_status_online)
        false -> stringResource(R.string.home_status_offline)
        null -> stringResource(R.string.home_verifying)
    }
    val cardShape = RoundedCornerShape(18.dp)

    // Resolve label key to localized string
    val summaryLabel = summary?.let { s ->
        when (s.label) {
            "containers" -> stringResource(R.string.portainer_containers)
            "total_queries" -> stringResource(R.string.pihole_total_queries)
            "adguard_total_queries" -> stringResource(R.string.adguard_total_queries)
            "systems_online" -> stringResource(R.string.beszel_systems_online)
            "repos" -> stringResource(R.string.gitea_repos)
            "linux_update_systems_up_to_date" -> stringResource(R.string.linux_update_widget_label)
            "technitium_blocked_queries" -> stringResource(R.string.technitium_blocked_queries)
            "dockhand_running_containers" -> stringResource(R.string.dockhand_running_containers)
            "dockhand_containers" -> stringResource(R.string.dockhand_containers)
            "proxy_hosts" -> stringResource(R.string.npm_proxy_hosts)
            "pangolin_sites_clients" -> stringResource(R.string.pangolin_sites_clients)
            "checks" -> stringResource(R.string.healthchecks_checks)
            "jellystat_watch_time" -> stringResource(R.string.jellystat_watch_time)
            "hosts" -> stringResource(R.string.patchmon_hosts)
            "plex_total_items" -> stringResource(R.string.plex_total_items)
            else -> s.label
        }.lowercase()
    }

    Surface(
        shape = cardShape,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Box {
            Column(
                modifier = Modifier
                    .clickable(onClick = onOpen)
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top
                ) {
                    ServiceIcon(
                        type = type,
                        size = 52.dp,
                        iconSize = 32.dp,
                        cornerRadius = 13.dp
                    )

                    Spacer(modifier = Modifier.weight(1f))

                    if (resolvedReachable == true && summary != null) {
                        Column(
                            horizontalAlignment = Alignment.End,
                            modifier = Modifier.widthIn(max = 108.dp),
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.Bottom,
                                horizontalArrangement = Arrangement.spacedBy(2.dp)
                            ) {
                                Text(
                                    text = summary.value,
                                    style = MaterialTheme.typography.titleSmall.copy(
                                        fontWeight = FontWeight.Bold
                                    ),
                                    color = type.primaryColor,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    softWrap = false,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                if (summary.subValue != null) {
                                    Text(
                                        text = summary.subValue,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        softWrap = false,
                                        modifier = Modifier.padding(bottom = 1.dp)
                                    )
                                }
                            }
                            Text(
                                text = summaryLabel ?: "",
                                style = MaterialTheme.typography.labelSmall.copy(lineHeight = 12.sp),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                textAlign = TextAlign.End,
                                softWrap = false
                            )
                        }
                    } else if (resolvedReachable == true && isSummaryLoading) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            modifier = Modifier.size(width = 52.dp, height = 14.dp)
                        ) {}
                    } else if (resolvedReachable == false) {
                        Surface(
                            shape = CircleShape,
                            color = type.primaryColor.copy(alpha = 0.1f),
                            modifier = Modifier.size(36.dp)
                        ) {
                            IconButton(onClick = onRefresh) {
                                val rotation by animateFloatAsState(
                                    targetValue = if (isPinging) 360f else 0f,
                                    animationSpec = if (isPinging) infiniteRepeatable(tween(1000, easing = LinearEasing)) else tween(300),
                                    label = "refresh_rotation"
                                )
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = stringResource(R.string.refresh),
                                    tint = type.primaryColor,
                                    modifier = Modifier.graphicsLayer(rotationZ = rotation)
                                )
                            }
                        }
                    } else if (resolvedReachable == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = type.primaryColor
                        )
                    }
                }

                Text(
                    text = instance.label.ifBlank { type.displayName },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = statusBackground
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .background(statusAccent, CircleShape)
                            )
                            Text(
                                text = statusLabel,
                                style = MaterialTheme.typography.labelSmall,
                                color = statusAccent,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }


                }
            }
        }
    }
}

private const val TAILSCALE_ICON_URL = "https://cdn.jsdelivr.net/gh/selfhst/icons/png/tailscale.png"

@Composable
private fun ConnectInstanceCard(
    type: ServiceType,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(
            modifier = Modifier
                .clickable(onClick = onClick)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ServiceIcon(
                    type = type,
                    size = 52.dp,
                    cornerRadius = 13.dp,
                    modifier = Modifier.size(52.dp)
                )

                Spacer(modifier = Modifier.weight(1f))
            }

            Column {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.home_connect_service, type.displayName),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
fun TailscaleCard(isConnected: Boolean) {
    val context = LocalContext.current

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceContainer,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    SubcomposeAsyncImage(
                        model = TAILSCALE_ICON_URL,
                        contentDescription = stringResource(R.string.tailscale_open),
                        modifier = Modifier.size(26.dp),
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
                                Icons.Default.Security,
                                contentDescription = stringResource(R.string.tailscale_open),
                                tint = if (isConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .clickable {
                        val launchIntent =
                            context.packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
                                ?: context.packageManager.getLaunchIntentForPackage("com.tailscale.ipn.beta")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        } else {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, "tailscale://app".toUri()))
                            } catch (_: ActivityNotFoundException) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, "market://details?id=com.tailscale.ipn".toUri()))
                                } catch (_: ActivityNotFoundException) {
                                    context.startActivity(
                                        Intent(
                                            Intent.ACTION_VIEW,
                                            "https://play.google.com/store/apps/details?id=com.tailscale.ipn".toUri()
                                        )
                                    )
                                }
                            }
                        }
                    }
            ) {
                Text(
                    text = stringResource(R.string.tailscale_open),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.tailscale_tap_to_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            val statusColor = if (isConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(statusColor))
                    Text(
                        text = stringResource(if (isConnected) R.string.tailscale_connected else R.string.tailscale_not_connected),
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
fun DashboardSummary(viewModel: HomeViewModel) {
    val portainer by viewModel.portainerSummary.collectAsStateWithLifecycle()
    val pihole by viewModel.piholeSummary.collectAsStateWithLifecycle()
    val adguard by viewModel.adguardSummary.collectAsStateWithLifecycle()
    val beszel by viewModel.beszelSummary.collectAsStateWithLifecycle()
    val gitea by viewModel.giteaSummary.collectAsStateWithLifecycle()
    val linuxUpdate by viewModel.linuxUpdateSummary.collectAsStateWithLifecycle()
    val healthchecks by viewModel.healthchecksSummary.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()

    val hasAny = connectionStatus.values.any { it }

    if (hasAny) {
        Spacer(modifier = Modifier.height(32.dp))
        Text(stringResource(R.string.home_summary_title), style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(MaterialTheme.colorScheme.surfaceContainerLow).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            if (connectionStatus[ServiceType.PORTAINER] == true) {
                SummaryRow(title = stringResource(R.string.portainer_containers), value = portainer?.running?.toString() ?: "—", subValue = portainer?.let { "/ ${it.total}" }, icon = Icons.Default.Widgets, color = ServiceType.PORTAINER.primaryColor)
            }
            if (connectionStatus[ServiceType.PIHOLE] == true) {
                val q = pihole?.totalQueries
                val formattedStr = if(q != null) NumberFormat.getInstance(Locale.ITALY).format(q) else "—"
                SummaryRow(title = stringResource(R.string.pihole_total_queries), value = formattedStr, subValue = null, icon = Icons.Default.Security, color = ServiceType.PIHOLE.primaryColor)
            }
            if (connectionStatus[ServiceType.ADGUARD_HOME] == true) {
                val q = adguard?.totalQueries
                val formattedStr = if (q != null) NumberFormat.getInstance(Locale.ITALY).format(q) else "—"
                SummaryRow(title = stringResource(R.string.adguard_total_queries), value = formattedStr, subValue = null, icon = Icons.Default.Security, color = ServiceType.ADGUARD_HOME.primaryColor)
            }
            if (connectionStatus[ServiceType.BESZEL] == true) {
                SummaryRow(title = stringResource(R.string.beszel_systems_online), value = beszel?.online?.toString() ?: "—", subValue = beszel?.let { "/ ${it.total}" }, icon = Icons.Default.Storage, color = ServiceType.BESZEL.primaryColor)
            }
            if (connectionStatus[ServiceType.GITEA] == true) {
                SummaryRow(title = stringResource(R.string.gitea_repos), value = gitea?.totalRepos?.toString() ?: "—", subValue = null, icon = Icons.Default.Source, color = ServiceType.GITEA.primaryColor)
            }
            if (connectionStatus[ServiceType.LINUX_UPDATE] == true) {
                SummaryRow(
                    title = stringResource(R.string.linux_update_systems_up_to_date),
                    value = linuxUpdate?.upToDate?.toString() ?: "—",
                    subValue = linuxUpdate?.let { "/ ${it.total}" },
                    icon = Icons.Default.CheckCircle,
                    color = ServiceType.LINUX_UPDATE.primaryColor
                )
            }
            if (connectionStatus[ServiceType.HEALTHCHECKS] == true) {
                SummaryRow(title = stringResource(R.string.healthchecks_checks), value = healthchecks?.up?.toString() ?: "—", subValue = healthchecks?.let { "/ ${it.total}" }, icon = Icons.Default.CheckCircle, color = ServiceType.HEALTHCHECKS.primaryColor)
            }
        }
    }
}

@Composable
fun SummaryRow(title: String, value: String, subValue: String?, icon: androidx.compose.ui.graphics.vector.ImageVector, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(shape = RoundedCornerShape(12.dp), color = color.copy(alpha = 0.15f), modifier = Modifier.size(42.dp)) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.padding(10.dp))
        }
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(value, style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold), color = MaterialTheme.colorScheme.onSurface)
                if (subValue != null) {
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(subValue, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(bottom = 2.dp))
                }
            }
            Text(title, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun ServiceCard(
    type: ServiceType,
    isConnected: Boolean,
    isReachable: Boolean?,
    isPinging: Boolean,
    onClick: () -> Unit,
    onRefresh: () -> Unit
) {
    // M3 Expressive Spring animation state
    var isPressed by remember { mutableStateOf(false) }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "card_bounce"
    )

    // Base colors based on type (simulating iOS theme)
    val cardBgColor = MaterialTheme.colorScheme.surfaceContainerLow
    val iconColor = type.primaryColor
    val iconBgColor = iconColor.copy(alpha = 0.15f)

    val serviceIcon = type.fallbackIcon

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(onClick) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        val success = tryAwaitRelease()
                        isPressed = false
                        if (success) {
                            haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                            onClick()
                        }
                    }
                )
            }
        ,
        color = cardBgColor,
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Surface(
                    color = iconBgColor,
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.size(50.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = serviceIcon,
                            contentDescription = type.displayName,
                            tint = iconColor,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }

                if (isPinging) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    IconButton(onClick = onRefresh, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = type.displayName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(6.dp))

            val statusText = when {
                isConnected && isReachable == false -> stringResource(R.string.error_server_unreachable)
                isConnected -> stringResource(R.string.home_status_online)
                else -> stringResource(R.string.home_status_offline)
            }

            val statusColor = when {
                isConnected && isReachable == false -> MaterialTheme.colorScheme.error
                isConnected -> StatusGreen
                else -> MaterialTheme.colorScheme.onSurfaceVariant
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(statusColor, CircleShape)
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.labelSmall,
                    color = statusColor,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.weight(1f))

            Icon(
                imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                contentDescription = type.displayName,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.End)
            )
        }
    }
}

@Composable
private fun ServiceOrderDialog(
    serviceOrder: List<ServiceType>,
    hiddenServices: Set<String>,
    onMoveUp: (ServiceType) -> Unit,
    onMoveDown: (ServiceType) -> Unit,
    onToggleVisibility: (ServiceType) -> Unit,
    onDismiss: () -> Unit
) {
    androidx.compose.material3.AlertDialog(
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
                                text = type.displayName,
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
            androidx.compose.material3.TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close))
            }
        }
    )
}

@Composable
private fun DashboardSummary(
    serviceOrder: List<ServiceType>,
    portainer: HomeViewModel.PortainerSummary?,
    pihole: HomeViewModel.PiholeSummary?,
    adguard: HomeViewModel.AdGuardSummary?,
    beszel: HomeViewModel.BeszelSummary?,
    gitea: HomeViewModel.GiteaSummary?,
    npm: HomeViewModel.NpmSummary?
) {
    // Don't show if no services are defined in order
    if (serviceOrder.isEmpty()) return

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Spacer(modifier = Modifier.height(20.dp))
        Text(
            text = stringResource(R.string.home_summary_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(4.dp))

        // We only show summaries for services that are actually in the service order
        val availableSummaries = serviceOrder.map { type ->
            val summary = when (type) {
                ServiceType.PORTAINER -> portainer
                ServiceType.PIHOLE -> pihole
                ServiceType.ADGUARD_HOME -> adguard
                ServiceType.BESZEL -> beszel
                ServiceType.GITEA -> gitea
                ServiceType.NGINX_PROXY_MANAGER -> npm
                else -> null
            }
            type to summary
        }

        availableSummaries.chunked(2).forEach { rowItems ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                rowItems.forEach { (type, summary) ->
                    Box(modifier = Modifier.weight(1f)) {
                        if (summary != null) {
                            when (type) {
                                ServiceType.PORTAINER -> {
                                    val s = summary as HomeViewModel.PortainerSummary
                                    DashboardSummaryCard(
                                        type = type,
                                        value = "${s.running}/${s.total}",
                                        label = stringResource(R.string.portainer_containers)
                                    )
                                }
                                ServiceType.PIHOLE -> {
                                    val s = summary as HomeViewModel.PiholeSummary
                                    DashboardSummaryCard(
                                        type = type,
                                        value = s.totalQueries.toString(),
                                        label = stringResource(R.string.pihole_total_queries)
                                    )
                                }
                                ServiceType.ADGUARD_HOME -> {
                                    val s = summary as HomeViewModel.AdGuardSummary
                                    DashboardSummaryCard(
                                        type = type,
                                        value = s.totalQueries.toString(),
                                        label = stringResource(R.string.adguard_total_queries)
                                    )
                                }
                                ServiceType.BESZEL -> {
                                    val s = summary as HomeViewModel.BeszelSummary
                                    DashboardSummaryCard(
                                        type = type,
                                        value = "${s.online}/${s.total}",
                                        label = stringResource(R.string.beszel_systems_online)
                                    )
                                }
                                ServiceType.GITEA -> {
                                    val s = summary as HomeViewModel.GiteaSummary
                                    DashboardSummaryCard(
                                        type = type,
                                        value = s.totalRepos.toString(),
                                        label = pluralStringResource(R.plurals.home_summary_gitea, s.totalRepos, s.totalRepos)
                                    )
                                }
                                ServiceType.NGINX_PROXY_MANAGER -> {
                                    val s = summary as HomeViewModel.NpmSummary
                                    DashboardSummaryCard(
                                        type = type,
                                        value = "${s.proxyHosts}/${s.total}",
                                        label = stringResource(R.string.npm_proxy_hosts)
                                    )
                                }
                                else -> {}
                            }
                        } else {
                            // Placeholder while loading
                            DashboardSummaryCard(
                                type = type,
                                value = "...",
                                label = ""
                            )
                        }
                    }
                }
                if (rowItems.size == 1) {
                    Spacer(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun DashboardSummaryCard(
    type: ServiceType,
    value: String,
    label: String
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            ServiceIcon(
                type = type,
                size = 56.dp,
                iconSize = 36.dp,
                cornerRadius = 14.dp
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = value,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun SummaryRow(
    title: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
