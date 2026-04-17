package com.homelab.app.ui.proxmox

import android.app.Application
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.homelab.app.R
import com.homelab.app.data.remote.dto.proxmox.*
import com.homelab.app.data.repository.ProxmoxRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProxmoxViewModel @Inject constructor(
    application: Application,
    private val proxmoxRepository: ProxmoxRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {

    private fun resolvedMessage(error: Throwable?): String {
        val message = error?.message?.trim().orEmpty()
        return if (message.isNotEmpty()) {
            message
        } else {
            getApplication<Application>().getString(R.string.error_unknown)
        }
    }

    private val prefs: SharedPreferences = application.getSharedPreferences(
        "proxmox_favorites",
        Context.MODE_PRIVATE
    )

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _favoriteIds = MutableStateFlow<Set<String>>(emptySet())
    val favoriteIds: StateFlow<Set<String>> = _favoriteIds.asStateFlow()

    val instances: StateFlow<List<ServiceInstance>> =
        servicesRepository.instancesByType
            .map { it[com.homelab.app.util.ServiceType.PROXMOX].orEmpty() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _uiState = MutableStateFlow<UiState<ProxmoxDashboardData>>(UiState.Idle)
    val uiState: StateFlow<UiState<ProxmoxDashboardData>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    // Node Detail State
    private val _nodeDetailState = MutableStateFlow<UiState<ProxmoxNodeDetailData>>(UiState.Idle)
    val nodeDetailState: StateFlow<UiState<ProxmoxNodeDetailData>> = _nodeDetailState.asStateFlow()

    // Guest Detail State
    private val _guestDetailState = MutableStateFlow<UiState<ProxmoxGuestDetailData>>(UiState.Idle)
    val guestDetailState: StateFlow<UiState<ProxmoxGuestDetailData>> = _guestDetailState.asStateFlow()

    // Storage Content State
    private val _storageContentState = MutableStateFlow<UiState<List<ProxmoxStorageContent>>>(UiState.Idle)
    val storageContentState: StateFlow<UiState<List<ProxmoxStorageContent>>> = _storageContentState.asStateFlow()

    // Task Log State
    private val _taskLogState = MutableStateFlow<UiState<List<ProxmoxTaskLogEntry>>>(UiState.Idle)
    val taskLogState: StateFlow<UiState<List<ProxmoxTaskLogEntry>>> = _taskLogState.asStateFlow()
    private val _taskStatusState = MutableStateFlow<UiState<ProxmoxTask>>(UiState.Idle)
    val taskStatusState: StateFlow<UiState<ProxmoxTask>> = _taskStatusState.asStateFlow()

    // Pool Detail State
    private val _poolDetailState = MutableStateFlow<UiState<ProxmoxPoolDetail>>(UiState.Idle)
    val poolDetailState: StateFlow<UiState<ProxmoxPoolDetail>> = _poolDetailState.asStateFlow()

    // Backup Jobs State
    private val _backupJobsState = MutableStateFlow<UiState<List<ProxmoxBackupJob>>>(UiState.Idle)
    val backupJobsState: StateFlow<UiState<List<ProxmoxBackupJob>>> = _backupJobsState.asStateFlow()

    // Firewall Rules State
    private val _firewallRulesState = MutableStateFlow<UiState<List<ProxmoxFirewallRule>>>(UiState.Idle)
    val firewallRulesState: StateFlow<UiState<List<ProxmoxFirewallRule>>> = _firewallRulesState.asStateFlow()

    // Firewall Options State
    private val _firewallOptionsState = MutableStateFlow<UiState<ProxmoxFirewallOptions>>(UiState.Idle)
    val firewallOptionsState: StateFlow<UiState<ProxmoxFirewallOptions>> = _firewallOptionsState.asStateFlow()

    // APT Updates State
    private val _aptUpdatesState = MutableStateFlow<UiState<List<ProxmoxAptPackage>>>(UiState.Idle)
    val aptUpdatesState: StateFlow<UiState<List<ProxmoxAptPackage>>> = _aptUpdatesState.asStateFlow()

    // HA Resources State
    private val _haResourcesState = MutableStateFlow<UiState<List<ProxmoxHAResource>>>(UiState.Idle)
    val haResourcesState: StateFlow<UiState<List<ProxmoxHAResource>>> = _haResourcesState.asStateFlow()

    // HA Groups State
    private val _haGroupsState = MutableStateFlow<UiState<List<ProxmoxHAGroup>>>(UiState.Idle)
    val haGroupsState: StateFlow<UiState<List<ProxmoxHAGroup>>> = _haGroupsState.asStateFlow()

    // Cluster Resources State
    private val _clusterResourcesState = MutableStateFlow<UiState<List<ProxmoxClusterResource>>>(UiState.Idle)
    val clusterResourcesState: StateFlow<UiState<List<ProxmoxClusterResource>>> = _clusterResourcesState.asStateFlow()

    // Ceph Status State
    private val _cephStatusState = MutableStateFlow<UiState<ProxmoxCephStatus>>(UiState.Idle)
    val cephStatusState: StateFlow<UiState<ProxmoxCephStatus>> = _cephStatusState.asStateFlow()

    // Replication Jobs State
    private val _replicationJobsState = MutableStateFlow<UiState<List<ProxmoxReplicationJob>>>(UiState.Idle)
    val replicationJobsState: StateFlow<UiState<List<ProxmoxReplicationJob>>> = _replicationJobsState.asStateFlow()

    // Clone/Migrate Result State
    private val _actionResultState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val actionResultState: StateFlow<UiState<String>> = _actionResultState.asStateFlow()

    // ISO List State
    private val _isoListState = MutableStateFlow<UiState<List<ProxmoxStorageIso>>>(UiState.Idle)
    val isoListState: StateFlow<UiState<List<ProxmoxStorageIso>>> = _isoListState.asStateFlow()

    // Journal State
    private val _journalState = MutableStateFlow<UiState<List<ProxmoxJournalLine>>>(UiState.Idle)
    val journalState: StateFlow<UiState<List<ProxmoxJournalLine>>> = _journalState.asStateFlow()

    // Next VMID State
    private val _nextVmidState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val nextVmidState: StateFlow<UiState<String>> = _nextVmidState.asStateFlow()

    // Guest Create Result State
    private val _guestCreateResultState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val guestCreateResultState: StateFlow<UiState<String>> = _guestCreateResultState.asStateFlow()

    // Network State
    private val _networkState = MutableStateFlow<UiState<List<ProxmoxNetworkInterface>>>(UiState.Idle)
    val networkState: StateFlow<UiState<List<ProxmoxNetworkInterface>>> = _networkState.asStateFlow()

    // VNC Ticket State
    private val _vncTicketState = MutableStateFlow<UiState<ProxmoxVncTicketData>>(UiState.Idle)
    val vncTicketState: StateFlow<UiState<ProxmoxVncTicketData>> = _vncTicketState.asStateFlow()

    // Global Tasks State
    private val _globalTasksState = MutableStateFlow<UiState<List<ProxmoxTask>>>(UiState.Idle)
    val globalTasksState: StateFlow<UiState<List<ProxmoxTask>>> = _globalTasksState.asStateFlow()

    private val _isGlobalTasksRefreshing = MutableStateFlow(false)
    val isGlobalTasksRefreshing: StateFlow<Boolean> = _isGlobalTasksRefreshing.asStateFlow()

    // Guest Config State
    private val _guestConfigState = MutableStateFlow<UiState<Map<String, String>>>(UiState.Idle)
    val guestConfigState: StateFlow<UiState<Map<String, String>>> = _guestConfigState.asStateFlow()

    // Nodes State (for Guest Create)
    private val _nodesState = MutableStateFlow<UiState<List<String>>>(UiState.Idle)
    val nodesState: StateFlow<UiState<List<String>>> = _nodesState.asStateFlow()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        val saved = prefs.getString("proxmox_favorites_${instanceId}", "") ?: ""
        _favoriteIds.value = saved.split(",").filter { it.isNotBlank() }.toSet()
    }

    fun toggleFavorite(node: String, vmid: Int, isQemu: Boolean) {
        val id = "${node}-${if (isQemu) "vm" else "lxc"}-${vmid}"
        val current = _favoriteIds.value.toMutableSet()
        if (id in current) {
            current.remove(id)
        } else {
            current.add(id)
        }
        _favoriteIds.value = current
        prefs.edit().putString("proxmox_favorites_${instanceId}", current.joinToString(",")).apply()
    }

    fun isFavorite(node: String, vmid: Int, isQemu: Boolean): Boolean {
        val id = "${node}-${if (isQemu) "vm" else "lxc"}-${vmid}"
        return id in _favoriteIds.value
    }

    fun fetchAll() {
        viewModelScope.launch {
            fetchAllInternal()
        }
    }

    fun pullToRefresh() {
        viewModelScope.launch {
            _isRefreshing.value = true
            fetchAllInternal()
            _isRefreshing.value = false
        }
    }

    private suspend fun fetchAllInternal() {
        _uiState.value = UiState.Loading
        try {
            val version = proxmoxRepository.getVersion(instanceId)
            val nodes = proxmoxRepository.getNodes(instanceId)
            val vmsByNode = mutableMapOf<String, List<ProxmoxVM>>()
            val lxcsByNode = mutableMapOf<String, List<ProxmoxLXC>>()
            val storageByNode = mutableMapOf<String, List<ProxmoxStorage>>()

            nodes.forEach { node ->
                try { vmsByNode[node.node] = proxmoxRepository.getVMs(instanceId, node.node) } catch (_: Exception) {}
                try { lxcsByNode[node.node] = proxmoxRepository.getLXCs(instanceId, node.node) } catch (_: Exception) {}
                try { storageByNode[node.node] = proxmoxRepository.getStorage(instanceId, node.node) } catch (_: Exception) {}
            }

            val pools = try { proxmoxRepository.getPools(instanceId) } catch (_: Exception) { emptyList() }

            _uiState.value = UiState.Success(
                ProxmoxDashboardData(
                    version = version,
                    nodes = nodes,
                    vmsByNode = vmsByNode,
                    lxcsByNode = lxcsByNode,
                    storageByNode = storageByNode,
                    pools = pools
                )
            )
        } catch (e: Exception) {
            _uiState.value = UiState.Error(resolvedMessage(e)) { fetchAll() }
        }
    }

    fun performAction(action: ProxmoxGuestAction, node: String, vmid: Int, isQemu: Boolean) {
        viewModelScope.launch {
            try {
                when (action) {
                    ProxmoxGuestAction.START -> if (isQemu) proxmoxRepository.startVM(instanceId, node, vmid) else proxmoxRepository.startLXC(instanceId, node, vmid)
                    ProxmoxGuestAction.STOP -> if (isQemu) proxmoxRepository.stopVM(instanceId, node, vmid) else proxmoxRepository.stopLXC(instanceId, node, vmid)
                    ProxmoxGuestAction.SHUTDOWN -> if (isQemu) proxmoxRepository.shutdownVM(instanceId, node, vmid) else proxmoxRepository.shutdownLXC(instanceId, node, vmid)
                    ProxmoxGuestAction.REBOOT -> if (isQemu) proxmoxRepository.rebootVM(instanceId, node, vmid) else proxmoxRepository.rebootLXC(instanceId, node, vmid)
                }
                fetchAll()
            } catch (e: Exception) {
                _actionResultState.value = UiState.Error(
                    getApplication<Application>().getString(R.string.error_action_failed, resolvedMessage(e))
                )
            }
        }
    }

    fun fetchNodeDetail(node: String) {
        viewModelScope.launch {
            _nodeDetailState.value = UiState.Loading
            try {
                val status = proxmoxRepository.getNodeStatus(instanceId, node)
                val vms = try { proxmoxRepository.getVMs(instanceId, node) } catch (_: Exception) { emptyList() }
                val lxcs = try { proxmoxRepository.getLXCs(instanceId, node) } catch (_: Exception) { emptyList() }
                val tasks = try { proxmoxRepository.getNodeTasks(instanceId, node) } catch (_: Exception) { emptyList() }
                _nodeDetailState.value = UiState.Success(ProxmoxNodeDetailData(status, node, vms, lxcs, tasks))
            } catch (e: Exception) {
                _nodeDetailState.value = UiState.Error(resolvedMessage(e))
            }
        }
    }

    fun fetchGuestDetail(node: String, vmid: Int, isQemu: Boolean) {
        viewModelScope.launch {
            _guestDetailState.value = UiState.Loading
            try {
                if (isQemu) {
                    val vm = proxmoxRepository.getVMStatus(instanceId, node, vmid)
                    val snaps = try { proxmoxRepository.getVMSnapshots(instanceId, node, vmid) } catch (_: Exception) { emptyList() }
                    _guestDetailState.value = UiState.Success(ProxmoxGuestDetailData(status = vm, snapshots = snaps))
                } else {
                    val lxc = proxmoxRepository.getLXCStatus(instanceId, node, vmid)
                    val snaps = try { proxmoxRepository.getLXCSnapshots(instanceId, node, vmid) } catch (_: Exception) { emptyList() }
                    _guestDetailState.value = UiState.Success(ProxmoxGuestDetailData(lxcStatus = lxc, snapshots = snaps))
                }
            } catch (e: Exception) {
                _guestDetailState.value = UiState.Error(resolvedMessage(e))
            }
        }
    }

    fun fetchStorageContent(node: String, storage: String) {
        viewModelScope.launch {
            _storageContentState.value = UiState.Loading
            try {
                val content = proxmoxRepository.getStorageContent(instanceId, node, storage)
                _storageContentState.value = UiState.Success(content)
            } catch (e: Exception) {
                _storageContentState.value = UiState.Error(resolvedMessage(e))
            }
        }
    }

    fun fetchTaskLog(node: String, upid: String, showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading || _taskLogState.value is UiState.Idle) {
                _taskLogState.value = UiState.Loading
            }
            try {
                val log = proxmoxRepository.getTaskLog(instanceId, node, upid)
                _taskLogState.value = UiState.Success(log)
            } catch (e: Exception) {
                _taskLogState.value = UiState.Error(resolvedMessage(e))
            }
        }
    }

    fun fetchTaskStatus(node: String, upid: String, showLoading: Boolean = true) {
        viewModelScope.launch {
            if (showLoading || _taskStatusState.value is UiState.Idle) {
                _taskStatusState.value = UiState.Loading
            }
            try {
                val status = proxmoxRepository.getTaskStatus(instanceId, node, upid)
                _taskStatusState.value = UiState.Success(status)
            } catch (e: Exception) {
                _taskStatusState.value = UiState.Error(resolvedMessage(e))
            }
        }
    }

    suspend fun refreshTaskProgress(node: String, upid: String, showLoading: Boolean = true) {
        if (showLoading || _taskLogState.value is UiState.Idle) {
            _taskLogState.value = UiState.Loading
        }
        if (showLoading || _taskStatusState.value is UiState.Idle) {
            _taskStatusState.value = UiState.Loading
        }
        try {
            val log = proxmoxRepository.getTaskLog(instanceId, node, upid)
            val status = proxmoxRepository.getTaskStatus(instanceId, node, upid)
            _taskLogState.value = UiState.Success(log)
            _taskStatusState.value = UiState.Success(status)
        } catch (e: Exception) {
            val message = resolvedMessage(e)
            _taskLogState.value = UiState.Error(message)
            _taskStatusState.value = UiState.Error(message)
        }
    }

    fun fetchPoolDetail(poolId: String) {
        viewModelScope.launch {
            _poolDetailState.value = UiState.Loading
            try {
                val detail = proxmoxRepository.getPoolMembers(instanceId, poolId)
                _poolDetailState.value = UiState.Success(detail)
            } catch (e: Exception) {
                _poolDetailState.value = UiState.Error(resolvedMessage(e))
            }
        }
    }

    fun fetchBackupJobs() {
        viewModelScope.launch {
            _backupJobsState.value = UiState.Loading
            try {
                val jobs = proxmoxRepository.getBackupJobs(instanceId)
                _backupJobsState.value = UiState.Success(jobs)
            } catch (e: Exception) {
                _backupJobsState.value = UiState.Error(resolvedMessage(e))
            }
        }
    }

    fun triggerBackupJob(jobId: String) {
        viewModelScope.launch {
            try {
                proxmoxRepository.triggerBackupJob(instanceId, jobId)
                fetchBackupJobs()
            } catch (e: Exception) {
                _backupJobsState.value = UiState.Error(e.message ?: "Backup trigger failed") { fetchBackupJobs() }
            }
        }
    }

    // MARK: - Storage Content Actions

    fun deleteStorageContent(node: String, storage: String, volume: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                proxmoxRepository.deleteStorageContent(instanceId, node, storage, volume)
                fetchStorageContent(node, storage)
                onSuccess()
            } catch (e: Exception) {
                _storageContentState.value = UiState.Error(e.message ?: "Delete failed") { fetchStorageContent(node, storage) }
            }
        }
    }

    // MARK: - Firewall Options

    fun fetchFirewallOptions() {
        viewModelScope.launch {
            _firewallOptionsState.value = UiState.Loading
            try {
                val options = proxmoxRepository.getClusterFirewallOptions(instanceId)
                _firewallOptionsState.value = UiState.Success(options)
            } catch (e: Exception) {
                _firewallOptionsState.value = UiState.Error(e.message ?: "Failed to fetch firewall options") { fetchFirewallOptions() }
            }
        }
    }

    fun toggleFirewall(enable: Boolean, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                proxmoxRepository.updateClusterFirewallOptions(instanceId, enable)
                fetchFirewallOptions()
                onSuccess()
            } catch (e: Exception) {
                _firewallOptionsState.value = UiState.Error(e.message ?: "Failed to toggle firewall") { fetchFirewallOptions() }
            }
        }
    }

    // MARK: - Replication

    fun triggerReplicationJob(id: String, onSuccess: () -> Unit = {}) {
        viewModelScope.launch {
            try {
                proxmoxRepository.triggerReplicationJob(instanceId, id)
                fetchReplicationJobs()
                onSuccess()
            } catch (e: Exception) {
                _replicationJobsState.value = UiState.Error(e.message ?: "Failed to trigger replication") { fetchReplicationJobs() }
            }
        }
    }

    fun fetchFirewallRules() {
        viewModelScope.launch {
            _firewallRulesState.value = UiState.Loading
            try {
                val rules = proxmoxRepository.getClusterFirewallRules(instanceId)
                _firewallRulesState.value = UiState.Success(rules)
            } catch (e: Exception) {
                _firewallRulesState.value = UiState.Error(resolvedMessage(e)) { fetchFirewallRules() }
            }
        }
    }

    fun createSnapshot(node: String, vmid: Int, isQemu: Boolean, snapname: String, description: String = "") {
        viewModelScope.launch {
            try {
                proxmoxRepository.createSnapshot(instanceId, node, vmid, isQemu, snapname, description)
                fetchGuestDetail(node, vmid, isQemu)
            } catch (e: Exception) {
                _guestDetailState.value = UiState.Error(e.message ?: "Snapshot creation failed") { fetchGuestDetail(node, vmid, isQemu) }
            }
        }
    }

    fun deleteSnapshot(node: String, vmid: Int, isQemu: Boolean, snapname: String) {
        viewModelScope.launch {
            try {
                proxmoxRepository.deleteSnapshot(instanceId, node, vmid, isQemu, snapname)
                fetchGuestDetail(node, vmid, isQemu)
            } catch (e: Exception) {
                _guestDetailState.value = UiState.Error(e.message ?: "Snapshot deletion failed") { fetchGuestDetail(node, vmid, isQemu) }
            }
        }
    }

    fun rollbackSnapshot(node: String, vmid: Int, isQemu: Boolean, snapname: String) {
        viewModelScope.launch {
            try {
                proxmoxRepository.rollbackSnapshot(instanceId, node, vmid, isQemu, snapname)
                fetchGuestDetail(node, vmid, isQemu)
            } catch (e: Exception) {
                _guestDetailState.value = UiState.Error(e.message ?: "Snapshot rollback failed") { fetchGuestDetail(node, vmid, isQemu) }
            }
        }
    }

    fun fetchVncTicket(node: String, vmid: Int, isQemu: Boolean) {
        viewModelScope.launch {
            _vncTicketState.value = UiState.Loading
            try {
                val ticketData = proxmoxRepository.getVncTicket(instanceId, node, vmid, isQemu)
                val instance = instances.value.find { it.id == instanceId }
                val baseUrl = instance?.url?.trimEnd('/') ?: ""
                _vncTicketState.value = UiState.Success(
                    ProxmoxVncTicketData(
                        ticket = ticketData.ticket,
                        port = ticketData.port,
                        baseUrl = baseUrl,
                        node = node,
                        vmid = vmid,
                        isQemu = isQemu
                    )
                )
            } catch (e: Exception) {
                _vncTicketState.value = UiState.Error(
                    e.message?.takeIf { it.isNotBlank() } ?: getApplication<Application>().getString(R.string.proxmox_failed_fetch_vnc_ticket)
                )
            }
        }
    }

    fun fetchGuestConfig(node: String, vmid: Int, isQemu: Boolean) {
        viewModelScope.launch {
            _guestConfigState.value = UiState.Loading
            try {
                val config = proxmoxRepository.getGuestConfig(instanceId, node, vmid, isQemu)
                _guestConfigState.value = UiState.Success(config)
            } catch (e: Exception) {
                _guestConfigState.value = UiState.Error(e.message ?: "Failed to fetch config")
            }
        }
    }

    fun updateGuestConfig(
        node: String,
        vmid: Int,
        isQemu: Boolean,
        config: Map<String, String>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                proxmoxRepository.updateGuestConfig(instanceId, node, vmid, isQemu, config)
                onSuccess()
            } catch (e: Exception) {
                _guestConfigState.value = UiState.Error(e.message ?: "Failed to update config")
            }
        }
    }

    fun fetchAptUpdates(node: String) {
        viewModelScope.launch {
            _aptUpdatesState.value = UiState.Loading
            try {
                val updates = proxmoxRepository.getAptUpdates(instanceId, node)
                _aptUpdatesState.value = UiState.Success(updates)
            } catch (e: Exception) {
                _aptUpdatesState.value = UiState.Error(resolvedMessage(e)) { fetchAptUpdates(node) }
            }
        }
    }

    fun fetchHAResources() {
        viewModelScope.launch {
            _haResourcesState.value = UiState.Loading
            try {
                val resources = proxmoxRepository.getHAResources(instanceId)
                _haResourcesState.value = UiState.Success(resources)
            } catch (e: Exception) {
                _haResourcesState.value = UiState.Error(resolvedMessage(e)) { fetchHAResources() }
            }
        }
    }

    fun fetchHAGroups() {
        viewModelScope.launch {
            _haGroupsState.value = UiState.Loading
            try {
                val groups = proxmoxRepository.getHAGroups(instanceId)
                _haGroupsState.value = UiState.Success(groups)
            } catch (e: Exception) {
                _haGroupsState.value = UiState.Error(resolvedMessage(e)) { fetchHAGroups() }
            }
        }
    }

    fun fetchClusterResources() {
        viewModelScope.launch {
            _clusterResourcesState.value = UiState.Loading
            try {
                val resources = proxmoxRepository.getClusterResources(instanceId)
                _clusterResourcesState.value = UiState.Success(resources)
            } catch (e: Exception) {
                _clusterResourcesState.value = UiState.Error(resolvedMessage(e)) { fetchClusterResources() }
            }
        }
    }

    fun fetchCephStatus(node: String) {
        viewModelScope.launch {
            _cephStatusState.value = UiState.Loading
            try {
                val cephStatus = proxmoxRepository.getCephStatus(instanceId, node)
                _cephStatusState.value = UiState.Success(cephStatus)
            } catch (e: Exception) {
                _cephStatusState.value = UiState.Error(e.message ?: "Failed to fetch Ceph status") { fetchCephStatus(node) }
            }
        }
    }

    fun fetchReplicationJobs() {
        viewModelScope.launch {
            _replicationJobsState.value = UiState.Loading
            try {
                val jobs = proxmoxRepository.getReplicationJobs(instanceId)
                _replicationJobsState.value = UiState.Success(jobs)
            } catch (e: Exception) {
                _replicationJobsState.value = UiState.Error(e.message ?: "Failed to fetch replication jobs") { fetchReplicationJobs() }
            }
        }
    }

    fun cloneVM(
        node: String,
        vmid: Int,
        newId: Int,
        name: String,
        fullClone: Boolean = true,
        targetNode: String? = null,
        targetStorage: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _actionResultState.value = UiState.Loading
            try {
                val body = mutableMapOf(
                    "newid" to newId.toString(),
                    "name" to name,
                    "full" to if (fullClone) "1" else "0"
                )
                if (!targetNode.isNullOrBlank()) body["target"] = targetNode
                if (!targetStorage.isNullOrBlank()) body["storage"] = targetStorage

                val upid = proxmoxRepository.cloneVM(instanceId, node, vmid, body)
                _actionResultState.value = UiState.Success(upid)
                onSuccess()
            } catch (e: Exception) {
                _actionResultState.value = UiState.Error(e.message ?: "Clone failed")
            }
        }
    }

    fun cloneLXC(
        node: String,
        vmid: Int,
        newId: Int,
        name: String,
        targetNode: String? = null,
        targetStorage: String? = null,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _actionResultState.value = UiState.Loading
            try {
                val body = mutableMapOf(
                    "newid" to newId.toString(),
                    "hostname" to name
                )
                if (!targetNode.isNullOrBlank()) body["target"] = targetNode
                if (!targetStorage.isNullOrBlank()) body["storage"] = targetStorage

                val upid = proxmoxRepository.cloneLXC(instanceId, node, vmid, body)
                _actionResultState.value = UiState.Success(upid)
                onSuccess()
            } catch (e: Exception) {
                _actionResultState.value = UiState.Error(e.message ?: "Clone failed")
            }
        }
    }

    fun migrateVM(
        node: String,
        vmid: Int,
        targetNode: String,
        online: Boolean = false,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _actionResultState.value = UiState.Loading
            try {
                val body = mutableMapOf(
                    "target" to targetNode,
                    "online" to if (online) "1" else "0"
                )
                val upid = proxmoxRepository.migrateVM(instanceId, node, vmid, body)
                _actionResultState.value = UiState.Success(upid)
                onSuccess()
            } catch (e: Exception) {
                _actionResultState.value = UiState.Error(e.message ?: "Migration failed")
            }
        }
    }

    fun migrateLXC(
        node: String,
        vmid: Int,
        targetNode: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _actionResultState.value = UiState.Loading
            try {
                val body = mutableMapOf("target" to targetNode)
                val upid = proxmoxRepository.migrateLXC(instanceId, node, vmid, body)
                _actionResultState.value = UiState.Success(upid)
                onSuccess()
            } catch (e: Exception) {
                _actionResultState.value = UiState.Error(e.message ?: "Migration failed")
            }
        }
    }

    // MARK: - ISO List

    fun fetchIsoList(node: String, storage: String, content: String = "iso") {
        viewModelScope.launch {
            _isoListState.value = UiState.Loading
            try {
                val isoList = proxmoxRepository.getIsoList(instanceId, node, storage, content)
                _isoListState.value = UiState.Success(isoList)
            } catch (e: Exception) {
                _isoListState.value = UiState.Error(e.message ?: "Failed to fetch ISO list")
            }
        }
    }

    // MARK: - Journal

    fun fetchJournal(node: String, since: Long? = null, limit: Int = 100) {
        viewModelScope.launch {
            _journalState.value = UiState.Loading
            try {
                val journal = proxmoxRepository.getJournal(instanceId, node, since, limit)
                _journalState.value = UiState.Success(journal)
            } catch (e: Exception) {
                _journalState.value = UiState.Error(e.message ?: "Failed to fetch journal")
            }
        }
    }

    // MARK: - Next VMID

    fun fetchNextVmid() {
        viewModelScope.launch {
            _nextVmidState.value = UiState.Loading
            try {
                val nextVmid = proxmoxRepository.getNextVmid(instanceId)
                _nextVmidState.value = UiState.Success(nextVmid)
            } catch (e: Exception) {
                _nextVmidState.value = UiState.Error(e.message ?: "Failed to fetch next VMID")
            }
        }
    }

    // MARK: - Guest Create

    fun createGuest(
        node: String,
        isQemu: Boolean,
        body: Map<String, String>,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            _guestCreateResultState.value = UiState.Loading
            try {
                val upid = if (isQemu) {
                    proxmoxRepository.createVM(instanceId, node, body)
                } else {
                    proxmoxRepository.createLXC(instanceId, node, body)
                }
                _guestCreateResultState.value = UiState.Success(upid)
                onSuccess()
            } catch (e: Exception) {
                _guestCreateResultState.value = UiState.Error(e.message ?: "Guest creation failed")
            }
        }
    }

    fun clearGuestCreateResult() {
        _guestCreateResultState.value = UiState.Idle
    }

    fun clearActionResult() {
        _actionResultState.value = UiState.Idle
    }

    // MARK: - Global Tasks

    fun fetchGlobalTasks() {
        viewModelScope.launch {
            _globalTasksState.value = UiState.Loading
            try {
                val nodes = proxmoxRepository.getNodes(instanceId)
                val allTasks = mutableListOf<ProxmoxTask>()

                nodes.forEach { node ->
                    try {
                        val tasks = proxmoxRepository.getNodeTasks(instanceId, node.node, limit = 10)
                        allTasks.addAll(tasks)
                    } catch (_: Exception) {
                        // Skip failed nodes, don't fail the whole screen
                    }
                }

                val sortedTasks = allTasks.sortedByDescending { it.starttime ?: 0L }
                _globalTasksState.value = UiState.Success(sortedTasks)
            } catch (e: Exception) {
                _globalTasksState.value = UiState.Error(e.message ?: "Failed to fetch global tasks") { fetchGlobalTasks() }
            }
        }
    }

    fun refreshGlobalTasks() {
        viewModelScope.launch {
            _isGlobalTasksRefreshing.value = true
            fetchGlobalTasks()
            _isGlobalTasksRefreshing.value = false
        }
    }

    // MARK: - Network

    fun fetchNetwork(node: String) {
        viewModelScope.launch {
            _networkState.value = UiState.Loading
            try {
                val network = proxmoxRepository.getNodeNetwork(instanceId, node)
                _networkState.value = UiState.Success(network)
            } catch (e: Exception) {
                _networkState.value = UiState.Error(e.message ?: "Failed to fetch network interfaces") { fetchNetwork(node) }
            }
        }
    }

    // MARK: - Update Guest Description

    fun updateGuestDescription(
        node: String,
        vmid: Int,
        isQemu: Boolean,
        description: String,
        onSuccess: () -> Unit = {}
    ) {
        viewModelScope.launch {
            try {
                proxmoxRepository.updateGuestDescription(instanceId, node, vmid, isQemu, description)
                fetchGuestDetail(node, vmid, isQemu)
                onSuccess()
            } catch (e: Exception) {
                _guestDetailState.value = UiState.Error(e.message ?: "Failed to update description")
            }
        }
    }

    fun fetchNodes() {
        viewModelScope.launch {
            _nodesState.value = UiState.Loading
            try {
                val nodes = proxmoxRepository.getNodes(instanceId).map { it.node }
                _nodesState.value = UiState.Success(nodes)
            } catch (e: Exception) {
                _nodesState.value = UiState.Error(e.message ?: "Failed to fetch nodes")
            }
        }
    }
}

data class ProxmoxDashboardData(
    val version: ProxmoxVersion,
    val nodes: List<ProxmoxNode>,
    val vmsByNode: Map<String, List<ProxmoxVM>>,
    val lxcsByNode: Map<String, List<ProxmoxLXC>>,
    val storageByNode: Map<String, List<ProxmoxStorage>>,
    val pools: List<ProxmoxPool>
) {
    val totalVMs: Int get() = vmsByNode.values.sumOf { it.size }
    val runningVMs: Int get() = vmsByNode.values.sumOf { it.count { vm -> vm.isRunning } }
    val totalLXCs: Int get() = lxcsByNode.values.sumOf { it.size }
    val runningLXCs: Int get() = lxcsByNode.values.sumOf { it.count { lxc -> lxc.isRunning } }
    val onlineNodes: Int get() = nodes.count { it.isOnline }
}

enum class ProxmoxGuestAction {
    START, STOP, SHUTDOWN, REBOOT
}

data class ProxmoxNodeDetailData(
    val status: ProxmoxNodeStatus,
    val nodeName: String = "",
    val vms: List<ProxmoxVM> = emptyList(),
    val lxcs: List<ProxmoxLXC> = emptyList(),
    val tasks: List<ProxmoxTask> = emptyList()
)

data class ProxmoxGuestDetailData(
    val status: ProxmoxVM? = null,
    val lxcStatus: ProxmoxLXC? = null,
    val snapshots: List<ProxmoxSnapshot> = emptyList()
) {
    val vmid: Int get() = status?.vmid ?: (lxcStatus?.vmid ?: 0)
    val name: String get() = status?.displayName ?: (lxcStatus?.displayName ?: "Unknown")
    val isRunning: Boolean get() = status?.isRunning ?: (lxcStatus?.isRunning ?: false)
    val isStopped: Boolean get() = status?.isStopped ?: (lxcStatus?.isStopped ?: false)
    val cpuPercent: Double get() = status?.cpuPercent ?: (lxcStatus?.cpuPercent ?: 0.0)
    val memPercent: Double get() = status?.memPercent ?: (lxcStatus?.memPercent ?: 0.0)
    val disk: Long? get() = status?.disk ?: lxcStatus?.disk
    val maxdisk: Long? get() = status?.maxdisk ?: lxcStatus?.maxdisk
    val diskPercent: Double
        get() {
            val d = disk ?: return 0.0
            val md = maxdisk ?: return 0.0
            return if (md > 0) d.toDouble() / md.toDouble() * 100 else 0.0
        }
    val uptime: String get() = status?.formattedUptime ?: (lxcStatus?.formattedUptime ?: "-")
    val isQemu: Boolean get() = status != null
}

data class ProxmoxVncTicketData(
    val ticket: String,
    val port: Int?,
    val baseUrl: String,
    val node: String,
    val vmid: Int,
    val isQemu: Boolean
) {
    /**
     * Builds the Proxmox noVNC URL with the ticket set as a cookie.
     * The WebView should set the PVEAuthCookie before loading this URL.
     */
    fun buildConsoleUrl(): String {
        return "$baseUrl/?console=${if (isQemu) "kvm" else "lxc"}&novnc=1&vmid=$vmid&node=$node&resize=off"
    }
}
