package com.homelab.app.ui.dockmon

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.DockmonContainer
import com.homelab.app.data.repository.DockmonDashboardData
import com.homelab.app.data.repository.DockmonRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class DockmonViewModel @Inject constructor(
    private val repository: DockmonRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<DockmonDashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<DockmonDashboardData>> = _uiState.asStateFlow()

    private val _selectedHostId = MutableStateFlow<String?>(null)
    val selectedHostId: StateFlow<String?> = _selectedHostId.asStateFlow()

    private val _selectedContainerId = MutableStateFlow<String?>(null)
    val selectedContainerId: StateFlow<String?> = _selectedContainerId.asStateFlow()

    private val _logsState = MutableStateFlow<UiState<String>>(UiState.Idle)
    val logsState: StateFlow<UiState<String>> = _logsState.asStateFlow()

    private val _imageDraft = MutableStateFlow("")
    val imageDraft: StateFlow<String> = _imageDraft.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _isRunningAction = MutableStateFlow(false)
    val isRunningAction: StateFlow<Boolean> = _isRunningAction.asStateFlow()

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val messages = _messages.asSharedFlow()

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.DOCKMON].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val visibleContainers: StateFlow<List<DockmonContainer>> = combine(uiState, selectedHostId) { state, hostId ->
        val containers = (state as? UiState.Success)?.data?.containers.orEmpty()
        hostId?.let { selected -> containers.filter { it.hostId == selected } } ?: containers
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedContainer: StateFlow<DockmonContainer?> = combine(uiState, selectedContainerId) { state, selectedId ->
        val containers = (state as? UiState.Success)?.data?.containers.orEmpty()
        selectedId?.let { id -> containers.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        fetchDashboard(forceLoading = true)
    }

    fun fetchDashboard(forceLoading: Boolean = false) {
        viewModelScope.launch {
            if (forceLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                val data = repository.getDashboard(instanceId = instanceId, hostId = _selectedHostId.value)
                _uiState.value = UiState.Success(data)
                servicesRepository.markInstanceReachable(instanceId)

                val selectedId = _selectedContainerId.value
                if (selectedId != null && data.containers.none { it.id == selectedId }) {
                    closeContainer()
                }
            } catch (error: Exception) {
                _uiState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { fetchDashboard(forceLoading = true) }
                )
            } finally {
                _isRefreshing.value = false
            }
        }
    }

    fun selectHost(hostId: String?) {
        if (_selectedHostId.value == hostId) return
        _selectedHostId.value = hostId
        fetchDashboard(forceLoading = true)
    }

    fun openContainer(containerId: String) {
        _selectedContainerId.value = containerId
        _imageDraft.value = ""
        refreshLogs(forceLoading = true)
    }

    fun closeContainer() {
        _selectedContainerId.value = null
        _logsState.value = UiState.Idle
        _imageDraft.value = ""
    }

    fun updateImageDraft(value: String) {
        _imageDraft.value = value
    }

    fun refreshLogs(forceLoading: Boolean = false) {
        val containerId = _selectedContainerId.value ?: return
        viewModelScope.launch {
            if (forceLoading || _logsState.value !is UiState.Success) {
                _logsState.value = UiState.Loading
            }
            try {
                _logsState.value = UiState.Success(
                    repository.getContainerLogs(instanceId = instanceId, containerId = containerId)
                )
            } catch (error: Exception) {
                _logsState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { refreshLogs(forceLoading = true) }
                )
            }
        }
    }

    fun restartSelectedContainer() {
        val containerId = _selectedContainerId.value ?: return
        if (_isRunningAction.value) return
        viewModelScope.launch {
            _isRunningAction.value = true
            try {
                val result = repository.restartContainer(instanceId = instanceId, containerId = containerId)
                _messages.tryEmit(result.message.orEmpty().ifBlank { "Container restart requested." })
                fetchDashboard(forceLoading = false)
                refreshLogs(forceLoading = false)
            } catch (error: Exception) {
                _messages.tryEmit(ErrorHandler.getMessage(context, error))
            } finally {
                _isRunningAction.value = false
            }
        }
    }

    fun updateSelectedContainer() {
        val containerId = _selectedContainerId.value ?: return
        if (_isRunningAction.value) return
        viewModelScope.launch {
            _isRunningAction.value = true
            try {
                val result = repository.updateContainer(
                    instanceId = instanceId,
                    containerId = containerId,
                    image = _imageDraft.value
                )
                _messages.tryEmit(result.message.orEmpty().ifBlank { "Container update requested." })
                _imageDraft.value = ""
                fetchDashboard(forceLoading = false)
                refreshLogs(forceLoading = false)
            } catch (error: Exception) {
                _messages.tryEmit(ErrorHandler.getMessage(context, error))
            } finally {
                _isRunningAction.value = false
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.DOCKMON, newInstanceId)
        }
    }
}
