package com.homelab.app.ui.nginxpm

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.R
import com.homelab.app.data.remote.dto.nginxpm.*
import com.homelab.app.data.repository.NginxProxyManagerRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class NpmDashboardData(
    val hostReport: NpmHostReport,
    val proxyHosts: List<NpmProxyHost>,
    val redirectionHosts: List<NpmRedirectionHost> = emptyList(),
    val streams: List<NpmStream> = emptyList(),
    val deadHosts: List<NpmDeadHost> = emptyList(),
    val certificates: List<NpmCertificate> = emptyList(),
    val accessLists: List<NpmAccessList> = emptyList(),
    val users: List<NpmUser> = emptyList(),
    val auditLogs: List<NpmAuditLog> = emptyList()
)

sealed class NpmActionEvent {
    data class Success(val message: String) : NpmActionEvent()
    data class Error(val message: String) : NpmActionEvent()
}

@HiltViewModel
class NpmDashboardViewModel @Inject constructor(
    private val repository: NginxProxyManagerRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _dashboardState = MutableStateFlow<UiState<NpmDashboardData>>(UiState.Loading)
    val dashboardState: StateFlow<UiState<NpmDashboardData>> = _dashboardState

    private val _selectedTab = MutableStateFlow(0)
    val selectedTab: StateFlow<Int> = _selectedTab.asStateFlow()

    private val _isPerformingAction = MutableStateFlow(false)
    val isPerformingAction: StateFlow<Boolean> = _isPerformingAction.asStateFlow()

    private val _actionEvent = MutableSharedFlow<NpmActionEvent>()
    val actionEvent: SharedFlow<NpmActionEvent> = _actionEvent.asSharedFlow()

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.NGINX_PROXY_MANAGER].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun selectTab(index: Int) {
        _selectedTab.value = index
    }

    fun fetchDashboard() {
        viewModelScope.launch {
            _dashboardState.value = UiState.Loading
            try {
                coroutineScope {
                    val reportDeferred = async { repository.getHostReport(instanceId) }
                    val hostsDeferred = async { repository.getProxyHosts(instanceId) }
                    val redirectionsDeferred = async { repository.getRedirectionHosts(instanceId) }
                    val streamsDeferred = async { repository.getStreams(instanceId) }
                    val deadHostsDeferred = async { repository.getDeadHosts(instanceId) }
                    val certsDeferred = async { repository.getCertificates(instanceId) }
                    val accessListsDeferred = async { repository.getAccessLists(instanceId) }
                    val usersDeferred = async { repository.getUsers(instanceId) }
                    val auditLogsDeferred = async { repository.getAuditLogs(instanceId) }

                    _dashboardState.value = UiState.Success(
                        NpmDashboardData(
                            hostReport = reportDeferred.await(),
                            proxyHosts = hostsDeferred.await(),
                            redirectionHosts = redirectionsDeferred.await(),
                            streams = streamsDeferred.await(),
                            deadHosts = deadHostsDeferred.await(),
                            certificates = certsDeferred.await(),
                            accessLists = accessListsDeferred.await(),
                            users = runCatching { usersDeferred.await() }.getOrDefault(emptyList()),
                            auditLogs = runCatching { auditLogsDeferred.await() }.getOrDefault(emptyList())
                        )
                    )
                }
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _dashboardState.value = UiState.Error(message, retryAction = { fetchDashboard() })
            }
        }
    }

    // ── Proxy Hosts ──

    fun createProxyHost(request: NpmProxyHostRequest) {
        performAction(R.string.npm_save_success) {
            repository.createProxyHost(instanceId, request)
        }
    }

    fun updateProxyHost(hostId: Int, request: NpmProxyHostRequest) {
        performAction(R.string.npm_save_success) {
            repository.updateProxyHost(instanceId, hostId, request)
        }
    }

    fun deleteProxyHost(hostId: Int) {
        performAction(R.string.npm_delete_success) {
            repository.deleteProxyHost(instanceId, hostId)
        }
    }

    fun toggleProxyHost(hostId: Int, enabled: Boolean) {
        performAction(R.string.npm_save_success) {
            if (enabled) repository.enableProxyHost(instanceId, hostId)
            else repository.disableProxyHost(instanceId, hostId)
        }
    }

    // ── Redirection Hosts ──

    fun createRedirectionHost(request: NpmRedirectionHostRequest) {
        performAction(R.string.npm_save_success) {
            repository.createRedirectionHost(instanceId, request)
        }
    }

    fun updateRedirectionHost(hostId: Int, request: NpmRedirectionHostRequest) {
        performAction(R.string.npm_save_success) {
            repository.updateRedirectionHost(instanceId, hostId, request)
        }
    }

    fun deleteRedirectionHost(hostId: Int) {
        performAction(R.string.npm_delete_success) {
            repository.deleteRedirectionHost(instanceId, hostId)
        }
    }

    // ── Streams ──

    fun createStream(request: NpmStreamRequest) {
        performAction(R.string.npm_save_success) {
            repository.createStream(instanceId, request)
        }
    }

    fun updateStream(streamId: Int, request: NpmStreamRequest) {
        performAction(R.string.npm_save_success) {
            repository.updateStream(instanceId, streamId, request)
        }
    }

    fun deleteStream(streamId: Int) {
        performAction(R.string.npm_delete_success) {
            repository.deleteStream(instanceId, streamId)
        }
    }

    // ── Dead Hosts ──

    fun createDeadHost(request: NpmDeadHostRequest) {
        performAction(R.string.npm_save_success) {
            repository.createDeadHost(instanceId, request)
        }
    }

    fun updateDeadHost(hostId: Int, request: NpmDeadHostRequest) {
        performAction(R.string.npm_save_success) {
            repository.updateDeadHost(instanceId, hostId, request)
        }
    }

    fun deleteDeadHost(hostId: Int) {
        performAction(R.string.npm_delete_success) {
            repository.deleteDeadHost(instanceId, hostId)
        }
    }

    // ── Certificates ──

    fun createCertificate(request: NpmCertificateRequest) {
        performAction(R.string.npm_save_success) {
            repository.createCertificate(instanceId, request)
        }
    }

    fun deleteCertificate(certId: Int) {
        performAction(R.string.npm_delete_success) {
            repository.deleteCertificate(instanceId, certId)
        }
    }

    fun renewCertificate(certId: Int) {
        performAction(R.string.npm_renew_success) {
            repository.renewCertificate(instanceId, certId)
        }
    }

    // ── Access Lists ──

    fun createAccessList(
        name: String,
        items: List<NpmAccessListItem>,
        clients: List<NpmAccessListClient>
    ) {
        performAction(R.string.npm_save_success) {
            val request = NpmAccessListRequest(
                name = name,
                items = items,
                clients = clients
            )
            repository.createAccessList(instanceId, request)
        }
    }

    fun updateAccessList(
        id: Int,
        name: String,
        items: List<NpmAccessListItem>,
        clients: List<NpmAccessListClient>
    ) {
        performAction(R.string.npm_save_success) {
            val request = NpmAccessListRequest(
                name = name,
                items = items,
                clients = clients
            )
            repository.updateAccessList(instanceId, id, request)
        }
    }

    fun deleteAccessList(id: Int) {
        performAction(R.string.npm_delete_success) {
            repository.deleteAccessList(instanceId, id)
        }
    }

    // ── Users ──

    fun createUser(request: NpmUserRequest) {
        performAction(R.string.npm_save_success) {
            repository.createUser(instanceId, request)
        }
    }

    fun updateUser(userId: Int, request: NpmUserRequest) {
        performAction(R.string.npm_save_success) {
            repository.updateUser(instanceId, userId, request)
        }
    }

    fun deleteUser(userId: Int) {
        performAction(R.string.npm_delete_success) {
            repository.deleteUser(instanceId, userId)
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.NGINX_PROXY_MANAGER, newInstanceId)
        }
    }

    private fun performAction(successMsgRes: Int, action: suspend () -> Unit) {
        viewModelScope.launch {
            _isPerformingAction.value = true
            try {
                action()
                _actionEvent.emit(NpmActionEvent.Success(context.getString(successMsgRes)))
                fetchDashboard()
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _actionEvent.emit(NpmActionEvent.Error(message))
            } finally {
                _isPerformingAction.value = false
            }
        }
    }
}
