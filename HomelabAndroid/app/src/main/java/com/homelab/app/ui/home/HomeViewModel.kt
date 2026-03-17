package com.homelab.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.GiteaRepository
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.NginxProxyManagerRepository
import com.homelab.app.data.repository.AdGuardHomeRepository
import com.homelab.app.data.repository.PiholeRepository
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val servicesRepository: ServicesRepository,
    private val portainerRepository: PortainerRepository,
    private val piholeRepository: PiholeRepository,
    private val adGuardHomeRepository: AdGuardHomeRepository,
    private val beszelRepository: BeszelRepository,
    private val giteaRepository: GiteaRepository,
    private val nginxProxyManagerRepository: NginxProxyManagerRepository,
    private val localPreferencesRepository: LocalPreferencesRepository
) : ViewModel() {

    data class PortainerSummary(val running: Int, val total: Int)
    data class PiholeSummary(val totalQueries: Int)
    data class AdGuardSummary(val totalQueries: Long)
    data class BeszelSummary(val online: Int, val total: Int)
    data class GiteaSummary(val totalRepos: Int)
    data class NpmSummary(val proxyHosts: Int, val total: Int)

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

    val connectedCount: StateFlow<Int> = servicesRepository.allInstances
        .map { it.size }
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

    private val _beszelSummary = MutableStateFlow<BeszelSummary?>(null)
    val beszelSummary: StateFlow<BeszelSummary?> = _beszelSummary

    private val _giteaSummary = MutableStateFlow<GiteaSummary?>(null)
    val giteaSummary: StateFlow<GiteaSummary?> = _giteaSummary

    private val _npmSummary = MutableStateFlow<NpmSummary?>(null)
    val npmSummary: StateFlow<NpmSummary?> = _npmSummary

    /** Per-instance summary data, keyed by instance ID. */
    private val _instanceSummaries = MutableStateFlow<Map<String, InstanceSummary>>(emptyMap())
    val instanceSummaries: StateFlow<Map<String, InstanceSummary>> = _instanceSummaries

    private val _summaryLoading = MutableStateFlow(false)
    val summaryLoading: StateFlow<Boolean> = _summaryLoading

    private var summaryJob: Job? = null

    fun checkReachability(instanceId: String) {
        viewModelScope.launch {
            servicesRepository.checkReachability(instanceId)
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
        val instancesMap = instancesByType.value
        summaryJob = viewModelScope.launch {
            _summaryLoading.value = true
            try {
                val newSummaries = mutableMapOf<String, InstanceSummary>()

                for (type in ServiceType.entries) {
                    if (type == ServiceType.UNKNOWN) continue
                    val instances = instancesMap[type].orEmpty()
                    for (instance in instances) {
                        try {
                            val summary = fetchInstanceSummary(type, instance.id)
                            if (summary != null) {
                                newSummaries[instance.id] = summary
                            }
                        } catch (error: Exception) {
                            Log.e("HomeViewModel", "${type.name} summary error for ${instance.id}: ${error.message}")
                        }
                    }
                }

                _instanceSummaries.value = newSummaries

                // Also update legacy per-type summaries from preferred instances
                val preferredIds = preferredInstanceIdByType.value
                for (type in ServiceType.entries) {
                    if (type == ServiceType.UNKNOWN) continue
                    val prefId = preferredIds[type] ?: instancesMap[type]?.firstOrNull()?.id ?: continue
                    val summary = newSummaries[prefId]
                    updateLegacySummary(type, prefId, instancesMap)
                }
            } finally {
                _summaryLoading.value = false
                summaryJob = null
            }
        }
    }

    private suspend fun fetchInstanceSummary(type: ServiceType, instanceId: String): InstanceSummary? {
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
            ServiceType.NGINX_PROXY_MANAGER -> {
                val report = nginxProxyManagerRepository.getHostReport(instanceId)
                _npmSummary.value = NpmSummary(report.proxy, report.total)
                InstanceSummary("${report.proxy}", "/ ${report.total}", "proxy_hosts")
            }
            else -> null
        }
    }

    private suspend fun updateLegacySummary(type: ServiceType, prefId: String, instancesMap: Map<ServiceType, List<ServiceInstance>>) {
        // Legacy summaries already updated inside fetchInstanceSummary
        // This is a no-op placeholder for compatibility
    }

    fun moveService(serviceType: ServiceType, offset: Int) {
        viewModelScope.launch {
            localPreferencesRepository.moveService(serviceType, offset)
        }
    }
}
