package com.homelab.app.ui.pangolin

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.homelab.app.R
import com.homelab.app.data.remote.dto.pangolin.PangolinClient
import com.homelab.app.data.remote.dto.pangolin.PangolinDomain
import com.homelab.app.data.remote.dto.pangolin.PangolinResource
import com.homelab.app.data.remote.dto.pangolin.PangolinSite
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResource
import com.homelab.app.data.remote.dto.pangolin.PangolinTarget
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import kotlin.math.roundToInt

private fun pangolinPageBackground(accent: Color): Brush = Brush.verticalGradient(
    listOf(
        Color(0xFF0A0E12),
        Color(0xFF0F1318),
        accent.copy(alpha = 0.03f),
        Color(0xFF0A0D11)
    )
)

private fun pangolinCardBorder(accent: Color): BorderStroke = BorderStroke(1.dp, accent.copy(alpha = 0.12f))

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PangolinDashboardScreen(
    onNavigateBack: () -> Unit,
    viewModel: PangolinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val accent = ServiceType.PANGOLIN.primaryColor
    val strings = rememberPangolinStrings()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(strings.serviceName) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = strings.back)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.refresh() }) {
                        Icon(Icons.Default.Refresh, contentDescription = strings.refresh, tint = accent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pangolinPageBackground(accent))
        ) {
            when (val state = uiState) {
            PangolinUiState.Loading -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = accent)
                }
            }
            is PangolinUiState.Error -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = state.message,
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.error
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        TextButton(onClick = { viewModel.refresh() }) {
                            Text(strings.retry)
                        }
                    }
                }
            }
            is PangolinUiState.Success -> {
                PangolinContent(
                    strings = strings,
                    padding = padding,
                    data = state.data,
                    instances = instances,
                    onSelectInstance = viewModel::setPreferredInstance,
                    onSelectOrg = viewModel::selectOrg
                )
            }
        }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PangolinContent(
    strings: PangolinStrings,
    padding: PaddingValues,
    data: PangolinDashboardData,
    instances: List<ServiceInstance>,
    onSelectInstance: (String) -> Unit,
    onSelectOrg: (String) -> Unit
) {
    val accent = ServiceType.PANGOLIN.primaryColor
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(padding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Surface(
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    accent.copy(alpha = 0.18f),
                                    accent.copy(alpha = 0.04f),
                                    Color.Transparent
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                            ServiceIcon(
                                type = ServiceType.PANGOLIN,
                                size = 64.dp,
                                iconSize = 36.dp,
                                cornerRadius = 18.dp
                            )
                            Column {
                                Text(
                                    text = data.orgs.firstOrNull { it.orgId == data.selectedOrgId }?.name ?: strings.serviceName,
                                    style = MaterialTheme.typography.headlineSmall,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = strings.overviewSubtitle,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OverviewPill(Icons.Default.Lan, strings.sites, data.sites.size.toString(), accent)
                            OverviewPill(Icons.Default.VpnLock, strings.privateResources, data.siteResources.size.toString(), accent)
                            OverviewPill(Icons.Default.Public, strings.publicResources, data.resources.size.toString(), accent)
                            OverviewPill(Icons.Default.Security, strings.clients, data.clients.size.toString(), accent)
                            OverviewPill(Icons.Default.Cloud, strings.domains, data.domains.size.toString(), accent)
                            OverviewPill(Icons.Default.Dns, strings.traffic, formatTraffic(data.sites, data.clients), accent)
                        }
                    }
                }
            }
        }

        if (instances.size > 1) {
            item {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    instances.forEach { instance ->
                        AssistChip(
                            onClick = { onSelectInstance(instance.id) },
                            label = { Text(instance.label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            colors = AssistChipDefaults.assistChipColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainer
                            )
                        )
                    }
                }
            }
        }

        if (data.orgs.size > 1) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(strings.organizations, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        data.orgs.forEach { org ->
                            val selected = org.orgId == data.selectedOrgId
                            AssistChip(
                                onClick = { onSelectOrg(org.orgId) },
                                label = { Text(org.name) },
                                leadingIcon = if (selected) {
                                    { Icon(Icons.Default.Dns, contentDescription = null, modifier = Modifier.size(18.dp)) }
                                } else null,
                                colors = AssistChipDefaults.assistChipColors(
                                    containerColor = if (selected) accent.copy(alpha = 0.16f) else MaterialTheme.colorScheme.surfaceContainer,
                                    labelColor = if (selected) accent else MaterialTheme.colorScheme.onSurface
                                )
                            )
                        }
                    }
                }
            }
        }

        item { PangolinSection(strings.sites, strings.onlineCount(data.sites.count { it.online })) }
        items(data.sites.take(8), key = { "site-${it.siteId}" }) { site ->
            PangolinRowCard(
                title = site.name,
                subtitle = listOfNotNull(site.address, site.subnet, site.type).joinToString(" • "),
                supporting = buildString {
                    append(if (site.online) strings.online else strings.offline)
                    site.newtVersion?.takeIf { it.isNotBlank() }?.let { append(" • ${strings.newtVersion(it)}") }
                    site.exitNodeName?.takeIf { it.isNotBlank() }?.let { append(" • ${strings.exitNode(it)}") }
                },
                accent = if (site.online) accent else MaterialTheme.colorScheme.error,
                detailChips = listOfNotNull(
                    formatTraffic(site.megabytesIn, site.megabytesOut),
                    site.newtUpdateAvailable?.takeIf { it }?.let { strings.newtUpdate },
                    site.exitNodeEndpoint?.takeIf { it.isNotBlank() }?.let { strings.endpoint(it) }
                )
            )
        }

        item { PangolinSection(strings.privateResources, strings.enabledCount(data.siteResources.count { it.enabled })) }
        items(data.siteResources.take(8), key = { "sr-${it.siteResourceId}" }) { resource ->
            PangolinRowCard(
                title = resource.name,
                subtitle = listOfNotNull(resource.siteName, resource.destination).joinToString(" • "),
                supporting = listOfNotNull(resource.mode, resource.protocol?.uppercase(), resource.proxyPort?.let { strings.proxyPort(it) }).joinToString(" • "),
                accent = if (resource.enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                detailChips = listOfNotNull(
                    resource.destinationPort?.let { strings.destinationPort(it) },
                    resource.alias?.takeIf { it.isNotBlank() }?.let { strings.alias(it) },
                    resource.tcpPortRangeString?.takeIf { it.isNotBlank() }?.let { strings.tcpPorts(it) },
                    resource.udpPortRangeString?.takeIf { it.isNotBlank() }?.let { strings.udpPorts(it) },
                    resource.authDaemonPort?.let { strings.authDaemonPort(it) },
                    resource.authDaemonMode?.takeIf { it.isNotBlank() }?.let { it.uppercase() },
                    resource.disableIcmp?.takeIf { it }?.let { strings.icmpOff }
                )
            )
        }

        item { PangolinSection(strings.publicResources, strings.enabledCount(data.resources.count { it.enabled })) }
        items(data.resources.take(8), key = { "res-${it.resourceId}" }) { resource ->
            val targets = data.targetsByResourceId[resource.resourceId].orEmpty().ifEmpty { resource.targets }
            PangolinRowCard(
                title = resource.name,
                subtitle = listOfNotNull(resource.fullDomain, resource.protocol?.uppercase()).joinToString(" • "),
                supporting = listOf(
                    if (resource.enabled) strings.enabled else strings.disabled,
                    strings.targetsCount(targets.size),
                    strings.healthSummary(targets)
                ).joinToString(" • "),
                accent = when {
                    targets.any { (it.healthStatus ?: "").contains("unhealthy", ignoreCase = true) } -> MaterialTheme.colorScheme.error
                    resource.enabled -> accent
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                detailChips = listOfNotNull(
                    resource.ssl.takeIf { it }?.let { "TLS" },
                    resource.sso.takeIf { it }?.let { "SSO" },
                    resource.whitelist.takeIf { it }?.let { strings.whitelist },
                    resource.http.takeIf { it }?.let { "HTTP" },
                    resource.proxyPort?.let { strings.proxyPort(it) }
                ),
                extraContent = {
                    if (targets.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            targets.take(3).forEach { target ->
                                PangolinTargetRow(target = target, accent = targetAccent(target, accent), strings = strings)
                            }
                        }
                    }
                }
            )
        }

        item { PangolinSection(strings.clients, strings.onlineCount(data.clients.count { it.online })) }
        items(data.clients.take(8), key = { "client-${it.clientId}" }) { client ->
            PangolinRowCard(
                title = client.name,
                subtitle = listOfNotNull(client.subnet, client.type).joinToString(" • "),
                supporting = buildClientSupporting(strings, client),
                accent = if (client.online && !client.blocked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                detailChips = listOfNotNull(
                    client.approvalState?.takeIf { it.isNotBlank() }?.let(strings::approvalState),
                    client.olmVersion?.takeIf { it.isNotBlank() }?.let { strings.olmVersion(it) },
                    client.olmUpdateAvailable?.takeIf { it }?.let { strings.agentUpdate },
                    formatTraffic(client.megabytesIn, client.megabytesOut),
                    client.sites.takeIf { it.isNotEmpty() }?.let { strings.linkedSites(it.size) }
                )
            )
        }

        item { PangolinSection(strings.domains, strings.verifiedCount(data.domains.count { it.verified })) }
        items(data.domains.take(8), key = { "domain-${it.domainId}" }) { domain ->
            PangolinRowCard(
                title = domain.baseDomain,
                subtitle = listOfNotNull(domain.type, domain.certResolver).joinToString(" • "),
                supporting = buildString {
                    append(if (domain.verified) strings.verified else strings.pending)
                    if (domain.failed) {
                        append(" • ")
                        append(domain.errorMessage ?: strings.error)
                    }
                },
                accent = if (domain.verified && !domain.failed) accent else MaterialTheme.colorScheme.error,
                detailChips = listOfNotNull(
                    domain.configManaged?.let { if (it) strings.managed else strings.manual },
                    domain.preferWildcardCert?.takeIf { it }?.let { strings.wildcard },
                    domain.tries?.takeIf { it > 0 }?.let { strings.tries(it) }
                )
            )
        }
    }
}

private fun buildClientSupporting(strings: PangolinStrings, client: PangolinClient): String {
    val status = when {
        client.blocked -> strings.blocked
        client.archived -> strings.archived
        client.online -> strings.online
        else -> strings.offline
    }
    return buildString {
        append(status)
        if (client.sites.isNotEmpty()) {
            append(" • ")
            append(client.sites.joinToString { it.siteName ?: it.siteNiceId ?: strings.site })
        }
    }
}

private fun formatTraffic(sites: List<PangolinSite>, clients: List<PangolinClient>): String {
    val totalMegabytes = sites.sumOf { (it.megabytesIn ?: 0.0) + (it.megabytesOut ?: 0.0) } +
        clients.sumOf { (it.megabytesIn ?: 0.0) + (it.megabytesOut ?: 0.0) }
    return formatTrafficValue(totalMegabytes)
}

private fun formatTraffic(inMegabytes: Double?, outMegabytes: Double?): String? {
    val totalMegabytes = (inMegabytes ?: 0.0) + (outMegabytes ?: 0.0)
    if (totalMegabytes <= 0.0) return null
    return formatTrafficValue(totalMegabytes)
}

private fun formatTrafficValue(totalMegabytes: Double): String {
    val gigabytes = totalMegabytes / 1024.0
    return if (gigabytes >= 1.0) {
        "${((gigabytes * 10.0).roundToInt() / 10.0)} GB"
    } else {
        "${totalMegabytes.roundToInt()} MB"
    }
}

private fun targetAccent(target: PangolinTarget, defaultAccent: Color): Color = when {
    (target.hcHealth ?: target.healthStatus ?: "").contains("unhealthy", ignoreCase = true) -> Color(0xFFDC2626)
    (target.hcHealth ?: target.healthStatus ?: "").contains("healthy", ignoreCase = true) -> defaultAccent
    target.enabled -> defaultAccent.copy(alpha = 0.75f)
    else -> Color.Gray
}

@Composable
private fun PangolinSection(title: String, trailing: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun OverviewPill(icon: ImageVector, title: String, value: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.72f),
        border = pangolinCardBorder(accent)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PangolinRowCard(
    title: String,
    subtitle: String,
    supporting: String,
    accent: Color,
    detailChips: List<String> = emptyList(),
    extraContent: (@Composable () -> Unit)? = null
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        border = pangolinCardBorder(accent)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(CircleShape)
                        .background(accent)
                )
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (subtitle.isNotBlank()) {
                        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    if (supporting.isNotBlank()) {
                        Text(supporting, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            if (detailChips.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    detailChips.forEach { chip ->
                        PangolinDetailChip(text = chip, accent = accent)
                    }
                }
            }
            extraContent?.invoke()
        }
    }
}

@Composable
private fun PangolinDetailChip(text: String, accent: Color) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = accent.copy(alpha = 0.10f)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun PangolinTargetRow(target: PangolinTarget, accent: Color, strings: PangolinStrings) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "${target.ip}:${target.port}",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            val supporting = listOfNotNull(
                target.method?.uppercase(),
                target.path?.takeIf { it.isNotBlank() },
                target.pathMatchType?.takeIf { it.isNotBlank() }?.uppercase(),
                target.rewritePath?.takeIf { it.isNotBlank() }?.let { strings.rewrite(it) }
            ).joinToString(" • ")
            if (supporting.isNotBlank()) {
                Text(
                    text = supporting,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                listOfNotNull(
                    if (target.enabled) strings.enabled else strings.disabled,
                    target.hcEnabled?.let { if (it) strings.healthCheck else null },
                    target.hcHealth?.takeIf { it.isNotBlank() }?.let(strings::healthStatus),
                    target.hcPath?.takeIf { it.isNotBlank() }?.let { strings.healthPath(it) },
                    target.priority?.let { strings.priority(it) }
                ).forEach { chip ->
                    PangolinDetailChip(text = chip, accent = accent)
                }
            }
        }
    }
}

@Composable
private fun rememberPangolinStrings(): PangolinStrings {
    val context = LocalContext.current
    return remember(context) { PangolinStrings(context) }
}

private class PangolinStrings(private val context: Context) {
    val serviceName: String = context.getString(R.string.service_pangolin)
    val back: String = context.getString(R.string.back)
    val refresh: String = context.getString(R.string.refresh)
    val retry: String = context.getString(R.string.retry)
    val error: String = context.getString(R.string.error)
    val overviewSubtitle: String = context.getString(R.string.pangolin_overview_subtitle)
    val organizations: String = context.getString(R.string.pangolin_organizations)
    val sites: String = context.getString(R.string.pangolin_sites)
    val privateResources: String = context.getString(R.string.pangolin_private_resources)
    val publicResources: String = context.getString(R.string.pangolin_public_resources)
    val clients: String = context.getString(R.string.pangolin_clients)
    val domains: String = context.getString(R.string.pangolin_domains)
    val traffic: String = context.getString(R.string.pangolin_traffic)
    val enabled: String = context.getString(R.string.pangolin_enabled)
    val disabled: String = context.getString(R.string.pangolin_disabled)
    val online: String = context.getString(R.string.pangolin_online)
    val offline: String = context.getString(R.string.pangolin_offline)
    val blocked: String = context.getString(R.string.pangolin_blocked)
    val archived: String = context.getString(R.string.pangolin_archived)
    val pending: String = context.getString(R.string.pangolin_pending)
    val verified: String = context.getString(R.string.pangolin_verified)
    val managed: String = context.getString(R.string.pangolin_managed)
    val manual: String = context.getString(R.string.pangolin_manual)
    val wildcard: String = context.getString(R.string.pangolin_wildcard)
    val whitelist: String = context.getString(R.string.pangolin_whitelist)
    val healthCheck: String = context.getString(R.string.pangolin_health_check)
    val agentUpdate: String = context.getString(R.string.pangolin_agent_update)
    val newtUpdate: String = context.getString(R.string.pangolin_newt_update)
    val icmpOff: String = context.getString(R.string.pangolin_icmp_off)
    val site: String = context.getString(R.string.pangolin_site)

    fun onlineCount(count: Int): String = context.getString(R.string.pangolin_online_count, count)
    fun enabledCount(count: Int): String = context.getString(R.string.pangolin_enabled_count, count)
    fun verifiedCount(count: Int): String = context.getString(R.string.pangolin_verified_count, count)
    fun linkedSites(count: Int): String = context.getString(R.string.pangolin_linked_sites, count)
    fun tries(count: Int): String = context.getString(R.string.pangolin_tries, count)
    fun targetsCount(count: Int): String = context.getString(R.string.pangolin_targets_count, count)
    fun newtVersion(value: String): String = context.getString(R.string.pangolin_newt_version, value)
    fun exitNode(value: String): String = context.getString(R.string.pangolin_exit_node, value)
    fun endpoint(value: String): String = context.getString(R.string.pangolin_endpoint, value)
    fun proxyPort(value: Int): String = context.getString(R.string.pangolin_proxy_port, value)
    fun destinationPort(value: Int): String = context.getString(R.string.pangolin_destination_port, value)
    fun alias(value: String): String = context.getString(R.string.pangolin_alias, value)
    fun tcpPorts(value: String): String = context.getString(R.string.pangolin_tcp_ports, value)
    fun udpPorts(value: String): String = context.getString(R.string.pangolin_udp_ports, value)
    fun authDaemonPort(value: Int): String = context.getString(R.string.pangolin_authd_port, value)
    fun olmVersion(value: String): String = context.getString(R.string.pangolin_olm_version, value)
    fun rewrite(value: String): String = context.getString(R.string.pangolin_rewrite, value)
    fun healthPath(value: String): String = context.getString(R.string.pangolin_health_path, value)
    fun priority(value: Int): String = context.getString(R.string.pangolin_priority, value)

    fun approvalState(value: String): String = when (value.trim().lowercase()) {
        "approved" -> context.getString(R.string.pangolin_approved)
        "pending" -> pending
        "blocked" -> blocked
        "archived" -> archived
        else -> value.replaceFirstChar { it.titlecase() }
    }

    fun healthStatus(value: String): String = when {
        value.contains("unhealthy", ignoreCase = true) -> context.getString(R.string.pangolin_unhealthy)
        value.contains("healthy", ignoreCase = true) -> context.getString(R.string.pangolin_healthy)
        value.contains("pending", ignoreCase = true) -> pending
        else -> value.replaceFirstChar { it.titlecase() }
    }

    fun healthSummary(targets: List<PangolinTarget>): String {
        if (targets.isEmpty()) return ""
        return targets
            .groupBy { healthStatus(it.hcHealth ?: it.healthStatus ?: context.getString(R.string.pangolin_unknown)) }
            .entries
            .joinToString(" ") { "${it.key}:${it.value.size}" }
    }
}
