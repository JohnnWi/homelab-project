package com.homelab.app.ui.tdarr

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.TdarrDashboardData
import com.homelab.app.data.repository.TdarrRepository
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

sealed interface TdarrUiState {
    data object Loading : TdarrUiState
    data class Success(val data: TdarrDashboardData) : TdarrUiState
    data class Error(val message: String) : TdarrUiState
}

@HiltViewModel
class TdarrViewModel @Inject constructor(
    private val repository: TdarrRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<TdarrUiState>(TdarrUiState.Loading)
    val uiState: StateFlow<TdarrUiState> = _uiState

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.TDARR].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = TdarrUiState.Loading
            try {
                val data = repository.getDashboard(instanceId)
                _uiState.value = TdarrUiState.Success(data)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = TdarrUiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.TDARR, newInstanceId)
        }
    }
}
