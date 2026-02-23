package com.homelab.app.ui.portainer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.portainer.PortainerContainer
import com.homelab.app.data.remote.dto.portainer.PortainerEndpoint
import com.homelab.app.data.repository.PortainerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PortainerViewModel @Inject constructor(
    private val repository: PortainerRepository
) : ViewModel() {

    private val _endpoints = MutableStateFlow<List<PortainerEndpoint>>(emptyList())
    val endpoints: StateFlow<List<PortainerEndpoint>> = _endpoints

    private val _selectedEndpoint = MutableStateFlow<PortainerEndpoint?>(null)
    val selectedEndpoint: StateFlow<PortainerEndpoint?> = _selectedEndpoint

    private val _containers = MutableStateFlow<List<PortainerContainer>>(emptyList())
    val containers: StateFlow<List<PortainerContainer>> = _containers

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun fetchAll() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                val eps = repository.getEndpoints()
                _endpoints.value = eps
                if (_selectedEndpoint.value == null && eps.isNotEmpty()) {
                    _selectedEndpoint.value = eps.first()
                }
                fetchContainers()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore caricamento Portainer"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun selectEndpoint(endpoint: PortainerEndpoint) {
        _selectedEndpoint.value = endpoint
        viewModelScope.launch {
            _error.value = null
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
                _error.value = e.localizedMessage ?: "Errore recupero containers"
            }
        }
    }
}
