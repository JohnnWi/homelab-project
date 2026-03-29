package com.homelab.app.ui.immich

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.ImmichDashboardData
import com.homelab.app.data.repository.ImmichRepository
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

sealed interface ImmichUiState {
    data object Loading : ImmichUiState
    data class Success(val data: ImmichDashboardData) : ImmichUiState
    data class Error(val message: String) : ImmichUiState
}

@HiltViewModel
class ImmichViewModel @Inject constructor(
    private val repository: ImmichRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<ImmichUiState>(ImmichUiState.Loading)
    val uiState: StateFlow<ImmichUiState> = _uiState

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.IMMICH].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = ImmichUiState.Loading
            try {
                val data = repository.getDashboard(instanceId)
                _uiState.value = ImmichUiState.Success(data)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = ImmichUiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.IMMICH, newInstanceId)
        }
    }
}
