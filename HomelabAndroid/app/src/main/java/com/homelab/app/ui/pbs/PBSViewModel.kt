package com.homelab.app.ui.pbs

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.PBSDashboardData
import com.homelab.app.data.repository.PBSRepository
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

sealed interface PBSUiState {
    data object Loading : PBSUiState
    data class Success(val data: PBSDashboardData) : PBSUiState
    data class Error(val message: String) : PBSUiState
}

@HiltViewModel
class PBSViewModel @Inject constructor(
    private val repository: PBSRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<PBSUiState>(PBSUiState.Loading)
    val uiState: StateFlow<PBSUiState> = _uiState

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.PROXMOX_BACKUP_SERVER].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var refreshJob: Job? = null

    init {
        refresh()
    }

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            _uiState.value = PBSUiState.Loading
            try {
                val data = repository.getDashboard(instanceId)
                _uiState.value = PBSUiState.Success(data)
            } catch (error: Exception) {
                if (error is CancellationException) throw error
                _uiState.value = PBSUiState.Error(ErrorHandler.getMessage(context, error))
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.PROXMOX_BACKUP_SERVER, newInstanceId)
        }
    }
}
