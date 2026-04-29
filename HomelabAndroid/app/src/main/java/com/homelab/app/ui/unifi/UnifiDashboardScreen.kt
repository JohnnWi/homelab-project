package com.homelab.app.ui.unifi

import android.annotation.SuppressLint
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DeviceHub
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Lan
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Router
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.homelab.app.R
import com.homelab.app.data.repository.UnifiDashboardData
import com.homelab.app.data.repository.UnifiClient
import com.homelab.app.data.repository.UnifiDevice
import com.homelab.app.data.repository.UnifiHost
import com.homelab.app.data.repository.UnifiIspMetric
import com.homelab.app.data.repository.UnifiNetwork
import com.homelab.app.data.repository.UnifiPort
import com.homelab.app.data.repository.UnifiRadio
import com.homelab.app.data.repository.UnifiSite
import com.homelab.app.ui.components.ServiceIcon
import com.homelab.app.ui.components.ServiceInstancePicker
import com.homelab.app.ui.theme.primaryColor
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import kotlin.math.max
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UnifiDashboardScreen(
    onNavigateBack: () -> Unit,
    onNavigateToInstance: (String) -> Unit,
    viewModel: UnifiViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val isRefreshing by viewModel.isRefreshing.collectAsStateWithLifecycle()
    val isDemo by viewModel.isDemo.collectAsStateWithLifecycle()
    val actionMessage by viewModel.actionMessage.collectAsStateWithLifecycle()
    val instances by viewModel.instances.collectAsStateWithLifecycle()
    val selectedSiteId by viewModel.selectedSiteId.collectAsStateWithLifecycle()
    val accent = ServiceType.UNIFI_NETWORK.primaryColor
    var selectedDevice by remember { mutableStateOf<UnifiDevice?>(null) }
    var selectedClient by remember { mutableStateOf<UnifiClient?>(null) }
    var deviceFilter by remember { mutableStateOf(UnifiDeviceFilter.ALL) }
    var deviceSearch by remember { mutableStateOf("") }
    var clientFilter by remember { mutableStateOf(UnifiClientFilter.ALL) }
    var clientSearch by remember { mutableStateOf("") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(ServiceType.UNIFI_NETWORK.displayName, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.openDemo() }) {
                        Text(stringResource(R.string.unifi_open_demo))
                    }
                    IconButton(onClick = { viewModel.fetchDashboard() }, enabled = !isRefreshing) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { padding ->
        when (val state = uiState) {
            UiState.Loading, UiState.Idle -> LoadingState(modifier = Modifier.fillMaxSize().padding(padding), accent = accent)
            is UiState.Error -> ErrorState(
                message = state.message,
                onRetry = { state.retryAction?.invoke() ?: viewModel.fetchDashboard(forceLoading = true) },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
            is UiState.Success -> {
                val scoped = state.data.scoped(selectedSiteId)
                val filteredDevices = scoped.devices
                    .filter { device -> deviceFilter.matches(device) }
                    .filter { device ->
                        deviceSearch.isBlank() ||
                            listOfNotNull(device.name, device.model, device.ipAddress, device.macAddress)
                                .any { it.contains(deviceSearch, ignoreCase = true) }
                    }
                val filteredClients = scoped.clients
                    .filter { client -> clientFilter.matches(client) }
                    .filter { client ->
                        clientSearch.isBlank() ||
                            listOfNotNull(client.name, client.ipAddress, client.macAddress, client.networkName, client.accessPointName)
                                .any { it.contains(clientSearch, ignoreCase = true) }
                    }
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(padding),
                    contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    item {
                        if (instances.size > 1) {
                            ServiceInstancePicker(
                                instances = instances,
                                selectedInstanceId = viewModel.instanceId,
                                onInstanceSelected = { instance ->
                                    viewModel.setPreferredInstance(instance.id)
                                    onNavigateToInstance(instance.id)
                                }
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        UnifiHero(data = scoped, allSites = state.data.sites)
                    }
                    if (isDemo) {
                        item { InfoBanner(text = stringResource(R.string.unifi_demo_info), accent = accent) }
                    }
                    actionMessage?.let { message ->
                        item { InfoBanner(text = message, accent = Color(0xFF34C759)) }
                    }
                    item {
                        SiteChips(
                            sites = state.data.sites,
                            selectedSiteId = selectedSiteId,
                            onSelectSite = viewModel::selectSite
                        )
                    }
                    item { OverviewGrid(data = scoped, accent = accent) }
                    item { OperationsBoard(data = scoped, accent = accent) }
                    if (scoped.clients.isNotEmpty()) {
                        item { ClientExperienceCard(data = scoped, accent = accent) }
                    }
                    item { InternetActivityCard(data = scoped, accent = accent) }
                    item { TopologyCard(data = scoped, accent = accent) }
                    item { SectionTitle(stringResource(R.string.unifi_devices)) }
                    item {
                        DeviceFilters(
                            filter = deviceFilter,
                            onFilterChange = { deviceFilter = it },
                            search = deviceSearch,
                            onSearchChange = { deviceSearch = it }
                        )
                    }
                    items(filteredDevices.sortedWith(compareByDescending<UnifiDevice> { it.online }.thenBy { it.name }), key = { it.id }) { device ->
                        DeviceRow(
                            device = device,
                            accent = accent,
                            onClick = { selectedDevice = device }
                        )
                    }
                    if (filteredDevices.isEmpty()) {
                        item { EmptyCard(stringResource(R.string.unifi_no_devices_for_site)) }
                    }
                    if (scoped.clients.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.unifi_clients)) }
                        item {
                            ClientFilters(
                                filter = clientFilter,
                                onFilterChange = { clientFilter = it },
                                search = clientSearch,
                                onSearchChange = { clientSearch = it }
                            )
                        }
                        items(
                            filteredClients.sortedByDescending { (it.rxBps ?: 0.0) + (it.txBps ?: 0.0) },
                            key = { "client_${it.id}" }
                        ) { client ->
                            ClientRow(client = client, accent = accent, onClick = { selectedClient = client })
                        }
                        if (filteredClients.isEmpty()) {
                            item { EmptyCard(stringResource(R.string.unifi_no_clients)) }
                        }
                    }
                    if (scoped.networks.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.unifi_networks)) }
                        items(scoped.networks.sortedBy { it.name }, key = { "network_${it.id}" }) { network ->
                            NetworkRow(network = network, accent = accent)
                        }
                    }
                    if (scoped.hosts.isNotEmpty()) {
                        item { SectionTitle(stringResource(R.string.unifi_hosts)) }
                        items(scoped.hosts.sortedBy { it.name }, key = { "host_${it.id}" }) { host ->
                            HostRow(host = host, accent = accent)
                        }
                    }
                }
            }
            UiState.Offline -> ErrorState(
                message = stringResource(R.string.unifi_offline_message),
                onRetry = { viewModel.fetchDashboard(forceLoading = true) },
                modifier = Modifier.fillMaxSize().padding(padding)
            )
        }
    }

    selectedDevice?.let { device ->
        ModalBottomSheet(onDismissRequest = { selectedDevice = null }) {
            DeviceDetailSheet(device = device, accent = accent, onClose = { selectedDevice = null })
        }
    }
    selectedClient?.let { client ->
        ModalBottomSheet(onDismissRequest = { selectedClient = null }) {
            ClientDetailSheet(
                client = client,
                accent = accent,
                onAuthorizeGuest = {
                    client.siteId?.let { siteId -> viewModel.authorizeGuest(siteId, client.id) }
                    selectedClient = null
                },
                onClose = { selectedClient = null }
            )
        }
    }
}

@Composable
private fun LoadingState(modifier: Modifier, accent: Color) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = accent)
    }
}

@Composable
private fun UnifiHero(data: UnifiDashboardData, allSites: List<UnifiSite>) {
    val accent = ServiceType.UNIFI_NETWORK.primaryColor
    val site = data.sites.firstOrNull()
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
        shape = RoundedCornerShape(28.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.20f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ServiceIcon(type = ServiceType.UNIFI_NETWORK, size = 68.dp, iconSize = 46.dp, cornerRadius = 20.dp)
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = site?.name ?: ServiceType.UNIFI_NETWORK.displayName,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (data.authMode.name == "SITE_MANAGER") {
                            stringResource(R.string.unifi_site_manager)
                        } else {
                            stringResource(R.string.unifi_local_network)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                StatusPill(
                    text = site?.health?.let(::healthLabel) ?: stringResource(R.string.unifi_online),
                    online = data.offlineDeviceCount == 0
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                HeroStat(stringResource(R.string.unifi_devices), "${data.onlineDeviceCount}/${deviceTotal(data)}", Icons.Default.Devices, accent, Modifier.weight(1f))
                HeroStat(stringResource(R.string.unifi_clients), "${data.totalClients}", Icons.Default.Groups, Color(0xFF34C759), Modifier.weight(1f))
                HeroStat(stringResource(R.string.unifi_sites), "${if (site == null) allSites.size else 1}", Icons.Default.CloudQueue, Color(0xFF8B5CF6), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun HeroStat(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(
        modifier = modifier.height(92.dp),
        color = color.copy(alpha = 0.10f),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Column {
                Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 21.sp)
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            }
        }
    }
}

@Composable
private fun SiteChips(
    sites: List<UnifiSite>,
    selectedSiteId: String?,
    onSelectSite: (String?) -> Unit
) {
    Row(
        modifier = Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        AssistChip(
            onClick = { onSelectSite(null) },
            label = { Text(stringResource(R.string.unifi_all_sites)) },
            leadingIcon = { Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(18.dp)) },
            border = BorderStroke(1.dp, if (selectedSiteId == null) ServiceType.UNIFI_NETWORK.primaryColor else MaterialTheme.colorScheme.outlineVariant)
        )
        sites.forEach { site ->
            AssistChip(
                onClick = { onSelectSite(site.id) },
                label = { Text(site.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                border = BorderStroke(1.dp, if (selectedSiteId == site.id) ServiceType.UNIFI_NETWORK.primaryColor else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun OverviewGrid(data: UnifiDashboardData, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(stringResource(R.string.unifi_download), formatRate(data.devices.sumOf { it.rxBps ?: 0.0 }), Icons.Default.Speed, Color(0xFF34C759), Modifier.weight(1f))
            MetricCard(stringResource(R.string.unifi_upload), formatRate(data.devices.sumOf { it.txBps ?: 0.0 }), Icons.Default.Speed, accent, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(stringResource(R.string.unifi_updates), "${data.devices.count { it.upgradeable }}", Icons.Default.Update, Color(0xFFFF9500), Modifier.weight(1f))
            MetricCard(stringResource(R.string.unifi_networks), "${data.networks.size}", Icons.Default.Dns, Color(0xFF8B5CF6), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MetricCard(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.height(104.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.10f)),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(14.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(22.dp))
            Column {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = color, maxLines = 1)
                Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun InfoBanner(text: String, accent: Color) {
    Surface(
        color = accent.copy(alpha = 0.10f),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, accent.copy(alpha = 0.22f))
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun OperationsBoard(data: UnifiDashboardData, accent: Color) {
    val updates = data.devices.count { it.upgradeable }
    val offline = data.offlineDeviceCount
    val poeWatts = data.devices.flatMap { it.ports }.sumOf { it.poePowerWatts ?: 0.0 }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.unifi_operations), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                OpsChip(stringResource(R.string.unifi_offline), "$offline", if (offline == 0) Color(0xFF34C759) else Color(0xFFFF9500), Modifier.weight(1f))
                OpsChip(stringResource(R.string.unifi_updates), "$updates", if (updates == 0) accent else Color(0xFFFF9500), Modifier.weight(1f))
                OpsChip("PoE", String.format(Locale.US, "%.1f W", poeWatts), Color(0xFF34C759), Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun OpsChip(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Surface(modifier = modifier.height(76.dp), color = color.copy(alpha = 0.11f), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Text(value, color = color, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, maxLines = 1)
        }
    }
}

@Composable
private fun ClientExperienceCard(data: UnifiDashboardData, accent: Color) {
    val experienceValues = data.clients.mapNotNull { it.experiencePercent }.filter { it.isFinite() }
    val averageExperience = experienceValues.average().takeIf { it.isFinite() }
    val weakSignal = data.clients.count { (it.signalDbm ?: 0) <= -75 }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(22.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Groups, contentDescription = null, tint = accent, modifier = Modifier.size(22.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.unifi_client_experience), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                MetricCard(
                    label = stringResource(R.string.unifi_experience),
                    value = averageExperience?.let { "${it.toInt()}%" } ?: "N/A",
                    icon = Icons.Default.Wifi,
                    color = Color(0xFF34C759),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    label = stringResource(R.string.unifi_weak_signal),
                    value = "$weakSignal",
                    icon = Icons.Default.Speed,
                    color = if (weakSignal == 0) accent else Color(0xFFFF9500),
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun InternetActivityCard(data: UnifiDashboardData, accent: Color) {
    val download = data.ispMetrics.mapNotNull { it.downloadMbps }.ifEmpty { data.devices.mapNotNull { it.rxBps?.div(1_000_000.0) } }
    val upload = data.ispMetrics.mapNotNull { it.uploadMbps }.ifEmpty { data.devices.mapNotNull { it.txBps?.div(1_000_000.0) } }
    val latency = data.ispMetrics.mapNotNull { it.latencyMs }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Speed, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.unifi_internet_activity), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            DualLineChart(
                primary = download,
                secondary = upload,
                primaryColor = Color(0xFF38C7F2),
                secondaryColor = Color(0xFFA855F7),
                modifier = Modifier.fillMaxWidth().height(170.dp)
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("DL ${formatMegabits(download.lastOrNull())}", color = Color(0xFF38C7F2), fontWeight = FontWeight.SemiBold)
                Text("UL ${formatMegabits(upload.lastOrNull())}", color = Color(0xFFA855F7), fontWeight = FontWeight.SemiBold)
                latency.lastOrNull()?.let {
                    Text("${it.toInt()} ms", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun DualLineChart(
    primary: List<Double>,
    secondary: List<Double>,
    primaryColor: Color,
    secondaryColor: Color,
    modifier: Modifier = Modifier
) {
    val safePrimary = primary.takeLast(48)
    val safeSecondary = secondary.takeLast(48)
    val maxValue = max(
        safePrimary.maxOrNull() ?: 0.0,
        safeSecondary.maxOrNull() ?: 0.0
    ).coerceAtLeast(1.0)
    val grid = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        repeat(4) { index ->
            val y = size.height * index / 3f
            drawLine(grid, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
        }
        drawSeries(safePrimary, maxValue, primaryColor)
        drawSeries(safeSecondary, maxValue, secondaryColor)
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawSeries(values: List<Double>, maxValue: Double, color: Color) {
    if (values.size < 2) return
    val path = Path()
    val fill = Path()
    values.forEachIndexed { index, value ->
        val x = size.width * index / (values.lastIndex.coerceAtLeast(1)).toFloat()
        val y = size.height - (size.height * (value / maxValue).toFloat()).coerceIn(0f, size.height)
        if (index == 0) {
            path.moveTo(x, y)
            fill.moveTo(x, size.height)
            fill.lineTo(x, y)
        } else {
            path.lineTo(x, y)
            fill.lineTo(x, y)
        }
        if (index == values.lastIndex) {
            fill.lineTo(x, size.height)
            fill.close()
        }
    }
    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.22f), Color.Transparent)))
    drawPath(path, color, style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round))
}

@Composable
private fun TopologyCard(data: UnifiDashboardData, accent: Color) {
    val gateways = data.devices.filter { it.isGateway }
    val switches = data.devices.filter { it.isSwitch }
    val aps = data.devices.filter { it.isAccessPoint }
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.DeviceHub, contentDescription = null, tint = accent)
                Spacer(modifier = Modifier.width(10.dp))
                Text(stringResource(R.string.unifi_network_topology), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TopologyNode(stringResource(R.string.unifi_gateway), gateways.size, Icons.Default.Router, accent)
                TopologyLink()
                TopologyNode(stringResource(R.string.unifi_switch), switches.size, Icons.Default.Lan, Color(0xFF34C759))
                TopologyLink()
                TopologyNode(stringResource(R.string.unifi_aps), aps.size, Icons.Default.Wifi, Color(0xFF8B5CF6))
            }
        }
    }
}

@Composable
private fun TopologyNode(label: String, count: Int, icon: ImageVector, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Surface(shape = CircleShape, color = color.copy(alpha = 0.12f), modifier = Modifier.size(58.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(28.dp))
            }
        }
        Text(label, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.SemiBold)
        Text("$count", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopologyLink() {
    Box(
        modifier = Modifier
            .width(42.dp)
            .height(2.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp))
    )
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun DeviceFilters(
    filter: UnifiDeviceFilter,
    onFilterChange: (UnifiDeviceFilter) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UnifiDeviceFilter.entries.forEach { option ->
                AssistChip(
                    onClick = { onFilterChange(option) },
                    label = { Text(stringResource(option.labelRes)) },
                    border = BorderStroke(
                        1.dp,
                        if (filter == option) ServiceType.UNIFI_NETWORK.primaryColor else MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.unifi_search_devices)) },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun ClientFilters(
    filter: UnifiClientFilter,
    onFilterChange: (UnifiClientFilter) -> Unit,
    search: String,
    onSearchChange: (String) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            UnifiClientFilter.entries.forEach { option ->
                AssistChip(
                    onClick = { onFilterChange(option) },
                    label = { Text(stringResource(option.labelRes)) },
                    border = BorderStroke(
                        1.dp,
                        if (filter == option) ServiceType.UNIFI_NETWORK.primaryColor else MaterialTheme.colorScheme.outlineVariant
                    )
                )
            }
        }
        OutlinedTextField(
            value = search,
            onValueChange = onSearchChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
            placeholder = { Text(stringResource(R.string.unifi_search_clients)) },
            shape = RoundedCornerShape(18.dp)
        )
    }
}

@Composable
private fun DeviceRow(device: UnifiDevice, accent: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DeviceGlyph(device = device, accent = accent, modifier = Modifier.size(52.dp))
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (device.upgradeable) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Icon(Icons.Default.Update, contentDescription = null, tint = Color(0xFFFF9500), modifier = Modifier.size(16.dp))
                    }
                }
                Text(
                    listOfNotNull(device.model, device.ipAddress).joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val clients = device.connectedClients
                if (clients != null || device.ports.isNotEmpty()) {
                    Text(
                        listOfNotNull(
                            clients?.let { "$it ${stringResource(R.string.unifi_clients).lowercase()}" },
                            device.ports.takeIf { it.isNotEmpty() }?.let { "${it.count { port -> port.online }}/${it.size} ${stringResource(R.string.unifi_ports).lowercase()}" }
                        ).joinToString(" • "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        maxLines = 1
                    )
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                StatusDot(online = device.online)
                Spacer(modifier = Modifier.height(8.dp))
                Text(formatRate((device.rxBps ?: 0.0) + (device.txBps ?: 0.0)), color = accent, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun ClientRow(client: UnifiClient, accent: Color, onClick: () -> Unit) {
    Card(
        modifier = Modifier.clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        shape = RoundedCornerShape(18.dp)
    ) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.12f), modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(if (client.isWireless) Icons.Default.Wifi else Icons.Default.Lan, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(client.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    if (client.isGuestUnauthorized) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(shape = RoundedCornerShape(999.dp), color = Color(0xFFFF9500).copy(alpha = 0.14f)) {
                            Text(
                                stringResource(R.string.unifi_guest_unauthorized),
                                color = Color(0xFFFF9500),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp)
                            )
                        }
                    }
                }
                Text(
                    listOfNotNull(client.networkName, client.accessPointName, client.ipAddress).joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    client.signalDbm?.let {
                        Text("$it dBm", color = signalColor(it), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                    }
                    client.experiencePercent?.let {
                        Text("${it.toInt()}% ${stringResource(R.string.unifi_experience).lowercase()}", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            Text(formatRate((client.rxBps ?: 0.0) + (client.txBps ?: 0.0)), color = accent, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun NetworkRow(network: UnifiNetwork, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.12f), modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Dns, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(network.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(network.purpose, network.subnet, network.vlanId?.let { "VLAN $it" }).joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun HostRow(host: UnifiHost, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(18.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = RoundedCornerShape(14.dp), color = accent.copy(alpha = 0.12f), modifier = Modifier.size(50.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = accent, modifier = Modifier.size(28.dp))
                }
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(host.name, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(host.model, host.ipAddress, host.version).joinToString(" • "),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(host.status ?: "N/A", color = accent, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
private fun DeviceGlyph(device: UnifiDevice, accent: Color, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val assetResId = remember(device.model, device.name, device.type) {
        unifiDeviceAssetResId(context, device)
    }
    Surface(shape = RoundedCornerShape(15.dp), color = accent.copy(alpha = 0.12f), modifier = modifier) {
        Box(contentAlignment = Alignment.Center) {
            if (assetResId != 0) {
                Image(
                    painter = painterResource(assetResId),
                    contentDescription = device.name,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp),
                    contentScale = ContentScale.Fit
                )
            } else {
                Icon(
                    imageVector = when {
                        device.isAccessPoint -> Icons.Default.Wifi
                        device.isSwitch -> Icons.Default.Lan
                        device.isGateway -> Icons.Default.Router
                        else -> Icons.Default.Devices
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(30.dp)
                )
            }
            if (device.upgradeable) {
                Surface(
                    shape = CircleShape,
                    color = Color(0xFF5AA9FF),
                    modifier = Modifier.align(Alignment.TopEnd).size(18.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Default.Update, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusDot(online: Boolean) {
    Box(
        modifier = Modifier
            .size(10.dp)
            .background(if (online) Color(0xFF34C759) else Color(0xFFFF9500), CircleShape)
    )
}

@Composable
private fun StatusPill(text: String, online: Boolean) {
    Surface(
        color = if (online) Color(0xFF34C759).copy(alpha = 0.14f) else Color(0xFFFF9500).copy(alpha = 0.16f),
        shape = RoundedCornerShape(999.dp)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            color = if (online) Color(0xFF34C759) else Color(0xFFFF9500),
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

@Composable
private fun DeviceDetailSheet(device: UnifiDevice, accent: Color, onClose: () -> Unit) {
    LazyColumn(
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.unifi_close))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                DeviceGlyph(device = device, accent = accent, modifier = Modifier.size(104.dp))
                Spacer(modifier = Modifier.height(12.dp))
                Text(device.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    StatusPill(
                        if (device.online) stringResource(R.string.unifi_online) else stringResource(R.string.unifi_offline),
                        online = device.online
                    )
                    Surface(shape = RoundedCornerShape(999.dp), color = accent.copy(alpha = 0.12f)) {
                        Text(device.type, modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), color = accent, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
        item {
            OverviewGridForDevice(device = device, accent = accent)
        }
        if (device.cpuPercent != null || device.memoryPercent != null || device.temperatureCelsius != null) {
            item {
                DeviceHealthCard(device = device, accent = accent)
            }
        }
        if (device.ports.isNotEmpty()) {
            item {
                PortsCard(ports = device.ports, accent = accent)
            }
        }
        if (device.radios.isNotEmpty()) {
            item {
                RadiosCard(radios = device.radios, accent = accent)
            }
        }
        item {
            InfoCard(device = device)
        }
    }
}

@Composable
private fun ClientDetailSheet(
    client: UnifiClient,
    accent: Color,
    onAuthorizeGuest: () -> Unit,
    onClose: () -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(start = 22.dp, end = 22.dp, bottom = 34.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Default.Close, contentDescription = stringResource(R.string.unifi_close))
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Surface(shape = RoundedCornerShape(28.dp), color = accent.copy(alpha = 0.12f), modifier = Modifier.size(104.dp)) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            if (client.isWireless) Icons.Default.Wifi else Icons.Default.Lan,
                            contentDescription = null,
                            tint = accent,
                            modifier = Modifier.size(46.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(client.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    if (client.isWireless) stringResource(R.string.unifi_wifi_clients) else stringResource(R.string.unifi_wired_clients),
                    color = accent,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(22.dp)) {
                Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.unifi_traffic_now), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                        MetricCard(stringResource(R.string.unifi_download), formatRate(client.rxBps ?: 0.0), Icons.Default.Speed, Color(0xFF34C759), Modifier.weight(1f))
                        MetricCard(stringResource(R.string.unifi_upload), formatRate(client.txBps ?: 0.0), Icons.Default.Speed, accent, Modifier.weight(1f))
                    }
                    client.experiencePercent?.let {
                        ClientDetailMetric(stringResource(R.string.unifi_experience), "${it.toInt()}%", accent)
                    }
                    client.signalDbm?.let {
                        ClientDetailMetric(stringResource(R.string.unifi_signal), "$it dBm", signalColor(it))
                    }
                }
            }
        }
        if (client.isGuestUnauthorized && client.siteId != null) {
            item {
                Button(onClick = onAuthorizeGuest, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.unifi_guest))
                }
            }
        }
        item {
            InfoCard(client = client)
        }
    }
}

@Composable
private fun ClientDetailMetric(label: String, value: String, color: Color) {
    Surface(color = color.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.weight(1f))
            Text(value, color = color, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun OverviewGridForDevice(device: UnifiDevice, accent: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(stringResource(R.string.unifi_download), formatRate(device.rxBps ?: 0.0), Icons.Default.Speed, Color(0xFF34C759), Modifier.weight(1f))
            MetricCard(stringResource(R.string.unifi_upload), formatRate(device.txBps ?: 0.0), Icons.Default.Speed, accent, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            MetricCard(stringResource(R.string.unifi_clients), device.connectedClients?.toString() ?: "N/A", Icons.Default.Groups, Color(0xFFA855F7), Modifier.weight(1f))
            MetricCard(stringResource(R.string.unifi_ports), "${device.ports.count { it.online }}/${device.ports.size}", Icons.Default.Lan, Color(0xFFFF6B00), Modifier.weight(1f))
        }
    }
}

@Composable
private fun DeviceHealthCard(device: UnifiDevice, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.unifi_device_health), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                device.cpuPercent?.let { MiniHealth("CPU", "${it.toInt()}%", Icons.Default.Memory, accent, Modifier.weight(1f)) }
                device.memoryPercent?.let { MiniHealth("RAM", "${it.toInt()}%", Icons.Default.Memory, Color(0xFF60A5FA), Modifier.weight(1f)) }
            }
            device.temperatureCelsius?.let {
                MiniHealth(stringResource(R.string.unifi_temperature), "${it.toInt()}°C", Icons.Default.Thermostat, Color(0xFFFF6B00), Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun MiniHealth(label: String, value: String, icon: ImageVector, color: Color, modifier: Modifier) {
    Surface(modifier = modifier.height(86.dp), color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(16.dp)) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
            Column {
                Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = 22.sp)
                Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
        }
    }
}

@Composable
private fun PortsCard(ports: List<UnifiPort>, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.unifi_ports), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Text("${ports.count { it.online }}/${ports.size}", color = accent, fontWeight = FontWeight.Bold)
            }
            ports.sortedBy { it.number }.take(12).forEach { port ->
                PortRow(port = port, accent = accent)
            }
        }
    }
}

@Composable
private fun PortRow(port: UnifiPort, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = if (port.poe) Color(0xFF34C759).copy(alpha = 0.14f) else accent.copy(alpha = 0.12f), modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text("${port.number}", color = if (port.poe) Color(0xFF34C759) else accent, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(port.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    port.speedMbps?.let { "$it Mbps" },
                    if (port.online) stringResource(R.string.unifi_online) else stringResource(R.string.unifi_offline),
                    port.poePowerWatts?.let { "PoE ${String.format(Locale.US, "%.1f", it)}W" }
                ).joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Text(formatRate((port.rxBps ?: 0.0) + (port.txBps ?: 0.0)), color = accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun RadiosCard(radios: List<UnifiRadio>, accent: Color) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.unifi_radios), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            radios.forEach { radio ->
                RadioRow(radio = radio, accent = accent)
            }
        }
    }
}

@Composable
private fun RadioRow(radio: UnifiRadio, accent: Color) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = RoundedCornerShape(10.dp), color = accent.copy(alpha = 0.12f), modifier = Modifier.size(42.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Wifi, contentDescription = null, tint = accent, modifier = Modifier.size(24.dp))
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(radio.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(
                    radio.band,
                    radio.channel?.let { "Ch. $it" },
                    radio.width,
                    radio.txPower
                ).joinToString(" • "),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        radio.utilizationPercent?.let {
            Text("${it.toInt()}%", color = utilizationColor(it), fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun InfoCard(device: UnifiDevice) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("IP", device.ipAddress)
            InfoRow("MAC", device.macAddress)
            InfoRow(stringResource(R.string.unifi_model), device.model)
            InfoRow(stringResource(R.string.unifi_firmware), device.firmware)
            InfoRow(stringResource(R.string.unifi_serial), device.serial)
            InfoRow(stringResource(R.string.unifi_uplink), device.uplinkName)
        }
    }
}

@Composable
private fun InfoCard(client: UnifiClient) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(22.dp)) {
        Column(modifier = Modifier.fillMaxWidth().padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            InfoRow("IP", client.ipAddress)
            InfoRow("MAC", client.macAddress)
            InfoRow(stringResource(R.string.unifi_networks), client.networkName)
            InfoRow(stringResource(R.string.unifi_uplink), client.accessPointName)
            InfoRow(stringResource(R.string.unifi_usage), formatUsage(client))
        }
    }
}

@Composable
private fun InfoRow(label: String, value: String?) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
        Text(value?.takeIf { it.isNotBlank() } ?: "N/A", fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun EmptyCard(message: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow), shape = RoundedCornerShape(18.dp)) {
        Text(message, modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(36.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(message, color = MaterialTheme.colorScheme.error)
        Spacer(modifier = Modifier.height(12.dp))
        Button(onClick = onRetry) {
            Text(stringResource(R.string.unifi_retry))
        }
    }
}

private enum class UnifiDeviceFilter(val labelRes: Int) {
    ALL(R.string.unifi_all),
    ONLINE(R.string.unifi_online),
    OFFLINE(R.string.unifi_offline),
    GATEWAYS(R.string.unifi_gateways),
    SWITCHES(R.string.unifi_switches),
    APS(R.string.unifi_aps),
    CAMERAS(R.string.unifi_cameras);

    fun matches(device: UnifiDevice): Boolean {
        return when (this) {
            ALL -> true
            ONLINE -> device.online
            OFFLINE -> !device.online
            GATEWAYS -> device.kind == UnifiDeviceKind.GATEWAY
            SWITCHES -> device.kind == UnifiDeviceKind.SWITCH
            APS -> device.kind == UnifiDeviceKind.ACCESS_POINT
            CAMERAS -> device.kind == UnifiDeviceKind.CAMERA
        }
    }
}

private enum class UnifiClientFilter(val labelRes: Int) {
    ALL(R.string.unifi_all),
    WIFI(R.string.unifi_wifi_clients),
    WIRED(R.string.unifi_wired_clients),
    GUEST(R.string.unifi_guest);

    fun matches(client: UnifiClient): Boolean {
        return when (this) {
            ALL -> true
            WIFI -> client.isWireless
            WIRED -> client.isWired
            GUEST -> client.isGuestUnauthorized
        }
    }
}

private enum class UnifiDeviceKind {
    GATEWAY,
    SWITCH,
    ACCESS_POINT,
    CAMERA,
    STORAGE,
    BRIDGE,
    ACCESS,
    PHONE,
    OTHER
}

private val UnifiDevice.kind: UnifiDeviceKind
    get() {
        val text = listOfNotNull(type, model, name).joinToString(" ").lowercase()
        return when {
            text.contains("camera") || text.contains("uvc") || text.contains("g4") || text.contains("g5") || text.contains("g6") -> UnifiDeviceKind.CAMERA
            text.contains("nvr") || text.contains("cloudkey") || text.contains("cloud key") || text.contains("storage") -> UnifiDeviceKind.STORAGE
            text.contains("door") || text.contains("access") || text.contains("reader") || text.contains("hub") -> UnifiDeviceKind.ACCESS
            text.contains("talk") || text.contains("phone") -> UnifiDeviceKind.PHONE
            text.contains("bridge") || text.contains("air") || text.contains("mesh") -> UnifiDeviceKind.BRIDGE
            text.contains("switch") || text.contains("usw") -> UnifiDeviceKind.SWITCH
            text.contains("gateway") || text.contains("udm") || text.contains("uxg") || text.contains("usg") || text.contains("ucg") -> UnifiDeviceKind.GATEWAY
            text.contains("access point") || text.contains("uap") || text.contains("wifi") || Regex("\\bu[67]-").containsMatchIn(text) -> UnifiDeviceKind.ACCESS_POINT
            else -> UnifiDeviceKind.OTHER
        }
    }

private val UnifiDevice.isAccessPoint: Boolean
    get() = kind == UnifiDeviceKind.ACCESS_POINT

private val UnifiDevice.isSwitch: Boolean
    get() = kind == UnifiDeviceKind.SWITCH

private val UnifiDevice.isGateway: Boolean
    get() = kind == UnifiDeviceKind.GATEWAY

private fun deviceTotal(data: UnifiDashboardData): Int {
    return data.devices.size.takeIf { it > 0 } ?: data.sites.sumOf { it.deviceCount }
}

private fun healthLabel(raw: String?): String {
    if (raw.isNullOrBlank()) return "Online"
    return raw.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

private fun formatMegabits(value: Double?): String {
    if (value == null || !value.isFinite()) return "N/A"
    return String.format(Locale.US, "%.1f Mbps", value)
}

private fun formatRate(bytesPerSecond: Double): String {
    if (!bytesPerSecond.isFinite() || bytesPerSecond <= 0.0) return "0 B/s"
    val units = listOf("B/s", "KB/s", "MB/s", "GB/s")
    var value = bytesPerSecond
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return if (index == 0) {
        "${value.toInt()} ${units[index]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

private fun formatUsage(client: UnifiClient): String? {
    val rx = client.rxBytes ?: 0.0
    val tx = client.txBytes ?: 0.0
    val total = rx + tx
    if (!total.isFinite() || total <= 0.0) return null
    return "DL ${formatBytes(rx)} • UL ${formatBytes(tx)}"
}

private fun formatBytes(bytes: Double): String {
    if (!bytes.isFinite() || bytes <= 0.0) return "0 B"
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var value = bytes
    var index = 0
    while (value >= 1024.0 && index < units.lastIndex) {
        value /= 1024.0
        index += 1
    }
    return if (index == 0) {
        "${value.toInt()} ${units[index]}"
    } else {
        String.format(Locale.US, "%.1f %s", value, units[index])
    }
}

private fun signalColor(signalDbm: Int): Color {
    return when {
        signalDbm >= -65 -> Color(0xFF34C759)
        signalDbm >= -75 -> Color(0xFFFF9500)
        else -> Color(0xFFFF3B30)
    }
}

private fun utilizationColor(percent: Double): Color {
    return when {
        percent < 60.0 -> Color(0xFF34C759)
        percent < 80.0 -> Color(0xFFFF9500)
        else -> Color(0xFFFF3B30)
    }
}

@SuppressLint("DiscouragedApi")
private fun unifiDeviceAssetResId(context: android.content.Context, device: UnifiDevice): Int {
    val resourceNames = unifiDeviceAssetCandidates(device)
    return resourceNames.firstNotNullOfOrNull { name ->
        context.resources.getIdentifier(name, "drawable", context.packageName).takeIf { it != 0 }
    } ?: 0
}

private fun unifiDeviceAssetCandidates(device: UnifiDevice): List<String> {
    val seeds = listOfNotNull(device.model, device.name)
        .flatMap { seed -> modelAliases(seed) }
        .map(::resourceSlug)
        .filter { it.isNotBlank() }

    val candidates = linkedSetOf<String>()
    seeds.forEach { slug ->
        candidates += "unifi_device_$slug"
        candidates += "ubiquiti_device_$slug"
        translatedWebSlugs(slug).forEach { webSlug ->
            candidates += "ubiquiti_device_$webSlug"
        }
    }

    when (device.kind) {
        UnifiDeviceKind.ACCESS_POINT -> {
            candidates += "ubiquiti_device_access_point_u7_pro"
            candidates += "unifi_device_u7_pro"
        }
        UnifiDeviceKind.SWITCH -> {
            candidates += "ubiquiti_device_switch_24_poe"
            candidates += "unifi_device_usw_24_poe"
        }
        UnifiDeviceKind.GATEWAY -> {
            candidates += "ubiquiti_device_cloud_gateway_max"
            candidates += "unifi_device_ucg_max"
        }
        UnifiDeviceKind.CAMERA -> candidates += "unifi_device_uvc_g5_bullet"
        UnifiDeviceKind.STORAGE -> candidates += "unifi_device_unvr"
        UnifiDeviceKind.ACCESS -> candidates += "unifi_device_ua_hub"
        UnifiDeviceKind.BRIDGE -> candidates += "ubiquiti_device_building_to_building_bridge"
        UnifiDeviceKind.PHONE -> candidates += "unifi_device_utp_touch"
        UnifiDeviceKind.OTHER -> Unit
    }
    return candidates.toList()
}

private fun modelAliases(raw: String): List<String> {
    val trimmed = raw.trim()
    val aliases = linkedSetOf(trimmed)
    val canonical = resourceSlug(trimmed).replace('_', '-')
    DEVICE_MODEL_ALIASES[canonical]?.let { aliases += it }
    aliases += trimmed.replace(" ", "-")
    aliases += trimmed.replace("UniFi ", "", ignoreCase = true)
    aliases += trimmed.replace("Dream Machine", "UDM", ignoreCase = true)
    aliases += trimmed.replace("Cloud Gateway", "UCG", ignoreCase = true)
    aliases += trimmed.replace("Switch", "USW", ignoreCase = true)
    aliases += trimmed.replace("Access Point", "UAP", ignoreCase = true)
    return aliases.toList()
}

private fun resourceSlug(raw: String): String {
    return raw.lowercase()
        .replace("+", "plus")
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .replace(Regex("_+"), "_")
}

private fun translatedWebSlugs(resourceSlug: String): List<String> {
    val slug = resourceSlug.replace('_', '-')
    val translated = linkedSetOf<String>()
    translated += slug
    when {
        slug.startsWith("uap-") -> translated += "access-point-${slug.removePrefix("uap-")}"
        slug.startsWith("u6-") || slug.startsWith("u7-") || slug.startsWith("e7") -> translated += "access-point-$slug"
        slug.startsWith("usw-") -> translated += "switch-${slug.removePrefix("usw-")}"
        slug.startsWith("udm-") -> translated += "dream-machine-${slug.removePrefix("udm-")}"
        slug.startsWith("ucg-") -> translated += "cloud-gateway-${slug.removePrefix("ucg-")}"
        slug == "ux" -> {
            translated += "unifi-express-7"
            translated += "unifi-travel-router"
        }
    }
    when (slug) {
        "uap-ac-m" -> translated += "access-point-ac-mesh"
        "uap-ac-m-pro" -> translated += "access-point-ac-mesh-pro"
        "u7-wall" -> translated += "access-point-u7-pro-wall"
        "u7-pro-xg-wall" -> translated += "access-point-u7-pro-xg-wall"
        "ux7" -> translated += "dream-router-7"
    }
    return translated.map(::resourceSlug)
}

private val DEVICE_MODEL_ALIASES = mapOf(
    "u7-wall" to "u7-pro-wall",
    "usw-24-poe-95w" to "usw-24-poe",
    "usw-lite-16-poe-45w" to "usw-lite-16-poe",
    "usw-16-poe-42w" to "usw-16-poe",
    "unifi-express" to "ux",
    "uap-ac-m" to "uap-ac-mesh",
    "udm-se" to "dream-machine-special-edition",
    "ucg-max" to "cloud-gateway-max",
    "ucg-ultra" to "cloud-gateway-ultra",
    "ucg-fiber" to "cloud-gateway-fiber",
    "ux7" to "dream-router-7"
)
