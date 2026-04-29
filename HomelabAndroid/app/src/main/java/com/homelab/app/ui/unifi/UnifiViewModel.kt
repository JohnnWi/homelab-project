package com.homelab.app.ui.unifi

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.UnifiDashboardData
import com.homelab.app.data.repository.UnifiRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class UnifiViewModel @Inject constructor(
    private val repository: UnifiRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<UnifiDashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<UnifiDashboardData>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _selectedSiteId = MutableStateFlow<String?>(null)
    val selectedSiteId: StateFlow<String?> = _selectedSiteId.asStateFlow()

    private val _isDemo = MutableStateFlow(false)
    val isDemo: StateFlow<Boolean> = _isDemo.asStateFlow()

    private val _actionMessage = MutableStateFlow<String?>(null)
    val actionMessage: StateFlow<String?> = _actionMessage.asStateFlow()

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.UNIFI_NETWORK].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        fetchDashboard(forceLoading = true)
    }

    fun fetchDashboard(forceLoading: Boolean = false) {
        if (_isRefreshing.value) return
        viewModelScope.launch {
            if (_isRefreshing.value) return@launch
            if (_isDemo.value && !forceLoading) {
                _uiState.value = UiState.Success(repository.demoDashboard())
                return@launch
            }
            if (forceLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                _isDemo.value = false
                val data = repository.getDashboard(instanceId)
                _uiState.value = UiState.Success(data)
                if (_selectedSiteId.value != null && data.sites.none { it.id == _selectedSiteId.value }) {
                    _selectedSiteId.value = null
                }
                servicesRepository.markInstanceReachable(instanceId)
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

    fun selectSite(siteId: String?) {
        _selectedSiteId.value = siteId
    }

    fun openDemo() {
        _selectedSiteId.value = null
        _actionMessage.value = null
        _isDemo.value = true
        _uiState.value = UiState.Success(repository.demoDashboard())
    }

    fun authorizeGuest(siteId: String, clientId: String) {
        viewModelScope.launch {
            try {
                if (_isDemo.value) {
                    val current = (_uiState.value as? UiState.Success)?.data ?: return@launch
                    _uiState.value = UiState.Success(
                        current.copy(
                            clients = current.clients.map { client ->
                                if (client.id == clientId) client.copy(authorized = true) else client
                            }
                        )
                    )
                } else {
                    repository.authorizeGuest(instanceId, siteId, clientId)
                    fetchDashboard(forceLoading = false)
                }
                _actionMessage.value = context.getString(com.homelab.app.R.string.unifi_guest_authorized)
            } catch (error: Exception) {
                _actionMessage.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.UNIFI_NETWORK, newInstanceId)
        }
    }
}
