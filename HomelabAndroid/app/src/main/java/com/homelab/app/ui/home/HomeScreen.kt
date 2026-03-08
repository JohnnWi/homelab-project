package com.homelab.app.ui.home

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Source
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.platform.LocalContext
import com.homelab.app.R
import com.homelab.app.ui.theme.StatusGreen
import com.homelab.app.ui.theme.isThemeDark
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    viewModel: HomeViewModel = hiltViewModel(),
    onNavigateToService: (ServiceType) -> Unit,
    onNavigateToLogin: (ServiceType) -> Unit
) {
    val reachability by viewModel.reachability.collectAsStateWithLifecycle()
    val pinging by viewModel.pinging.collectAsStateWithLifecycle()
    val connectedCount by viewModel.connectedCount.collectAsStateWithLifecycle()
    val isTailscaleConnected by viewModel.isTailscaleConnected.collectAsStateWithLifecycle()
    val hiddenServices by viewModel.hiddenServices.collectAsStateWithLifecycle()

    // Effetto per il refresh all'apertura
    LaunchedEffect(Unit) {
        viewModel.checkAllReachability()
        viewModel.fetchSummaryData()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets.systemBars
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState())
        ) {
            
            // Header
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.app_name),
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = (-1).sp
                        ),
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Connected Badge
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = CircleShape
                ) {
                    Text(
                        text = "$connectedCount/4",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Determine if ANY active, un-hidden service is currently down
            val connectionStatus by viewModel.connectionStatus.collectAsStateWithLifecycle()
            
            val hasUnreachableService = ServiceType.entries.filter { 
                it != ServiceType.UNKNOWN && !hiddenServices.contains(it.name) 
            }.any { type ->
                connectionStatus[type] == true && reachability[type] == false
            }

            Spacer(modifier = Modifier.height(16.dp))
            
            if (hasUnreachableService || isTailscaleConnected) {
                TailscaleCard(isConnected = isTailscaleConnected)
                Spacer(modifier = Modifier.height(16.dp))
            }

            // Grid Component (M3 Expressive Bouncy Cards)
            val services = ServiceType.entries.filter { it != ServiceType.UNKNOWN && !hiddenServices.contains(it.name) }
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                val rows = services.chunked(2)
                rows.forEach { rowServices ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        rowServices.forEach { type ->
                            val isConnected = connectionStatus[type] ?: false
                            val isReachable = reachability[type]
                            val isPinging = pinging[type] ?: false

                            Box(modifier = Modifier.weight(1f)) {
                                ServiceCard(
                                    type = type,
                                    isConnected = isConnected,
                                    isReachable = isReachable,
                                    isPinging = isPinging,
                                    onClick = {
                                        if (isConnected) onNavigateToService(type) else onNavigateToLogin(type)
                                    },
                                    onRefresh = { viewModel.checkReachability(type) }
                                )
                            }
                        }
                        if (rowServices.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            DashboardSummary(viewModel)

            Spacer(modifier = Modifier.height(40.dp))
            Text(stringResource(R.string.home_summary_count).format(connectedCount), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
fun TailscaleCard(isConnected: Boolean) {
    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    var isPressed by remember { mutableStateOf(false) }

    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium
        ),
        label = "ts_card_bounce"
    )

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isPressed = false

                        val launchIntent =
                            context.packageManager.getLaunchIntentForPackage("com.tailscale.ipn")
                                ?: context.packageManager.getLaunchIntentForPackage("com.tailscale.ipn.beta")
                        if (launchIntent != null) {
                            context.startActivity(launchIntent.apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) })
                        } else {
                            try {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("tailscale://app")))
                            } catch (e: ActivityNotFoundException) {
                                try {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.tailscale.ipn")))
                                } catch (e: ActivityNotFoundException) {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.tailscale.ipn")))
                                }
                            }
                        }
                    }
                )
            },
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color.Black,
                modifier = Modifier.size(44.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Security,
                        contentDescription = "Tailscale",
                        tint = Color.White,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.tailscale_open),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.tailscale_tap_to_open),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            // Status badge
            val statusColor = if (isConnected) StatusGreen else MaterialTheme.colorScheme.onSurfaceVariant
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = statusColor.copy(alpha = 0.1f)
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
    val beszel by viewModel.beszelSummary.collectAsStateWithLifecycle()
    val gitea by viewModel.giteaSummary.collectAsStateWithLifecycle()
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
            if (connectionStatus[ServiceType.BESZEL] == true) {
                SummaryRow(title = stringResource(R.string.beszel_systems_online), value = beszel?.online?.toString() ?: "—", subValue = beszel?.let { "/ ${it.total}" }, icon = Icons.Default.Storage, color = ServiceType.BESZEL.primaryColor)
            }
            if (connectionStatus[ServiceType.GITEA] == true) {
                SummaryRow(title = stringResource(R.string.gitea_repos), value = gitea?.totalRepos?.toString() ?: "—", subValue = null, icon = Icons.Default.Source, color = ServiceType.GITEA.primaryColor)
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

    val serviceIcon = when(type) {
        ServiceType.PORTAINER -> Icons.Default.Widgets
        ServiceType.PIHOLE -> Icons.Default.Security
        ServiceType.BESZEL -> Icons.Default.Storage
        ServiceType.GITEA -> Icons.Default.Source
        else -> Icons.Default.Widgets
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp)
            .scale(scale)
            .clip(RoundedCornerShape(24.dp))
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        isPressed = true
                        haptic.performHapticFeedback(androidx.compose.ui.hapticfeedback.HapticFeedbackType.TextHandleMove)
                        tryAwaitRelease()
                        isPressed = false
                        onClick()
                    }
                )
            },
        color = cardBgColor,
        shape = RoundedCornerShape(24.dp),
        shadowElevation = 0.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // Icon
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = iconBgColor,
                    modifier = Modifier.size(42.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            serviceIcon,
                            contentDescription = type.name,
                            tint = iconColor,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                // Refresh Button or Status icon
                if (isConnected && isReachable == false) {
                    val rotation by animateFloatAsState(
                        targetValue = if (isPinging) 360f else 0f,
                        animationSpec = spring(stiffness = Spring.StiffnessLow),
                        label = "spin_refresh"
                    )
                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp).rotate(rotation)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = stringResource(R.string.refresh),
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                } else if (isConnected && isReachable == null) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = type.name.lowercase().replaceFirstChar { it.uppercase() },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )

                // Status Badge
                val badgeBg = if (!isConnected) MaterialTheme.colorScheme.surfaceVariant
                              else if (isReachable == false) MaterialTheme.colorScheme.errorContainer
                              else if (isReachable == null) MaterialTheme.colorScheme.surfaceVariant
                              else MaterialTheme.colorScheme.primaryContainer

                val badgeFg = if (!isConnected) MaterialTheme.colorScheme.onSurfaceVariant
                              else if (isReachable == false) MaterialTheme.colorScheme.onErrorContainer
                              else if (isReachable == null) MaterialTheme.colorScheme.onSurfaceVariant
                              else MaterialTheme.colorScheme.onPrimaryContainer

                val badgeText = if (!isConnected) stringResource(R.string.home_status_unconfigured)
                                else if (isReachable == false) "• " + stringResource(R.string.home_status_offline)
                                else if (isReachable == null) stringResource(R.string.home_verifying)
                                else "• " + stringResource(R.string.home_status_online)

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg
                ) {
                    Text(
                        text = badgeText,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = badgeFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}
