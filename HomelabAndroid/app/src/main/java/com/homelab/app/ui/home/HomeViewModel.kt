package com.homelab.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.DockhandRepository
import com.homelab.app.data.repository.DockmonRepository
import com.homelab.app.data.repository.GiteaRepository
import com.homelab.app.data.repository.LinuxUpdateRepository
import com.homelab.app.data.repository.CraftyRepository
import com.homelab.app.data.repository.JellystatRepository
import com.homelab.app.data.repository.KomodoRepository
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.MaltrailRepository
import com.homelab.app.data.repository.NginxProxyManagerRepository
import com.homelab.app.data.repository.HealthchecksRepository
import com.homelab.app.data.repository.PatchmonRepository
import com.homelab.app.data.repository.PangolinRepository
import com.homelab.app.data.repository.PlexRepository
import com.homelab.app.data.repository.ProxmoxRepository
import com.homelab.app.data.repository.PterodactylRepository
import com.homelab.app.data.repository.CalagopusRepository
import com.homelab.app.data.repository.AdGuardHomeRepository
import com.homelab.app.data.repository.PiholeRepository
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.TechnitiumRepository
import com.homelab.app.data.repository.UptimeKumaRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.Locale
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.floor

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val servicesRepository: ServicesRepository,
    private val portainerRepository: PortainerRepository,
    private val piholeRepository: PiholeRepository,
    private val adGuardHomeRepository: AdGuardHomeRepository,
    private val jellystatRepository: JellystatRepository,
    private val beszelRepository: BeszelRepository,
    private val giteaRepository: GiteaRepository,
    private val linuxUpdateRepository: LinuxUpdateRepository,
    private val technitiumRepository: TechnitiumRepository,
    private val dockhandRepository: DockhandRepository,
    private val dockmonRepository: DockmonRepository,
    private val komodoRepository: KomodoRepository,
    private val maltrailRepository: MaltrailRepository,
    private val uptimeKumaRepository: UptimeKumaRepository,
    private val craftyRepository: CraftyRepository,
    private val nginxProxyManagerRepository: NginxProxyManagerRepository,
    private val healthchecksRepository: HealthchecksRepository,
    private val patchmonRepository: PatchmonRepository,
    private val plexRepository: PlexRepository,
    private val proxmoxRepository: ProxmoxRepository,
    private val pangolinRepository: PangolinRepository,
    private val wakapiRepository: com.homelab.app.data.repository.WakapiRepository,
    private val pterodactylRepository: PterodactylRepository,
    private val calagopusRepository: CalagopusRepository,
    private val localPreferencesRepository: LocalPreferencesRepository
) : ViewModel() {

    /** Summary info for a single instance card. */
    data class InstanceSummary(val value: String, val subValue: String?, val label: String)

    val reachability: StateFlow<Map<String, Boolean?>> = servicesRepository.reachability
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pinging: StateFlow<Map<String, Boolean>> = servicesRepository.pinging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val instancesByType: StateFlow<Map<ServiceType, List<ServiceInstance>>> = servicesRepository.instancesByType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val preferredInstancesByType: StateFlow<Map<ServiceType, ServiceInstance?>> = servicesRepository.preferredInstancesByType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val preferredInstanceIdByType: StateFlow<Map<ServiceType, String?>> = servicesRepository.preferredInstanceIdByType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val connectionStatus: StateFlow<Map<ServiceType, Boolean>> = instancesByType
        .map { grouped -> grouped.mapValues { it.value.isNotEmpty() } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val connectedCount: StateFlow<Int> = instancesByType
        .map { grouped -> ServiceType.homeTypes.sumOf { grouped[it].orEmpty().size } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val isTailscaleConnected: StateFlow<Boolean> = servicesRepository.isTailscaleConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val hiddenServices: StateFlow<Set<String>> = localPreferencesRepository.hiddenServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val serviceOrder: StateFlow<List<ServiceType>> = localPreferencesRepository.serviceOrder
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ServiceType.entries.filter { it != ServiceType.UNKNOWN }
        )

    /** Per-instance summary data, keyed by instance ID. */
    private val _instanceSummaries = MutableStateFlow<Map<String, InstanceSummary>>(emptyMap())
    val instanceSummaries: StateFlow<Map<String, InstanceSummary>> = _instanceSummaries

    private val _summaryLoadingIds = MutableStateFlow<Set<String>>(emptySet())
    val summaryLoadingIds: StateFlow<Set<String>> = _summaryLoadingIds

    private val _refreshingInstanceIds = MutableStateFlow<Set<String>>(emptySet())
    val refreshingInstanceIds: StateFlow<Set<String>> = _refreshingInstanceIds

    private var summaryJob: Job? = null
    private var homeRefreshJob: Job? = null

    fun checkReachability(instanceId: String, force: Boolean = false) {
        viewModelScope.launch {
            servicesRepository.checkReachability(instanceId, force = force)
        }
    }

    fun checkAllReachability() {
        viewModelScope.launch {
            servicesRepository.checkAllReachability()
        }
    }

    fun fetchSummaryData() {
        if (summaryJob?.isActive == true) return
        Log.d("HomeViewModel", "Fetching summary data...")
        summaryJob = viewModelScope.launch {
            try {
                fetchSummaryDataInternal(instancesByType.value)
            } finally {
                summaryJob = null
            }
        }
    }

    fun refreshHome() {
        if (homeRefreshJob?.isActive == true) return

        homeRefreshJob = viewModelScope.launch {
            val instancesList: List<ServiceInstance> = servicesRepository.allInstances.firstOrNull().orEmpty()
            val instancesMap: Map<ServiceType, List<ServiceInstance>> = ServiceType.homeTypes.associateWith { type ->
                instancesList.filter { instance -> instance.type == type }
            }

            val targetIds = ServiceType.homeTypes
                .flatMap { type -> instancesMap[type].orEmpty() }
                .filter { it.id !in reachability.value } // Only show spinner for truly unknown statuses
                .map { it.id }
                .toSet()

            if (targetIds.isNotEmpty()) {
                _refreshingInstanceIds.value = _refreshingInstanceIds.value + targetIds
            }

            try {
                coroutineScope {
                    launch { servicesRepository.checkAllReachability() }
                    launch {
                        val currentSummaryJob = summaryJob
                        if (currentSummaryJob?.isActive == true) {
                            currentSummaryJob.join()
                        } else {
                            fetchSummaryDataInternal(instancesMap)
                        }
                    }
                }
            } finally {
                _refreshingInstanceIds.value = _refreshingInstanceIds.value - targetIds
                homeRefreshJob = null
            }
        }
    }

    private suspend fun fetchSummaryDataInternal(instancesMap: Map<ServiceType, List<ServiceInstance>>) {
        val targetInstances = ServiceType.homeTypes
            .flatMap { type -> instancesMap[type].orEmpty().map { instance -> type to instance } }
        val targetIds = targetInstances.map { (_, instance) -> instance.id }.toSet()
        val coldStartIds = targetInstances
            .mapNotNull { (_, instance) ->
                instance.id.takeIf { id -> _instanceSummaries.value[id] == null }
            }
            .toSet()
        if (coldStartIds.isNotEmpty()) {
            _summaryLoadingIds.value = _summaryLoadingIds.value + coldStartIds
        }

        try {
            val summaryResults = coroutineScope {
                targetInstances.map { (type, instance) ->
                    async {
                        try {
                            withTimeoutOrNull(10_000L) {
                                runCatching {
                                    fetchInstanceSummary(type, instance)?.also {
                                        servicesRepository.markInstanceReachable(instance.id)
                                    }
                                }.onFailure { error ->
                                    Log.e("HomeViewModel", "${type.name} summary error for ${instance.id}: ${error.message}")
                                }.getOrNull()?.let { instance.id to it }
                            }
                        } finally {
                            _summaryLoadingIds.value = _summaryLoadingIds.value - instance.id
                        }
                    }
                }
                    .awaitAll()
                    .filterNotNull()
            }

            val newSummaries = summaryResults.toMap()
            _instanceSummaries.value = newSummaries
        } finally {
            _summaryLoadingIds.value = _summaryLoadingIds.value - targetIds
        }
    }

    private suspend fun fetchInstanceSummary(type: ServiceType, instance: ServiceInstance): InstanceSummary? {
        val instanceId = instance.id
        return when (type) {
            ServiceType.PORTAINER -> {
                val endpoints = portainerRepository.getEndpoints(instanceId)
                if (endpoints.isEmpty()) return InstanceSummary("0", "/ 0", "containers")
                var running = 0
                var total = 0
                endpoints.forEach { endpoint ->
                    val containers = runCatching { portainerRepository.getContainers(instanceId, endpoint.id) }
                        .getOrDefault(emptyList())
                    total += containers.size
                    running += containers.count { it.state == "running" || it.status.contains("Up") }
                }
                InstanceSummary("$running", "/ $total", "containers")
            }
            ServiceType.PIHOLE -> {
                val stats = piholeRepository.getStats(instanceId)
                val formatted = java.text.NumberFormat.getInstance().format(stats.queries.total)
                InstanceSummary(formatted, null, "total_queries")
            }
            ServiceType.ADGUARD_HOME -> {
                val stats = adGuardHomeRepository.getStats(instanceId)
                val formatted = java.text.NumberFormat.getInstance().format(stats.numDnsQueries)
                InstanceSummary(formatted, null, "adguard_total_queries")
            }
            ServiceType.JELLYSTAT -> {
                val summary = jellystatRepository.getWatchSummary(instanceId, 7)
                InstanceSummary(formatHours(summary.totalHours), null, "jellystat_watch_time")
            }
            ServiceType.BESZEL -> {
                val systems = beszelRepository.getSystems(instanceId)
                val online = systems.count { it.isOnline }
                InstanceSummary("$online", "/ ${systems.size}", "systems_online")
            }
            ServiceType.GITEA -> {
                val pageSize = 100
                var page = 1
                var total = 0
                while (true) {
                    val repos = giteaRepository.getUserRepos(instanceId, page, pageSize)
                    total += repos.size
                    if (repos.size < pageSize) break
                    page += 1
                }
                InstanceSummary("$total", null, "repos")
            }
            ServiceType.LINUX_UPDATE -> {
                val stats = linuxUpdateRepository.getDashboardStats(instanceId)
                InstanceSummary("${stats.upToDate}", "/ ${stats.total}", "linux_update_systems_up_to_date")
            }
            ServiceType.TECHNITIUM -> {
                val overview = technitiumRepository.getOverview(instanceId)
                val formattedBlocked = java.text.NumberFormat.getInstance().format(overview.totalBlocked)
                val formattedTotal = java.text.NumberFormat.getInstance().format(overview.totalQueries)
                InstanceSummary(formattedBlocked, "/ $formattedTotal", "technitium_blocked_queries")
            }
            ServiceType.DOCKHAND -> {
                val data = dockhandRepository.getDashboard(instanceId = instanceId, env = null)
                InstanceSummary("${data.stats.runningContainers}", "/ ${data.stats.totalContainers}", "dockhand_containers")
            }
            ServiceType.DOCKMON -> {
                val data = dockmonRepository.getSummary(instanceId)
                InstanceSummary("${data.runningContainers}", "/ ${data.containers.size}", "dockmon_containers")
            }
            ServiceType.KOMODO -> {
                val summary = komodoRepository.getSummary(instanceId)
                InstanceSummary("${summary.runningContainers}", "/ ${summary.totalContainers}", "komodo_containers")
            }
            ServiceType.MALTRAIL -> {
                val summary = maltrailRepository.getSummary(instanceId)
                val latest = java.text.NumberFormat.getInstance().format(summary.latestCount)
                val day = summary.latestDayLabel.takeIf { it.isNotBlank() }
                InstanceSummary(latest, day, "maltrail_findings")
            }
            ServiceType.UPTIME_KUMA -> {
                val summary = uptimeKumaRepository.getSummary(instanceId)
                InstanceSummary("${summary.upCount}", "/ ${summary.totalCount}", "uptime_kuma_monitors")
            }
            ServiceType.CRAFTY_CONTROLLER -> {
                val servers = craftyRepository.getServers(instanceId)
                val stats = servers.mapNotNull { server ->
                    runCatching { craftyRepository.getServerStats(instanceId, server.serverId) }.getOrNull()
                }
                val running = stats.count { it.running }
                InstanceSummary("$running", "/ ${servers.size}", "crafty_running_servers")
            }
            ServiceType.NGINX_PROXY_MANAGER -> {
                val report = nginxProxyManagerRepository.getHostReport(instanceId)
                InstanceSummary("${report.proxy}", "/ ${report.total}", "proxy_hosts")
            }
            ServiceType.PANGOLIN -> {
                val scopedOrgId = instance.username?.takeIf { it.isNotBlank() }
                val (sites, resources, clients) = pangolinRepository.getAggregateSummary(instanceId, scopedOrgId)
                InstanceSummary("$sites", "/ $clients", "pangolin_sites_clients")
            }
            ServiceType.HEALTHCHECKS -> {
                val checks = healthchecksRepository.listChecks(instanceId)
                val up = checks.count { it.status == "up" || it.status == "grace" }
                InstanceSummary("$up", "/ ${checks.size}", "checks")
            }
            ServiceType.PATCHMON -> {
                val hosts = patchmonRepository.getHosts(instanceId).hosts
                val active = hosts.count { it.status.equals("active", ignoreCase = true) }
                InstanceSummary("$active", "/ ${hosts.size}", "hosts")
            }
            ServiceType.WAKAPI -> {
                val summary = try {
                    wakapiRepository.getSummary(instanceId)
                } catch (e: Exception) {
                    null
                } ?: return null
                val grandTotal = summary.effectiveGrandTotal()
                val totalSeconds = grandTotal.totalSeconds ?: summary.inferredTotalSeconds()
                val totalHours = totalSeconds / 3600.0
                val time = formatHours(totalHours)
                InstanceSummary(time, null, "coded_today")
            }
            ServiceType.PLEX -> {
                val dashboard = plexRepository.getDashboard(instanceId)
                val formattedItems = java.text.NumberFormat.getInstance().format(dashboard.stats.totalItems)
                InstanceSummary(formattedItems, null, "plex_total_items")
            }
            ServiceType.PROXMOX -> {
                val nodes = proxmoxRepository.getNodes(instanceId)
                val onlineNodes = nodes.filter { it.isOnline }
                var totalRunning = 0
                var totalGuests = 0
                for (node in onlineNodes) {
                    val vms = runCatching { proxmoxRepository.getVMs(instanceId, node.node) }.getOrDefault(emptyList())
                    val lxcs = runCatching { proxmoxRepository.getLXCs(instanceId, node.node) }.getOrDefault(emptyList())
                    totalGuests += vms.size + lxcs.size
                    totalRunning += vms.count { it.isRunning } + lxcs.count { it.isRunning }
                }
                InstanceSummary("$totalRunning", "/ $totalGuests", "proxmox_guests_running")
            }
            ServiceType.PTERODACTYL -> {
                val servers = pterodactylRepository.getServers(instanceId)
                val running = servers.count { it.status == null && !it.isSuspended && !it.isInstalling }
                InstanceSummary("$running", "/ ${servers.size}", "pterodactyl_running_servers")
            }
            ServiceType.CALAGOPUS -> {
                val servers = calagopusRepository.getServers(instanceId)
                val running = servers.count { it.status == null && !it.isSuspended }
                InstanceSummary("$running", "/ ${servers.size}", "calagopus_running_servers")
            }
            else -> null
        }
    }

    private fun formatHours(value: Double): String {
        val locale = Locale.getDefault()
        if (value in 0.000001..0.999999) {
            val minutes = floor(value * 60.0).toInt()
            if (minutes <= 0) return "<1m"
            return "${minutes}m"
        }
        return when {
            value >= 100.0 -> String.format(locale, "%.0fh", value)
            value >= 10.0 -> String.format(locale, "%.1fh", value)
            else -> String.format(locale, "%.2fh", value)
        }
    }

    fun moveService(serviceType: ServiceType, offset: Int) {
        viewModelScope.launch {
            localPreferencesRepository.moveService(serviceType, offset)
        }
    }

    fun toggleServiceVisibility(serviceType: ServiceType) {
        viewModelScope.launch {
            localPreferencesRepository.toggleServiceVisibility(serviceType.name)
        }
    }
}
