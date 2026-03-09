package com.homelab.app.ui.portainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.portainer.PortainerContainer
import com.homelab.app.data.remote.dto.portainer.PortainerEndpoint
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.homelab.app.util.Logger
import javax.inject.Inject
import android.content.Context

@HiltViewModel
class PortainerViewModel @Inject constructor(
    private val repository: PortainerRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState

    private val _endpoints = MutableStateFlow<List<PortainerEndpoint>>(emptyList())
    val endpoints: StateFlow<List<PortainerEndpoint>> = _endpoints

    private val _selectedEndpoint = MutableStateFlow<PortainerEndpoint?>(null)
    val selectedEndpoint: StateFlow<PortainerEndpoint?> = _selectedEndpoint

    private val _containers = MutableStateFlow<List<PortainerContainer>>(emptyList())
    val containers: StateFlow<List<PortainerContainer>> = _containers

    init {
        _uiState.onEach { Logger.stateTransition("PortainerViewModel", "uiState", it) }.launchIn(viewModelScope)
    }

    fun fetchAll() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val eps = repository.getEndpoints()
                _endpoints.value = eps
                if (_selectedEndpoint.value == null && eps.isNotEmpty()) {
                    _selectedEndpoint.value = eps.first()
                }
                fetchContainers()
                _uiState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                val message = ErrorHandler.getMessage(context, e)
                _uiState.value = UiState.Error(message, retryAction = { fetchAll() })
            }
        }
    }

    fun selectEndpoint(endpoint: PortainerEndpoint) {
        _selectedEndpoint.value = endpoint
        viewModelScope.launch {
            fetchContainers() 
        }
    }

    private suspend fun fetchContainers() {
        val ep = _selectedEndpoint.value ?: return
        try {
            val conts = repository.getContainers(ep.id)
            _containers.value = conts
        } catch (e: Exception) {
            if (_containers.value.isEmpty()) {
                val message = ErrorHandler.getMessage(context, e)
                _uiState.value = UiState.Error(message, retryAction = { fetchAll() })
            }
        }
    }
}
