package com.homelab.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.DockhandRepository
import com.homelab.app.data.repository.GiteaRepository
import com.homelab.app.data.repository.LinuxUpdateRepository
import com.homelab.app.data.repository.CraftyRepository
import com.homelab.app.data.repository.JellystatRepository
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.NginxProxyManagerRepository
import com.homelab.app.data.repository.HealthchecksRepository
import com.homelab.app.data.repository.PatchmonRepository
import com.homelab.app.data.repository.PangolinRepository
import com.homelab.app.data.repository.PlexRepository
import com.homelab.app.data.repository.AdGuardHomeRepository
import com.homelab.app.data.repository.PiholeRepository
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.TechnitiumRepository
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
    private val craftyRepository: CraftyRepository,
    private val nginxProxyManagerRepository: NginxProxyManagerRepository,
    private val healthchecksRepository: HealthchecksRepository,
    private val patchmonRepository: PatchmonRepository,
    private val plexRepository: PlexRepository,
    private val pangolinRepository: PangolinRepository,
    private val wakapiRepository: com.homelab.app.data.repository.WakapiRepository,
    private val localPreferencesRepository: LocalPreferencesRepository
) : ViewModel() {

    data class PortainerSummary(val running: Int, val total: Int)
    data class PiholeSummary(val totalQueries: Int)
    data class AdGuardSummary(val totalQueries: Long)
    data class JellystatSummary(val watchedHours: Double, val totalViews: Int)
    data class BeszelSummary(val online: Int, val total: Int)
    data class GiteaSummary(val totalRepos: Int)
    data class LinuxUpdateSummary(val upToDate: Int, val total: Int)
    data class TechnitiumSummary(val blocked: Int, val total: Int)
    data class DockhandSummary(val running: Int, val total: Int)
    data class CraftySummary(val running: Int, val total: Int)
    data class NpmSummary(val proxyHosts: Int, val total: Int)
    data class PangolinSummary(val sites: Int, val resources: Int, val clients: Int)
    data class HealthchecksSummary(val up: Int, val total: Int)
    data class PatchmonSummary(val active: Int, val total: Int)
    data class PlexSummary(val sessions: Int, val totalItems: Int)
    data class WakapiSummary(val totalCoding: String)

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

    // Legacy per-type summaries (kept for old DashboardSummary if still referenced)
    private val _portainerSummary = MutableStateFlow<PortainerSummary?>(null)
    val portainerSummary: StateFlow<PortainerSummary?> = _portainerSummary

    private val _piholeSummary = MutableStateFlow<PiholeSummary?>(null)
    val piholeSummary: StateFlow<PiholeSummary?> = _piholeSummary

    private val _adguardSummary = MutableStateFlow<AdGuardSummary?>(null)
    val adguardSummary: StateFlow<AdGuardSummary?> = _adguardSummary

    private val _jellystatSummary = MutableStateFlow<JellystatSummary?>(null)
    val jellystatSummary: StateFlow<JellystatSummary?> = _jellystatSummary

    private val _beszelSummary = MutableStateFlow<BeszelSummary?>(null)
    val beszelSummary: StateFlow<BeszelSummary?> = _beszelSummary

    private val _giteaSummary = MutableStateFlow<GiteaSummary?>(null)
    val giteaSummary: StateFlow<GiteaSummary?> = _giteaSummary

    private val _linuxUpdateSummary = MutableStateFlow<LinuxUpdateSummary?>(null)
    val linuxUpdateSummary: StateFlow<LinuxUpdateSummary?> = _linuxUpdateSummary

    private val _technitiumSummary = MutableStateFlow<TechnitiumSummary?>(null)
    val technitiumSummary: StateFlow<TechnitiumSummary?> = _technitiumSummary

    private val _dockhandSummary = MutableStateFlow<DockhandSummary?>(null)
    val dockhandSummary: StateFlow<DockhandSummary?> = _dockhandSummary

    private val _craftySummary = MutableStateFlow<CraftySummary?>(null)
    val craftySummary: StateFlow<CraftySummary?> = _craftySummary

    private val _npmSummary = MutableStateFlow<NpmSummary?>(null)
    val npmSummary: StateFlow<NpmSummary?> = _npmSummary

    private val _pangolinSummary = MutableStateFlow<PangolinSummary?>(null)
    val pangolinSummary: StateFlow<PangolinSummary?> = _pangolinSummary

    private val _healthchecksSummary = MutableStateFlow<HealthchecksSummary?>(null)
    val healthchecksSummary: StateFlow<HealthchecksSummary?> = _healthchecksSummary

    private val _patchmonSummary = MutableStateFlow<PatchmonSummary?>(null)
    val patchmonSummary: StateFlow<PatchmonSummary?> = _patchmonSummary

    private val _plexSummary = MutableStateFlow<PlexSummary?>(null)
    val plexSummary: StateFlow<PlexSummary?> = _plexSummary

    private val _wakapiSummary = MutableStateFlow<WakapiSummary?>(null)
    val wakapiSummary: StateFlow<WakapiSummary?> = _wakapiSummary

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

            val preferredIds = preferredInstanceIdByType.value
            for (type in ServiceType.homeTypes) {
                val prefId = preferredIds[type] ?: instancesMap[type]?.firstOrNull()?.id ?: continue
                updateLegacySummary(type, prefId, instancesMap)
            }
        } finally {
            _summaryLoadingIds.value = _summaryLoadingIds.value - targetIds
        }
    }

    private suspend fun fetchInstanceSummary(type: ServiceType, instance: ServiceInstance): InstanceSummary? {
        val instanceId = instance.id
        return when (type) {
            ServiceType.PORTAINER -> {
                val endpoints = portainerRepository.getEndpoints(instanceId)
                val first = endpoints.firstOrNull() ?: return InstanceSummary("0", "/ 0", "containers")
                val containers = portainerRepository.getContainers(instanceId, first.id)
                val running = containers.count { it.state == "running" || it.status.contains("Up") }
                _portainerSummary.value = PortainerSummary(running, containers.size)
                InstanceSummary("$running", "/ ${containers.size}", "containers")
            }
            ServiceType.PIHOLE -> {
                val stats = piholeRepository.getStats(instanceId)
                _piholeSummary.value = PiholeSummary(stats.queries.total)
                val formatted = java.text.NumberFormat.getInstance().format(stats.queries.total)
                InstanceSummary(formatted, null, "total_queries")
            }
            ServiceType.ADGUARD_HOME -> {
                val stats = adGuardHomeRepository.getStats(instanceId)
                _adguardSummary.value = AdGuardSummary(stats.numDnsQueries)
                val formatted = java.text.NumberFormat.getInstance().format(stats.numDnsQueries)
                InstanceSummary(formatted, null, "adguard_total_queries")
            }
            ServiceType.JELLYSTAT -> {
                val summary = jellystatRepository.getWatchSummary(instanceId, 7)
                _jellystatSummary.value = JellystatSummary(summary.totalHours, summary.totalViews)
                InstanceSummary(formatHours(summary.totalHours), null, "jellystat_watch_time")
            }
            ServiceType.BESZEL -> {
                val systems = beszelRepository.getSystems(instanceId)
                val online = systems.count { it.isOnline }
                _beszelSummary.value = BeszelSummary(online, systems.size)
                InstanceSummary("$online", "/ ${systems.size}", "systems_online")
            }
            ServiceType.GITEA -> {
                val repos = giteaRepository.getUserRepos(instanceId, 1, 100)
                _giteaSummary.value = GiteaSummary(repos.size)
                InstanceSummary("${repos.size}", null, "repos")
            }
            ServiceType.LINUX_UPDATE -> {
                val stats = linuxUpdateRepository.getDashboardStats(instanceId)
                _linuxUpdateSummary.value = LinuxUpdateSummary(
                    upToDate = stats.upToDate,
                    total = stats.total
                )
                InstanceSummary("${stats.upToDate}", "/ ${stats.total}", "linux_update_systems_up_to_date")
            }
            ServiceType.TECHNITIUM -> {
                val overview = technitiumRepository.getOverview(instanceId)
                _technitiumSummary.value = TechnitiumSummary(
                    blocked = overview.totalBlocked,
                    total = overview.totalQueries
                )
                val formattedBlocked = java.text.NumberFormat.getInstance().format(overview.totalBlocked)
                val formattedTotal = java.text.NumberFormat.getInstance().format(overview.totalQueries)
                InstanceSummary(formattedBlocked, "/ $formattedTotal", "technitium_blocked_queries")
            }
            ServiceType.DOCKHAND -> {
                val data = dockhandRepository.getDashboard(instanceId = instanceId, env = null)
                _dockhandSummary.value = DockhandSummary(
                    running = data.stats.runningContainers,
                    total = data.stats.totalContainers
                )
                InstanceSummary("${data.stats.runningContainers}", "/ ${data.stats.totalContainers}", "dockhand_containers")
            }
            ServiceType.CRAFTY_CONTROLLER -> {
                val servers = craftyRepository.getServers(instanceId)
                val stats = servers.mapNotNull { server ->
                    runCatching { craftyRepository.getServerStats(instanceId, server.serverId) }.getOrNull()
                }
                val running = stats.count { it.running }
                _craftySummary.value = CraftySummary(running = running, total = servers.size)
                InstanceSummary("$running", "/ ${servers.size}", "crafty_running_servers")
            }
            ServiceType.NGINX_PROXY_MANAGER -> {
                val report = nginxProxyManagerRepository.getHostReport(instanceId)
                _npmSummary.value = NpmSummary(report.proxy, report.total)
                InstanceSummary("${report.proxy}", "/ ${report.total}", "proxy_hosts")
            }
            ServiceType.PANGOLIN -> {
                val scopedOrgId = instance.username?.takeIf { it.isNotBlank() }
                val (sites, resources, clients) = pangolinRepository.getAggregateSummary(instanceId, scopedOrgId)
                _pangolinSummary.value = PangolinSummary(sites, resources, clients)
                InstanceSummary("$sites", "/ $clients", "pangolin_sites_clients")
            }
            ServiceType.HEALTHCHECKS -> {
                val checks = healthchecksRepository.listChecks(instanceId)
                val up = checks.count { it.status == "up" }
                _healthchecksSummary.value = HealthchecksSummary(up, checks.size)
                InstanceSummary("$up", "/ ${checks.size}", "checks")
            }
            ServiceType.PATCHMON -> {
                val hosts = patchmonRepository.getHosts(instanceId).hosts
                val active = hosts.count { it.status.equals("active", ignoreCase = true) }
                _patchmonSummary.value = PatchmonSummary(active, hosts.size)
                InstanceSummary("$active", "/ ${hosts.size}", "hosts")
            }
            ServiceType.WAKAPI -> {
                val summary = try {
                    wakapiRepository.getSummary(instanceId)
                } catch (e: Exception) {
                    null
                } ?: return null
                val grandTotal = summary.effectiveGrandTotal()
                val time = if (grandTotal.resolvedHours > 0) {
                    "${grandTotal.resolvedHours}h ${grandTotal.resolvedMinutes}m"
                } else {
                    "${grandTotal.resolvedMinutes}m"
                }
                _wakapiSummary.value = WakapiSummary(time)
                InstanceSummary(time, null, "coded_today")
            }
            ServiceType.PLEX -> {
                val dashboard = plexRepository.getDashboard(instanceId)
                val activeSessions = dashboard.activeSessions.size
                _plexSummary.value = PlexSummary(activeSessions, dashboard.stats.totalItems)
                
                val formattedItems = java.text.NumberFormat.getInstance().format(dashboard.stats.totalItems)
                InstanceSummary(formattedItems, null, "plex_total_items")
            }
            else -> null
        }
    }

    private suspend fun updateLegacySummary(type: ServiceType, prefId: String, instancesMap: Map<ServiceType, List<ServiceInstance>>) {
        // Legacy summaries already updated inside fetchInstanceSummary
        // This is a no-op placeholder for compatibility
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
