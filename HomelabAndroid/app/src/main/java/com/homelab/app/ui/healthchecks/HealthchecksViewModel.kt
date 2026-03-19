package com.homelab.app.ui.healthchecks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksBadgeFormats
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksChannel
import com.homelab.app.data.remote.dto.healthchecks.HealthchecksCheck
import com.homelab.app.data.repository.HealthchecksRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class HealthchecksViewModel @Inject constructor(
    private val repository: HealthchecksRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _checks = MutableStateFlow<List<HealthchecksCheck>>(emptyList())
    val checks: StateFlow<List<HealthchecksCheck>> = _checks

    private val _channels = MutableStateFlow<List<HealthchecksChannel>>(emptyList())
    val channels: StateFlow<List<HealthchecksChannel>> = _channels

    private val _badges = MutableStateFlow<Map<String, HealthchecksBadgeFormats>>(emptyMap())
    val badges: StateFlow<Map<String, HealthchecksBadgeFormats>> = _badges

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.HEALTHCHECKS].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun fetchAll() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val checksTask = async { repository.listChecks(instanceId) }
                val channelsTask = async { repository.listChannels(instanceId) }
                _checks.value = checksTask.await()
                _channels.value = channelsTask.await()
                _uiState.value = UiState.Success(Unit)
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _uiState.value = UiState.Error(message, retryAction = { fetchAll() })
            }
        }
    }

    fun fetchBadges() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                _badges.value = repository.listBadges(instanceId).badges
                _uiState.value = UiState.Success(Unit)
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _uiState.value = UiState.Error(message, retryAction = { fetchBadges() })
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.HEALTHCHECKS, newInstanceId)
        }
    }
}
