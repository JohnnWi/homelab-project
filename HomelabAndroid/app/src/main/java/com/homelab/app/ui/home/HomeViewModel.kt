package com.homelab.app.ui.home

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.GiteaRepository
import com.homelab.app.data.repository.PiholeRepository
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val servicesRepository: ServicesRepository,
    private val portainerRepository: PortainerRepository,
    private val piholeRepository: PiholeRepository,
    private val beszelRepository: BeszelRepository,
    private val giteaRepository: GiteaRepository,
    private val localPreferencesRepository: LocalPreferencesRepository
) : ViewModel() {

    data class PortainerSummary(val running: Int, val total: Int)
    data class PiholeSummary(val totalQueries: Int)
    data class BeszelSummary(val online: Int, val total: Int)
    data class GiteaSummary(val totalRepos: Int)

    val reachability: StateFlow<Map<ServiceType, Boolean?>> = servicesRepository.reachability
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pinging: StateFlow<Map<ServiceType, Boolean>> = servicesRepository.pinging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val connectionStatus: StateFlow<Map<ServiceType, Boolean>> = servicesRepository.allConnections
        .map { map -> map.mapValues { it.value != null } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val connectedCount: StateFlow<Int> = connectionStatus
        .map { map -> map.values.count { it } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val hiddenServices: StateFlow<Set<String>> = localPreferencesRepository.hiddenServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun checkReachability(type: ServiceType) {
        viewModelScope.launch {
            servicesRepository.checkReachability(type)
        }
    }

    fun checkAllReachability() {
        viewModelScope.launch {
            ServiceType.entries.filter { it != ServiceType.UNKNOWN }.forEach {
                servicesRepository.checkReachability(it)
            }
        }
    }

    // --- Dashboard Summary ---
    private val _portainerSummary = kotlinx.coroutines.flow.MutableStateFlow<PortainerSummary?>(null)
    val portainerSummary: StateFlow<PortainerSummary?> = _portainerSummary

    private val _piholeSummary = kotlinx.coroutines.flow.MutableStateFlow<PiholeSummary?>(null)
    val piholeSummary: StateFlow<PiholeSummary?> = _piholeSummary

    private val _beszelSummary = kotlinx.coroutines.flow.MutableStateFlow<BeszelSummary?>(null)
    val beszelSummary: StateFlow<BeszelSummary?> = _beszelSummary

    private val _giteaSummary = kotlinx.coroutines.flow.MutableStateFlow<GiteaSummary?>(null)
    val giteaSummary: StateFlow<GiteaSummary?> = _giteaSummary

    fun fetchSummaryData() {
        Log.d("HomeViewModel", "Fetching summary data...")
        val conn = connectionStatus.value
        val reach = reachability.value

        viewModelScope.launch {
            if (conn[ServiceType.PORTAINER] == true && reach[ServiceType.PORTAINER] != false) {
                try {
                    val endpoints = portainerRepository.getEndpoints()
                    val first = endpoints.firstOrNull()
                    if (first != null) {
                        val containers = portainerRepository.getContainers(first.id)
                        val running = containers.count { it.state == "running" || it.status.contains("Up") }
                        _portainerSummary.value = PortainerSummary(running, containers.size)
                    }
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Portainer summary error: ${e.message}")
                }
            } else {
                _portainerSummary.value = null
            }

            if (conn[ServiceType.PIHOLE] == true && reach[ServiceType.PIHOLE] != false) {
                try {
                    val stats = piholeRepository.getStats()
                    _piholeSummary.value = PiholeSummary(stats.queries.total)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Pihole summary error: ${e.message}")
                }
            } else {
                _piholeSummary.value = null
            }

            if (conn[ServiceType.BESZEL] == true && reach[ServiceType.BESZEL] != false) {
                try {
                    val systems = beszelRepository.getSystems()
                    val online = systems.count { it.isOnline }
                    _beszelSummary.value = BeszelSummary(online, systems.size)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Beszel summary error: ${e.message}")
                }
            } else {
                _beszelSummary.value = null
            }

            if (conn[ServiceType.GITEA] == true && reach[ServiceType.GITEA] != false) {
                try {
                    val repos = giteaRepository.getUserRepos(1, 100)
                    _giteaSummary.value = GiteaSummary(repos.size)
                } catch (e: Exception) {
                    Log.e("HomeViewModel", "Gitea summary error: ${e.message}")
                }
            } else {
                _giteaSummary.value = null
            }
        }
    }
}
