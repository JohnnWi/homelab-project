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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VpnLock
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalContext
import com.homelab.app.R
import com.homelab.app.data.remote.dto.pangolin.PangolinDomain
import com.homelab.app.data.remote.dto.pangolin.PangolinResource
import com.homelab.app.data.remote.dto.pangolin.PangolinSite
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResource
import com.homelab.app.data.remote.dto.pangolin.PangolinTarget
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

private fun pangolinPageBackground(isDarkTheme: Boolean, accent: Color): Brush = if (isDarkTheme) {
    Brush.verticalGradient(
        listOf(
            Color(0xFF0A0E12),
            Color(0xFF0F1318),
            accent.copy(alpha = 0.03f),
            Color(0xFF0A0D11)
        )
    )
} else {
    Brush.verticalGradient(
        listOf(
            Color(0xFFFFFBF7),
            Color(0xFFFFF4EA),
            accent.copy(alpha = 0.045f),
            Color(0xFFFFFAF5)
        )
    )
}

private fun pangolinTopBarColor(isDarkTheme: Boolean, accent: Color): Color = if (isDarkTheme) {
    Color(0xFF0A0E12).copy(alpha = 0.98f)
} else {
    Color(0xFFFFFBF7).copy(alpha = 0.98f).compositeOver(accent.copy(alpha = 0.03f))
}

private fun pangolinCardBorder(accent: Color): BorderStroke = BorderStroke(1.dp, accent.copy(alpha = 0.12f))

private data class PangolinPublicEditorState(
    val resource: PangolinResource,
    val targets: List<PangolinTarget>
)

private data class PangolinDropdownOption<T>(
    val value: T,
    val label: String
)

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
    val isDarkTheme = MaterialTheme.colorScheme.background.luminance() < 0.45f
    val snackbarHostState = remember { SnackbarHostState() }
    var editingPublicResource by remember { mutableStateOf<PangolinPublicEditorState?>(null) }
    var editingPrivateResource by remember { mutableStateOf<PangolinSiteResource?>(null) }
    var creatingPublicResource by remember { mutableStateOf(false) }
    var creatingPrivateResource by remember { mutableStateOf(false) }
    var togglingResourceKey by remember { mutableStateOf<String?>(null) }
    val topBarColor = pangolinTopBarColor(isDarkTheme, accent)

    LaunchedEffect(viewModel.instanceId) {
        while (currentCoroutineContext().isActive) {
            delay(20_000L)
            viewModel.refresh(showLoading = false)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
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
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = topBarColor,
                    scrolledContainerColor = topBarColor
                )
            )
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(pangolinPageBackground(isDarkTheme, accent))
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
                    onSelectOrg = viewModel::selectOrg,
                    onEditPublicResource = { resource, targets ->
                        editingPublicResource = PangolinPublicEditorState(resource, targets)
                    },
                    onEditPrivateResource = { resource ->
                        editingPrivateResource = resource
                    },
                    onCreatePublicResource = {
                        creatingPublicResource = true
                    },
                    onCreatePrivateResource = {
                        creatingPrivateResource = true
                    },
                    onTogglePublicResource = { resource ->
                        val actionKey = "public-${resource.resourceId}"
                        togglingResourceKey = actionKey
                        val result = viewModel.togglePublicResource(resource)
                        togglingResourceKey = null
                        result.fold(
                            onSuccess = { enabled ->
                                snackbarHostState.showSnackbar("${resource.name} • ${if (enabled) strings.enabled else strings.disabled}")
                            },
                            onFailure = { error ->
                                snackbarHostState.showSnackbar(error.message ?: strings.error)
                            }
                        )
                    },
                    togglingResourceKey = togglingResourceKey
                )
            }
        }
        }
    }

    editingPublicResource?.let { editor ->
        PangolinPublicResourceEditorSheet(
            strings = strings,
            resource = editor.resource,
            targets = editor.targets,
            sites = (uiState as? PangolinUiState.Success)?.data?.sites.orEmpty(),
            onDismiss = { editingPublicResource = null },
            onSave = { input ->
                val result = viewModel.savePublicResource(input)
                result.fold(
                    onSuccess = {
                        editingPublicResource = null
                        snackbarHostState.showSnackbar(strings.saved)
                    },
                    onFailure = { error ->
                        snackbarHostState.showSnackbar(error.message ?: strings.error)
                    }
                )
            }
        )
    }

    editingPrivateResource?.let { resource ->
        PangolinPrivateResourceEditorSheet(
            strings = strings,
            resource = resource,
            sites = (uiState as? PangolinUiState.Success)?.data?.sites.orEmpty(),
            onDismiss = { editingPrivateResource = null },
            onSave = { input ->
                val result = viewModel.savePrivateResource(input)
                result.fold(
                    onSuccess = {
                        editingPrivateResource = null
                        snackbarHostState.showSnackbar(strings.saved)
                    },
                    onFailure = { error ->
                        snackbarHostState.showSnackbar(error.message ?: strings.error)
                    }
                )
            }
        )
    }

    if (creatingPublicResource) {
        PangolinPublicResourceCreateSheet(
            strings = strings,
            sites = (uiState as? PangolinUiState.Success)?.data?.sites.orEmpty(),
            domains = (uiState as? PangolinUiState.Success)?.data?.domains.orEmpty(),
            onDismiss = { creatingPublicResource = false },
            onSave = { input ->
                val result = viewModel.createPublicResource(input)
                result.fold(
                    onSuccess = {
                        creatingPublicResource = false
                        snackbarHostState.showSnackbar(strings.saved)
                    },
                    onFailure = { error ->
                        snackbarHostState.showSnackbar(error.message ?: strings.error)
                    }
                )
            }
        )
    }

    if (creatingPrivateResource) {
        PangolinPrivateResourceCreateSheet(
            strings = strings,
            sites = (uiState as? PangolinUiState.Success)?.data?.sites.orEmpty(),
            onDismiss = { creatingPrivateResource = false },
            onSave = { input ->
                val result = viewModel.createPrivateResource(input)
                result.fold(
                    onSuccess = {
                        creatingPrivateResource = false
                        snackbarHostState.showSnackbar(strings.saved)
                    },
                    onFailure = { error ->
                        snackbarHostState.showSnackbar(error.message ?: strings.error)
                    }
                )
            }
        )
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
    onSelectOrg: (String) -> Unit,
    onEditPublicResource: (PangolinResource, List<PangolinTarget>) -> Unit,
    onEditPrivateResource: (PangolinSiteResource) -> Unit,
    onCreatePublicResource: () -> Unit,
    onCreatePrivateResource: () -> Unit,
    onTogglePublicResource: suspend (PangolinResource) -> Unit,
    togglingResourceKey: String? = null
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
                            OverviewPill(Icons.Default.Security, strings.clients, data.clientEntries.size.toString(), accent)
                            OverviewPill(Icons.Default.Cloud, strings.domains, data.domains.size.toString(), accent)
                            OverviewPill(Icons.Default.Dns, strings.traffic, formatTraffic(data.sites, data.clientEntries), accent)
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

        item {
            PangolinSection(
                title = strings.privateResources,
                trailing = strings.enabledCount(data.siteResources.count { it.enabled }),
                actionContentDescription = strings.createPrivateResource,
                onAction = onCreatePrivateResource.takeIf { data.sites.isNotEmpty() },
                actionTint = accent
            )
        }
        items(data.siteResources.take(8), key = { "sr-${it.siteResourceId}" }) { resource ->
            PangolinRowCard(
                title = resource.name,
                subtitle = listOfNotNull(resource.siteName, resource.destination).joinToString(" • "),
                supporting = listOfNotNull(resource.mode, resource.protocol?.uppercase(), resource.proxyPort?.let { strings.proxyPort(it) }).joinToString(" • "),
                accent = if (resource.enabled) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                onEdit = { onEditPrivateResource(resource) },
                editContentDescription = strings.edit,
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

        item {
            PangolinSection(
                title = strings.publicResources,
                trailing = strings.enabledCount(data.resources.count { it.enabled }),
                actionContentDescription = strings.createPublicResource,
                onAction = onCreatePublicResource.takeIf { data.sites.isNotEmpty() },
                actionTint = accent
            )
        }
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
                onEdit = { onEditPublicResource(resource, targets) },
                onToggle = { onTogglePublicResource(resource) },
                toggleContentDescription = if (resource.enabled) strings.disableAction else strings.enableAction,
                editContentDescription = strings.edit,
                toggleTint = if (resource.enabled) MaterialTheme.colorScheme.error else accent,
                toggleLoading = togglingResourceKey == "public-${resource.resourceId}",
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

        item { PangolinSection(strings.clients, strings.onlineCount(data.clientEntries.count { it.online })) }
        items(data.clientEntries.take(8), key = { it.id }) { client ->
            PangolinRowCard(
                title = client.name,
                subtitle = client.subtitle,
                supporting = buildClientSupporting(strings, client),
                accent = if (client.online && !client.blocked) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                detailChips = listOfNotNull(
                    client.source.let(strings::clientSource),
                    client.agent?.takeIf { it.isNotBlank() }?.let(strings::agent),
                    client.approvalState?.takeIf { it.isNotBlank() }?.let(strings::approvalState),
                    client.version?.takeIf { it.isNotBlank() }?.let { strings.olmVersion(it) },
                    client.updateAvailable.takeIf { it }?.let { strings.agentUpdate },
                    formatTraffic(client.trafficIn, client.trafficOut),
                    client.linkedSites.takeIf { it.isNotEmpty() }?.let { strings.linkedSites(it.size) }
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

private fun buildClientSupporting(strings: PangolinStrings, client: PangolinClientEntry): String {
    val status = when {
        client.blocked -> strings.blocked
        client.archived -> strings.archived
        client.online -> strings.online
        else -> strings.offline
    }
    return buildString {
        append(status)
        if (client.linkedSites.isNotEmpty()) {
            append(" • ")
            append(client.linkedSites.joinToString())
        }
    }
}

private fun formatTraffic(sites: List<PangolinSite>, clients: List<PangolinClientEntry>): String {
    val totalMegabytes = sites.sumOf { (it.megabytesIn ?: 0.0) + (it.megabytesOut ?: 0.0) } +
        clients.sumOf { (it.trafficIn ?: 0.0) + (it.trafficOut ?: 0.0) }
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
private fun PangolinSection(
    title: String,
    trailing: String,
    actionContentDescription: String? = null,
    onAction: (() -> Unit)? = null,
    actionTint: Color = Color.Unspecified
) {
    val resolvedActionTint = if (actionTint == Color.Unspecified) MaterialTheme.colorScheme.primary else actionTint
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(trailing, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            onAction?.let {
                IconButton(onClick = it) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = actionContentDescription,
                        tint = resolvedActionTint
                    )
                }
            }
        }
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
    onEdit: (() -> Unit)? = null,
    onToggle: (suspend () -> Unit)? = null,
    toggleContentDescription: String? = null,
    editContentDescription: String? = null,
    toggleTint: Color = accent,
    toggleLoading: Boolean = false,
    extraContent: (@Composable () -> Unit)? = null
) {
    val scope = rememberCoroutineScope()
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    onToggle?.let {
                        IconButton(
                            onClick = { scope.launch { it() } },
                            enabled = !toggleLoading
                        ) {
                            if (toggleLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = toggleTint
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PowerSettingsNew,
                                    contentDescription = toggleContentDescription,
                                    tint = toggleTint
                                )
                            }
                        }
                    }
                    onEdit?.let {
                        IconButton(onClick = it) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = editContentDescription,
                                tint = accent
                            )
                        }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PangolinPublicResourceEditorSheet(
    strings: PangolinStrings,
    resource: PangolinResource,
    targets: List<PangolinTarget>,
    sites: List<PangolinSite>,
    onDismiss: () -> Unit,
    onSave: suspend (PangolinPublicResourceUpdateInput) -> Unit
) {
    val scope = rememberCoroutineScope()
    val initialTarget = remember(targets) { targets.firstOrNull { it.enabled } ?: targets.firstOrNull() }
    val siteOptions = remember(sites) {
        sites.sortedBy { it.name.lowercase() }.map { PangolinDropdownOption(it.siteId, it.name) }
    }
    val targetOptions = remember(targets) {
        targets.map { target ->
            PangolinDropdownOption(target.targetId, "${target.ip}:${target.port}")
        }
    }

    var saving by remember(resource.resourceId) { mutableStateOf(false) }
    var name by remember(resource.resourceId) { mutableStateOf(resource.name) }
    var enabled by remember(resource.resourceId) { mutableStateOf(resource.enabled) }
    var sso by remember(resource.resourceId) { mutableStateOf(resource.sso) }
    var ssl by remember(resource.resourceId) { mutableStateOf(resource.ssl) }
    var selectedTargetId by remember(resource.resourceId, targets) { mutableStateOf(initialTarget?.targetId) }
    var selectedSiteId by remember(resource.resourceId, targets) { mutableStateOf(initialTarget?.siteId) }
    var targetIp by remember(resource.resourceId, targets) { mutableStateOf(initialTarget?.ip.orEmpty()) }
    var targetPort by remember(resource.resourceId, targets) { mutableStateOf(initialTarget?.port?.toString().orEmpty()) }
    var targetEnabled by remember(resource.resourceId, targets) { mutableStateOf(initialTarget?.enabled ?: true) }

    fun syncTarget(target: PangolinTarget?) {
        selectedTargetId = target?.targetId
        selectedSiteId = target?.siteId
        targetIp = target?.ip.orEmpty()
        targetPort = target?.port?.toString().orEmpty()
        targetEnabled = target?.enabled ?: true
    }

    val canSave = name.isNotBlank() && (
        selectedTargetId == null ||
            (selectedSiteId != null && targetIp.isNotBlank() && targetPort.toIntOrNull() != null)
        )

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(strings.editPublicResource, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            resource.fullDomain?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.name) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            PangolinSwitchRow(
                title = strings.enabled,
                checked = enabled,
                enabled = !saving,
                onCheckedChange = { enabled = it }
            )
            PangolinSwitchRow(
                title = strings.pangolinSso,
                checked = sso,
                enabled = !saving,
                onCheckedChange = { sso = it }
            )
            PangolinSwitchRow(
                title = strings.tls,
                checked = ssl,
                enabled = !saving,
                onCheckedChange = { ssl = it }
            )

            if (targetOptions.isNotEmpty()) {
                Text(strings.target, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (targetOptions.size > 1) {
                    PangolinDropdownField(
                        label = strings.target,
                        selectedValue = selectedTargetId,
                        options = targetOptions,
                        enabled = !saving,
                        onSelected = { targetId ->
                            syncTarget(targets.firstOrNull { it.targetId == targetId })
                        }
                    )
                }
                PangolinDropdownField(
                    label = strings.site,
                    selectedValue = selectedSiteId,
                    options = siteOptions,
                    enabled = !saving,
                    onSelected = { selectedSiteId = it }
                )
                OutlinedTextField(
                    value = targetIp,
                    onValueChange = { targetIp = it },
                    label = { Text(strings.targetIp) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    shape = RoundedCornerShape(16.dp)
                )
                OutlinedTextField(
                    value = targetPort,
                    onValueChange = { targetPort = it.filter(Char::isDigit) },
                    label = { Text(strings.targetPort) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp)
                )
                PangolinSwitchRow(
                    title = strings.targetEnabled,
                    checked = targetEnabled,
                    enabled = !saving,
                    onCheckedChange = { targetEnabled = it }
                )
            } else {
                Text(strings.noEditableTargets, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(strings.cancel)
                }
                TextButton(
                    onClick = {
                        if (!canSave || saving) return@TextButton
                        saving = true
                        scope.launch {
                            try {
                                onSave(
                                    PangolinPublicResourceUpdateInput(
                                        resourceId = resource.resourceId,
                                        name = name,
                                        enabled = enabled,
                                        sso = sso,
                                        ssl = ssl,
                                        targetId = selectedTargetId,
                                        targetSiteId = selectedSiteId,
                                        targetIp = targetIp,
                                        targetPort = targetPort,
                                        targetEnabled = targetEnabled
                                    )
                                )
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = canSave && !saving
                ) {
                    Text(if (saving) strings.saving else strings.save)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PangolinPublicResourceCreateSheet(
    strings: PangolinStrings,
    sites: List<PangolinSite>,
    domains: List<PangolinDomain>,
    onDismiss: () -> Unit,
    onSave: suspend (PangolinPublicResourceCreateInput) -> Unit
) {
    val scope = rememberCoroutineScope()
    val siteOptions = remember(sites) {
        sites.sortedBy { it.name.lowercase() }.map { PangolinDropdownOption(it.siteId, it.name) }
    }
    val domainOptions = remember(domains) {
        domains.sortedBy { it.baseDomain.lowercase() }.map { PangolinDropdownOption(it.domainId, it.baseDomain) }
    }
    val protocolOptions = listOf(
        PangolinDropdownOption("http", strings.httpResource),
        PangolinDropdownOption("tcp", strings.tcpResource),
        PangolinDropdownOption("udp", strings.udpResource)
    )
    val targetMethodOptions = listOf(
        PangolinDropdownOption("http", strings.httpMethod),
        PangolinDropdownOption("https", strings.httpsMethod),
        PangolinDropdownOption("h2c", strings.h2cMethod)
    )

    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var protocol by remember { mutableStateOf("http") }
    var enabled by remember { mutableStateOf(true) }
    var selectedDomainId by remember(domainOptions) { mutableStateOf(domainOptions.firstOrNull()?.value.orEmpty()) }
    var subdomain by remember { mutableStateOf("") }
    var proxyPort by remember { mutableStateOf("") }
    var selectedSiteId by remember(siteOptions) { mutableStateOf(siteOptions.firstOrNull()?.value ?: 0) }
    var targetIp by remember { mutableStateOf("") }
    var targetPort by remember { mutableStateOf("") }
    var targetEnabled by remember { mutableStateOf(true) }
    var targetMethod by remember { mutableStateOf("http") }

    val isHttp = protocol == "http"
    val canSave = name.isNotBlank() &&
        selectedSiteId > 0 &&
        targetIp.isNotBlank() &&
        targetPort.toIntOrNull() != null &&
        (!isHttp || selectedDomainId.isNotBlank()) &&
        (isHttp || proxyPort.toIntOrNull() != null)

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(strings.createPublicResource, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.name) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            PangolinDropdownField(
                label = strings.protocolLabel,
                selectedValue = protocol,
                options = protocolOptions,
                enabled = !saving,
                onSelected = { protocol = it }
            )
            if (isHttp) {
                PangolinDropdownField(
                    label = strings.domainLabel,
                    selectedValue = selectedDomainId,
                    options = domainOptions,
                    enabled = !saving && domainOptions.isNotEmpty(),
                    onSelected = { selectedDomainId = it }
                )
                OutlinedTextField(
                    value = subdomain,
                    onValueChange = { subdomain = it.trim() },
                    label = { Text(strings.subdomainLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    shape = RoundedCornerShape(16.dp)
                )
            } else {
                OutlinedTextField(
                    value = proxyPort,
                    onValueChange = { proxyPort = it.filter(Char::isDigit) },
                    label = { Text(strings.proxyPortLabel) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !saving,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(16.dp)
                )
            }
            PangolinSwitchRow(
                title = strings.enabled,
                checked = enabled,
                enabled = !saving,
                onCheckedChange = { enabled = it }
            )

            Text(strings.target, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            PangolinDropdownField(
                label = strings.site,
                selectedValue = selectedSiteId,
                options = siteOptions,
                enabled = !saving,
                onSelected = { selectedSiteId = it }
            )
            OutlinedTextField(
                value = targetIp,
                onValueChange = { targetIp = it },
                label = { Text(strings.targetIp) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = targetPort,
                onValueChange = { targetPort = it.filter(Char::isDigit) },
                label = { Text(strings.targetPort) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp)
            )
            if (isHttp) {
                PangolinDropdownField(
                    label = strings.backendMethodLabel,
                    selectedValue = targetMethod,
                    options = targetMethodOptions,
                    enabled = !saving,
                    onSelected = { targetMethod = it }
                )
            }
            PangolinSwitchRow(
                title = strings.targetEnabled,
                checked = targetEnabled,
                enabled = !saving,
                onCheckedChange = { targetEnabled = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(strings.cancel)
                }
                TextButton(
                    onClick = {
                        if (!canSave || saving) return@TextButton
                        saving = true
                        scope.launch {
                            try {
                                onSave(
                                    PangolinPublicResourceCreateInput(
                                        name = name,
                                        protocol = protocol,
                                        enabled = enabled,
                                        domainId = selectedDomainId.takeIf { it.isNotBlank() },
                                        subdomain = subdomain,
                                        proxyPort = proxyPort,
                                        targetSiteId = selectedSiteId,
                                        targetIp = targetIp,
                                        targetPort = targetPort,
                                        targetEnabled = targetEnabled,
                                        targetMethod = if (isHttp) targetMethod else null
                                    )
                                )
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = canSave && !saving
                ) {
                    Text(if (saving) strings.saving else strings.save)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PangolinPrivateResourceEditorSheet(
    strings: PangolinStrings,
    resource: PangolinSiteResource,
    sites: List<PangolinSite>,
    onDismiss: () -> Unit,
    onSave: suspend (PangolinPrivateResourceUpdateInput) -> Unit
) {
    val scope = rememberCoroutineScope()
    val siteOptions = remember(sites) {
        sites.sortedBy { it.name.lowercase() }.map { PangolinDropdownOption(it.siteId, it.name) }
    }
    val modeOptions = listOf(
        PangolinDropdownOption("host", strings.hostMode),
        PangolinDropdownOption("cidr", strings.cidrMode)
    )
    val authDaemonOptions = listOf(
        PangolinDropdownOption("", strings.none),
        PangolinDropdownOption("site", strings.siteMode),
        PangolinDropdownOption("remote", strings.remoteMode)
    )

    var saving by remember(resource.siteResourceId) { mutableStateOf(false) }
    var name by remember(resource.siteResourceId) { mutableStateOf(resource.name) }
    var selectedSiteId by remember(resource.siteResourceId) { mutableStateOf(resource.siteId) }
    var mode by remember(resource.siteResourceId) { mutableStateOf(resource.mode ?: "host") }
    var destination by remember(resource.siteResourceId) { mutableStateOf(resource.destination.orEmpty()) }
    var enabled by remember(resource.siteResourceId) { mutableStateOf(resource.enabled) }
    var alias by remember(resource.siteResourceId) { mutableStateOf(resource.alias.orEmpty()) }
    var tcpPorts by remember(resource.siteResourceId) { mutableStateOf(resource.tcpPortRangeString.orEmpty()) }
    var udpPorts by remember(resource.siteResourceId) { mutableStateOf(resource.udpPortRangeString.orEmpty()) }
    var disableIcmp by remember(resource.siteResourceId) { mutableStateOf(resource.disableIcmp ?: false) }
    var authDaemonPort by remember(resource.siteResourceId) { mutableStateOf(resource.authDaemonPort?.toString().orEmpty()) }
    var authDaemonMode by remember(resource.siteResourceId) { mutableStateOf(resource.authDaemonMode.orEmpty()) }

    val canSave = name.isNotBlank() &&
        destination.isNotBlank() &&
        selectedSiteId > 0 &&
        mode in setOf("host", "cidr") &&
        (authDaemonPort.isBlank() || authDaemonPort.toIntOrNull() != null)

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(strings.editPrivateResource, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
            Text(
                text = listOfNotNull(resource.siteName, resource.protocol?.uppercase()).joinToString(" • "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.name) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            PangolinDropdownField(
                label = strings.site,
                selectedValue = selectedSiteId,
                options = siteOptions,
                enabled = !saving,
                onSelected = { selectedSiteId = it }
            )
            PangolinDropdownField(
                label = strings.mode,
                selectedValue = mode,
                options = modeOptions,
                enabled = !saving,
                onSelected = { mode = it }
            )
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text(strings.destination) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text(strings.aliasLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = tcpPorts,
                onValueChange = { tcpPorts = it },
                label = { Text(strings.tcpPortsLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = udpPorts,
                onValueChange = { udpPorts = it },
                label = { Text(strings.udpPortsLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = authDaemonPort,
                onValueChange = { authDaemonPort = it.filter(Char::isDigit) },
                label = { Text(strings.authDaemonPortLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp)
            )
            PangolinDropdownField(
                label = strings.authDaemonModeLabel,
                selectedValue = authDaemonMode,
                options = authDaemonOptions,
                enabled = !saving,
                onSelected = { authDaemonMode = it }
            )
            PangolinSwitchRow(
                title = strings.disableIcmp,
                checked = disableIcmp,
                enabled = !saving,
                onCheckedChange = { disableIcmp = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(strings.cancel)
                }
                TextButton(
                    onClick = {
                        if (!canSave || saving) return@TextButton
                        saving = true
                        scope.launch {
                            try {
                                onSave(
                                    PangolinPrivateResourceUpdateInput(
                                        siteResourceId = resource.siteResourceId,
                                        name = name,
                                        siteId = selectedSiteId,
                                        mode = mode,
                                        destination = destination,
                                        enabled = enabled,
                                        alias = alias,
                                        tcpPortRangeString = tcpPorts,
                                        udpPortRangeString = udpPorts,
                                        disableIcmp = disableIcmp,
                                        authDaemonPort = authDaemonPort,
                                        authDaemonMode = authDaemonMode.ifBlank { null }
                                    )
                                )
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = canSave && !saving
                ) {
                    Text(if (saving) strings.saving else strings.save)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PangolinPrivateResourceCreateSheet(
    strings: PangolinStrings,
    sites: List<PangolinSite>,
    onDismiss: () -> Unit,
    onSave: suspend (PangolinPrivateResourceCreateInput) -> Unit
) {
    val scope = rememberCoroutineScope()
    val siteOptions = remember(sites) {
        sites.sortedBy { it.name.lowercase() }.map { PangolinDropdownOption(it.siteId, it.name) }
    }
    val modeOptions = listOf(
        PangolinDropdownOption("host", strings.hostMode),
        PangolinDropdownOption("cidr", strings.cidrMode)
    )
    val authDaemonOptions = listOf(
        PangolinDropdownOption("", strings.none),
        PangolinDropdownOption("site", strings.siteMode),
        PangolinDropdownOption("remote", strings.remoteMode)
    )

    var saving by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var selectedSiteId by remember(siteOptions) { mutableStateOf(siteOptions.firstOrNull()?.value ?: 0) }
    var mode by remember { mutableStateOf("host") }
    var destination by remember { mutableStateOf("") }
    var enabled by remember { mutableStateOf(true) }
    var alias by remember { mutableStateOf("") }
    var tcpPorts by remember { mutableStateOf("*") }
    var udpPorts by remember { mutableStateOf("*") }
    var disableIcmp by remember { mutableStateOf(false) }
    var authDaemonPort by remember { mutableStateOf("") }
    var authDaemonMode by remember { mutableStateOf("") }

    val canSave = name.isNotBlank() &&
        destination.isNotBlank() &&
        selectedSiteId > 0 &&
        mode in setOf("host", "cidr") &&
        (authDaemonPort.isBlank() || authDaemonPort.toIntOrNull() != null)

    ModalBottomSheet(
        onDismissRequest = { if (!saving) onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(strings.createPrivateResource, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(strings.name) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            PangolinDropdownField(
                label = strings.site,
                selectedValue = selectedSiteId,
                options = siteOptions,
                enabled = !saving,
                onSelected = { selectedSiteId = it }
            )
            PangolinDropdownField(
                label = strings.mode,
                selectedValue = mode,
                options = modeOptions,
                enabled = !saving,
                onSelected = { mode = it }
            )
            OutlinedTextField(
                value = destination,
                onValueChange = { destination = it },
                label = { Text(strings.destination) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = alias,
                onValueChange = { alias = it },
                label = { Text(strings.aliasLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = tcpPorts,
                onValueChange = { tcpPorts = it },
                label = { Text(strings.tcpPortsLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = udpPorts,
                onValueChange = { udpPorts = it },
                label = { Text(strings.udpPortsLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                shape = RoundedCornerShape(16.dp)
            )
            OutlinedTextField(
                value = authDaemonPort,
                onValueChange = { authDaemonPort = it.filter(Char::isDigit) },
                label = { Text(strings.authDaemonPortLabel) },
                modifier = Modifier.fillMaxWidth(),
                enabled = !saving,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                shape = RoundedCornerShape(16.dp)
            )
            PangolinDropdownField(
                label = strings.authDaemonModeLabel,
                selectedValue = authDaemonMode,
                options = authDaemonOptions,
                enabled = !saving,
                onSelected = { authDaemonMode = it }
            )
            PangolinSwitchRow(
                title = strings.disableIcmp,
                checked = disableIcmp,
                enabled = !saving,
                onCheckedChange = { disableIcmp = it }
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss, enabled = !saving) {
                    Text(strings.cancel)
                }
                TextButton(
                    onClick = {
                        if (!canSave || saving) return@TextButton
                        saving = true
                        scope.launch {
                            try {
                                onSave(
                                    PangolinPrivateResourceCreateInput(
                                        name = name,
                                        siteId = selectedSiteId,
                                        mode = mode,
                                        destination = destination,
                                        enabled = enabled,
                                        alias = alias,
                                        tcpPortRangeString = tcpPorts,
                                        udpPortRangeString = udpPorts,
                                        disableIcmp = disableIcmp,
                                        authDaemonPort = authDaemonPort,
                                        authDaemonMode = authDaemonMode.ifBlank { null }
                                    )
                                )
                            } finally {
                                saving = false
                            }
                        }
                    },
                    enabled = canSave && !saving
                ) {
                    Text(if (saving) strings.saving else strings.save)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
private fun PangolinSwitchRow(
    title: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLowest
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                enabled = enabled
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> PangolinDropdownField(
    label: String,
    selectedValue: T?,
    options: List<PangolinDropdownOption<T>>,
    enabled: Boolean,
    onSelected: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = options.firstOrNull { it.value == selectedValue }?.label.orEmpty()

    ExposedDropdownMenuBox(
        expanded = expanded && enabled,
        onExpandedChange = { expanded = if (enabled) it else false }
    ) {
        OutlinedTextField(
            value = selectedLabel,
            onValueChange = {},
            readOnly = true,
            enabled = enabled,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable, enabled = true)
                .fillMaxWidth(),
            shape = RoundedCornerShape(16.dp)
        )
        ExposedDropdownMenu(
            expanded = expanded && enabled,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = {
                        onSelected(option.value)
                        expanded = false
                    }
                )
            }
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
    val cancel: String = context.getString(R.string.cancel)
    val save: String = context.getString(R.string.save)
    val edit: String = context.getString(R.string.edit)
    val refresh: String = context.getString(R.string.refresh)
    val retry: String = context.getString(R.string.retry)
    val error: String = context.getString(R.string.error)
    val enableAction: String = context.getString(R.string.pangolin_action_enable)
    val disableAction: String = context.getString(R.string.pangolin_action_disable)
    val saved: String = context.getString(R.string.pangolin_saved)
    val saving: String = context.getString(R.string.pangolin_saving)
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
    val disableIcmp: String = context.getString(R.string.pangolin_disable_icmp)
    val site: String = context.getString(R.string.pangolin_site)
    val name: String = context.getString(R.string.pangolin_name)
    val mode: String = context.getString(R.string.pangolin_mode)
    val destination: String = context.getString(R.string.pangolin_destination)
    val target: String = context.getString(R.string.pangolin_target)
    val targetIp: String = context.getString(R.string.pangolin_target_ip)
    val targetPort: String = context.getString(R.string.pangolin_target_port)
    val targetEnabled: String = context.getString(R.string.pangolin_target_enabled)
    val tls: String = context.getString(R.string.pangolin_tls)
    val pangolinSso: String = context.getString(R.string.pangolin_sso_label)
    val aliasLabel: String = context.getString(R.string.pangolin_alias_label)
    val tcpPortsLabel: String = context.getString(R.string.pangolin_tcp_ports_label)
    val udpPortsLabel: String = context.getString(R.string.pangolin_udp_ports_label)
    val authDaemonPortLabel: String = context.getString(R.string.pangolin_authd_port_label)
    val authDaemonModeLabel: String = context.getString(R.string.pangolin_authd_mode_label)
    val editPublicResource: String = context.getString(R.string.pangolin_edit_public_resource)
    val editPrivateResource: String = context.getString(R.string.pangolin_edit_private_resource)
    val createPublicResource: String = context.getString(R.string.pangolin_create_public_resource)
    val createPrivateResource: String = context.getString(R.string.pangolin_create_private_resource)
    val noEditableTargets: String = context.getString(R.string.pangolin_no_editable_targets)
    val none: String = context.getString(R.string.pangolin_none)
    val protocolLabel: String = context.getString(R.string.pangolin_protocol_label)
    val domainLabel: String = context.getString(R.string.pangolin_domain_label)
    val subdomainLabel: String = context.getString(R.string.pangolin_subdomain_label)
    val backendMethodLabel: String = context.getString(R.string.pangolin_backend_method_label)
    val proxyPortLabel: String = context.getString(R.string.pangolin_proxy_port_label)
    val httpResource: String = context.getString(R.string.pangolin_protocol_http)
    val tcpResource: String = context.getString(R.string.pangolin_protocol_tcp)
    val udpResource: String = context.getString(R.string.pangolin_protocol_udp)
    val httpMethod: String = context.getString(R.string.pangolin_target_method_http)
    val httpsMethod: String = context.getString(R.string.pangolin_target_method_https)
    val h2cMethod: String = context.getString(R.string.pangolin_target_method_h2c)
    val hostMode: String = context.getString(R.string.pangolin_mode_host)
    val cidrMode: String = context.getString(R.string.pangolin_mode_cidr)
    val siteMode: String = context.getString(R.string.pangolin_authd_mode_site)
    val remoteMode: String = context.getString(R.string.pangolin_authd_mode_remote)

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
    fun agent(value: String): String = context.getString(R.string.pangolin_agent, value)
    fun clientSource(value: PangolinClientSource): String = when (value) {
        PangolinClientSource.MACHINE -> context.getString(R.string.pangolin_machine_client)
        PangolinClientSource.USER_DEVICE -> context.getString(R.string.pangolin_user_device)
    }

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
