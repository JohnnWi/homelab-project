package com.homelab.app.ui.sabnzbd

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.SABnzbdDashboardData
import com.homelab.app.data.repository.SABnzbdRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

sealed interface SABnzbdUiState {
    data object Loading : SABnzbdUiState
    data class Success(val data: SABnzbdDashboardData) : SABnzbdUiState
    data class Error(val message: String) : SABnzbdUiState
}

@HiltViewModel
class SABnzbdViewModel @Inject constructor(
    private val repository: SABnzbdRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<SABnzbdUiState>(SABnzbdUiState.Loading)
    val uiState: StateFlow<SABnzbdUiState> = _uiState

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.SABNZBD].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = SABnzbdUiState.Loading
            try {
                val data = repository.getDashboard(instanceId)
                _uiState.value = SABnzbdUiState.Success(data)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = SABnzbdUiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.SABNZBD, newInstanceId)
        }
    }
}
