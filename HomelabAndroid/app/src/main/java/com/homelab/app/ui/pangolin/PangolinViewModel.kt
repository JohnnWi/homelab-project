package com.homelab.app.ui.pangolin

import android.content.Context
import com.homelab.app.R
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.pangolin.PangolinClient
import com.homelab.app.data.remote.dto.pangolin.PangolinDomain
import com.homelab.app.data.remote.dto.pangolin.PangolinOrg
import com.homelab.app.data.remote.dto.pangolin.PangolinResource
import com.homelab.app.data.remote.dto.pangolin.PangolinSite
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResource
import com.homelab.app.data.remote.dto.pangolin.PangolinTarget
import com.homelab.app.data.remote.dto.pangolin.PangolinUserDevice
import com.homelab.app.data.repository.PangolinRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

data class PangolinDashboardData(
    val orgs: List<PangolinOrg>,
    val selectedOrgId: String,
    val sites: List<PangolinSite>,
    val siteResources: List<PangolinSiteResource>,
    val resources: List<PangolinResource>,
    val targetsByResourceId: Map<Int, List<PangolinTarget>>,
    val clientEntries: List<PangolinClientEntry>,
    val domains: List<PangolinDomain>
)

enum class PangolinClientSource {
    MACHINE,
    USER_DEVICE
}

data class PangolinClientEntry(
    val id: String,
    val name: String,
    val subtitle: String,
    val online: Boolean,
    val blocked: Boolean,
    val archived: Boolean,
    val approvalState: String?,
    val version: String?,
    val updateAvailable: Boolean,
    val trafficIn: Double?,
    val trafficOut: Double?,
    val source: PangolinClientSource,
    val agent: String?,
    val linkedSites: List<String>
)

data class PangolinPublicResourceUpdateInput(
    val resourceId: Int,
    val name: String,
    val enabled: Boolean,
    val sso: Boolean,
    val ssl: Boolean,
    val targetId: Int?,
    val targetSiteId: Int?,
    val targetIp: String,
    val targetPort: String,
    val targetEnabled: Boolean
)

data class PangolinPublicResourceCreateInput(
    val name: String,
    val protocol: String,
    val enabled: Boolean,
    val domainId: String?,
    val subdomain: String,
    val proxyPort: String,
    val targetSiteId: Int,
    val targetIp: String,
    val targetPort: String,
    val targetEnabled: Boolean,
    val targetMethod: String?
)

data class PangolinPrivateResourceUpdateInput(
    val siteResourceId: Int,
    val name: String,
    val siteId: Int,
    val mode: String,
    val destination: String,
    val enabled: Boolean,
    val alias: String,
    val tcpPortRangeString: String,
    val udpPortRangeString: String,
    val disableIcmp: Boolean,
    val authDaemonPort: String,
    val authDaemonMode: String?
)

data class PangolinPrivateResourceCreateInput(
    val name: String,
    val siteId: Int,
    val mode: String,
    val destination: String,
    val enabled: Boolean,
    val alias: String,
    val tcpPortRangeString: String,
    val udpPortRangeString: String,
    val disableIcmp: Boolean,
    val authDaemonPort: String,
    val authDaemonMode: String?
)

sealed interface PangolinUiState {
    data object Loading : PangolinUiState
    data class Success(val data: PangolinDashboardData) : PangolinUiState
    data class Error(val message: String) : PangolinUiState
}

@HiltViewModel
class PangolinViewModel @Inject constructor(
    private val repository: PangolinRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<PangolinUiState>(PangolinUiState.Loading)
    val uiState: StateFlow<PangolinUiState> = _uiState

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.PANGOLIN].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var selectedOrgId: String? = null
    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh(forceOrgId: String? = selectedOrgId, showLoading: Boolean = true) {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val previousData = (_uiState.value as? PangolinUiState.Success)?.data
            if (showLoading || _uiState.value !is PangolinUiState.Success) {
                _uiState.value = PangolinUiState.Loading
            }
            try {
                val currentInstance = servicesRepository.instancesByType.first()[ServiceType.PANGOLIN]
                    ?.firstOrNull { it.id == instanceId }
                val scopedOrgId = currentInstance?.username?.takeIf { it.isNotBlank() }
                val orgs = repository.listOrgs(instanceId, scopedOrgId)
                val resolvedOrgId = forceOrgId?.takeIf { candidate -> orgs.any { it.orgId == candidate } }
                    ?: orgs.firstOrNull()?.orgId
                    ?: throw IllegalStateException(context.getString(R.string.pangolin_error_no_orgs))
                selectedOrgId = resolvedOrgId

                val snapshot = repository.getSnapshot(instanceId, resolvedOrgId, orgs)
                val mergedResources = mergeMissingDisabledResources(snapshot.resources, previousData?.resources)
                val mergedTargetsByResourceId = mergeMissingTargets(
                    currentTargets = snapshot.targetsByResourceId,
                    currentResources = snapshot.resources,
                    mergedResources = mergedResources,
                    previousData = previousData
                )
                _uiState.value = PangolinUiState.Success(
                    PangolinDashboardData(
                        orgs = snapshot.orgs,
                        selectedOrgId = resolvedOrgId,
                        sites = snapshot.sites,
                        siteResources = snapshot.siteResources,
                        resources = mergedResources,
                        targetsByResourceId = mergedTargetsByResourceId,
                        clientEntries = buildClientEntries(snapshot.clients, snapshot.userDevices),
                        domains = snapshot.domains
                    )
                )
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = PangolinUiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun selectOrg(orgId: String) {
        if (orgId == selectedOrgId) return
        refresh(forceOrgId = orgId)
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.PANGOLIN, newInstanceId)
        }
    }

    suspend fun savePublicResource(input: PangolinPublicResourceUpdateInput): Result<Unit> = runCatching {
        repository.updateResource(
            instanceId = instanceId,
            resourceId = input.resourceId,
            name = input.name.trim(),
            enabled = input.enabled,
            sso = input.sso,
            ssl = input.ssl
        )

        val targetId = input.targetId
        val siteId = input.targetSiteId
        val port = input.targetPort.trim().toIntOrNull()
        if (targetId != null && siteId != null && input.targetIp.isNotBlank() && port != null) {
            repository.updateTarget(
                instanceId = instanceId,
                targetId = targetId,
                siteId = siteId,
                ip = input.targetIp.trim(),
                port = port,
                enabled = input.targetEnabled
            )
        }

        refresh(showLoading = false)
    }.mapError()

    suspend fun createPublicResource(input: PangolinPublicResourceCreateInput): Result<Unit> = runCatching {
        val resource = repository.createResource(
            instanceId = instanceId,
            orgId = requireSelectedOrgId(),
            name = input.name.trim(),
            protocol = input.protocol,
            enabled = input.enabled,
            domainId = input.domainId?.trim()?.takeIf { it.isNotBlank() },
            subdomain = input.subdomain.trim().takeIf { it.isNotBlank() },
            proxyPort = input.proxyPort.trim().toIntOrNull()
        )

        try {
            repository.createTarget(
                instanceId = instanceId,
                resourceId = resource.resourceId,
                siteId = input.targetSiteId,
                ip = input.targetIp.trim(),
                port = input.targetPort.trim().toInt(),
                enabled = input.targetEnabled,
                method = input.targetMethod?.trim()?.takeIf { it.isNotBlank() }
            )
        } catch (error: Exception) {
            runCatching {
                repository.deleteResource(instanceId = instanceId, resourceId = resource.resourceId)
            }
            throw error
        }

        refresh(showLoading = false)
    }.mapError()

    suspend fun savePrivateResource(input: PangolinPrivateResourceUpdateInput): Result<Unit> = runCatching {
        val bindings = repository.getSiteResourceBindings(instanceId = instanceId, siteResourceId = input.siteResourceId)
        repository.updateSiteResource(
            instanceId = instanceId,
            siteResourceId = input.siteResourceId,
            bindings = bindings,
            name = input.name.trim(),
            siteId = input.siteId,
            mode = input.mode,
            destination = input.destination.trim(),
            enabled = input.enabled,
            alias = input.alias.trim(),
            tcpPortRangeString = input.tcpPortRangeString.trim(),
            udpPortRangeString = input.udpPortRangeString.trim(),
            disableIcmp = input.disableIcmp,
            authDaemonPort = input.authDaemonPort.trim().toIntOrNull(),
            authDaemonMode = input.authDaemonMode
        )

        refresh(showLoading = false)
    }.mapError()

    suspend fun createPrivateResource(input: PangolinPrivateResourceCreateInput): Result<Unit> = runCatching {
        repository.createSiteResource(
            instanceId = instanceId,
            orgId = requireSelectedOrgId(),
            name = input.name.trim(),
            siteId = input.siteId,
            mode = input.mode,
            destination = input.destination.trim(),
            enabled = input.enabled,
            alias = input.alias.trim(),
            tcpPortRangeString = input.tcpPortRangeString.trim(),
            udpPortRangeString = input.udpPortRangeString.trim(),
            disableIcmp = input.disableIcmp,
            authDaemonPort = input.authDaemonPort.trim().toIntOrNull(),
            authDaemonMode = input.authDaemonMode
        )

        refresh(showLoading = false)
    }.mapError()

    suspend fun togglePublicResource(resource: PangolinResource): Result<Boolean> = runCatching {
        val newEnabled = !resource.enabled
        repository.updateResource(
            instanceId = instanceId,
            resourceId = resource.resourceId,
            name = resource.name.trim(),
            enabled = newEnabled,
            sso = resource.sso,
            ssl = resource.ssl
        )

        refresh(showLoading = false)
        newEnabled
    }.mapErrorValue()

    suspend fun togglePrivateResource(resource: PangolinSiteResource): Result<Boolean> = runCatching {
        val newEnabled = !resource.enabled
        val bindings = repository.getSiteResourceBindings(instanceId = instanceId, siteResourceId = resource.siteResourceId)
        repository.updateSiteResource(
            instanceId = instanceId,
            siteResourceId = resource.siteResourceId,
            bindings = bindings,
            name = resource.name.trim(),
            siteId = resource.siteId,
            mode = resource.mode ?: "host",
            destination = resource.destination.orEmpty().trim(),
            enabled = newEnabled,
            alias = resource.alias.orEmpty().trim(),
            tcpPortRangeString = resource.tcpPortRangeString.orEmpty().trim(),
            udpPortRangeString = resource.udpPortRangeString.orEmpty().trim(),
            disableIcmp = resource.disableIcmp ?: false,
            authDaemonPort = resource.authDaemonPort,
            authDaemonMode = resource.authDaemonMode
        )

        refresh(showLoading = false)
        newEnabled
    }.mapErrorValue()

    private fun buildClientEntries(
        machineClients: List<PangolinClient>,
        userDevices: List<PangolinUserDevice>
    ): List<PangolinClientEntry> {
        val machines = machineClients.map { client ->
            PangolinClientEntry(
                id = "machine-${client.clientId}",
                name = client.name,
                subtitle = listOfNotNull(client.subnet, client.type).joinToString(" • "),
                online = client.online,
                blocked = client.blocked,
                archived = client.archived,
                approvalState = client.approvalState,
                version = client.olmVersion,
                updateAvailable = client.olmUpdateAvailable == true,
                trafficIn = client.megabytesIn,
                trafficOut = client.megabytesOut,
                source = PangolinClientSource.MACHINE,
                agent = null,
                linkedSites = client.sites.mapNotNull { it.siteName ?: it.siteNiceId }
            )
        }

        val devices = userDevices.map { device ->
            PangolinClientEntry(
                id = "device-${device.clientId}",
                name = device.name,
                subtitle = listOfNotNull(device.deviceModel, device.fingerprintPlatform, device.subnet).joinToString(" • "),
                online = device.online,
                blocked = device.blocked,
                archived = device.archived || device.olmArchived,
                approvalState = device.approvalState,
                version = device.olmVersion,
                updateAvailable = device.olmUpdateAvailable == true,
                trafficIn = device.megabytesIn,
                trafficOut = device.megabytesOut,
                source = PangolinClientSource.USER_DEVICE,
                agent = device.agent ?: device.type,
                linkedSites = emptyList()
            )
        }

        return (machines + devices).sortedWith(
            compareByDescending<PangolinClientEntry> { it.online }
                .thenBy { it.blocked }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun mergeMissingDisabledResources(
        currentResources: List<PangolinResource>,
        previousResources: List<PangolinResource>?
    ): List<PangolinResource> {
        val missingDisabled = previousResources.orEmpty()
            .filterNot { it.enabled }
            .filterNot { previous ->
                currentResources.any { it.resourceId == previous.resourceId }
            }
        if (missingDisabled.isEmpty()) return currentResources
        return (currentResources + missingDisabled).sortedWith(
            compareByDescending<PangolinResource> { it.enabled }
                .thenBy { it.name.lowercase() }
        )
    }

    private fun mergeMissingTargets(
        currentTargets: Map<Int, List<PangolinTarget>>,
        currentResources: List<PangolinResource>,
        mergedResources: List<PangolinResource>,
        previousData: PangolinDashboardData?
    ): Map<Int, List<PangolinTarget>> {
        if (previousData == null) return currentTargets
        val currentIds = currentResources.mapTo(mutableSetOf()) { it.resourceId }
        val mergedTargets = currentTargets.toMutableMap()
        mergedResources
            .asSequence()
            .filterNot { it.resourceId in currentIds }
            .forEach { resource ->
                previousData.targetsByResourceId[resource.resourceId]?.let { mergedTargets[resource.resourceId] = it }
            }
        return mergedTargets
    }

    private fun requireSelectedOrgId(): String {
        return selectedOrgId
            ?: (uiState.value as? PangolinUiState.Success)?.data?.selectedOrgId
            ?: throw IllegalStateException(context.getString(R.string.pangolin_error_no_orgs))
    }

    private fun Result<Unit>.mapError(): Result<Unit> = fold(
        onSuccess = { Result.success(Unit) },
        onFailure = { error ->
            Result.failure(IllegalStateException(ErrorHandler.getMessage(context, error)))
        }
    )

    private fun Result<Boolean>.mapErrorValue(): Result<Boolean> = fold(
        onSuccess = { Result.success(it) },
        onFailure = { error ->
            Result.failure(IllegalStateException(ErrorHandler.getMessage(context, error)))
        }
    )
}
