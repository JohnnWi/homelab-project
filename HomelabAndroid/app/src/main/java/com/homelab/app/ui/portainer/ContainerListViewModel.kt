package com.homelab.app.ui.portainer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.portainer.ContainerAction
import com.homelab.app.data.remote.dto.portainer.PortainerContainer
import com.homelab.app.data.repository.PortainerRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class ContainerFilter { ALL, RUNNING, STOPPED }

@HiltViewModel
class ContainerListViewModel @Inject constructor(
    private val repository: PortainerRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val endpointId: Int = checkNotNull(savedStateHandle["endpointId"])

    private val _containers = MutableStateFlow<List<PortainerContainer>>(emptyList())
    
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery

    private val _filter = MutableStateFlow(ContainerFilter.ALL)
    val filter: StateFlow<ContainerFilter> = _filter

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    private val _actionInProgress = MutableStateFlow<String?>(null)
    val actionInProgress: StateFlow<String?> = _actionInProgress

    val filteredContainers: StateFlow<List<PortainerContainer>> = combine(
        _containers, _searchQuery, _filter
    ) { containers, query, filter ->
        var list = containers
        list = when (filter) {
            ContainerFilter.ALL -> list
            ContainerFilter.RUNNING -> list.filter { it.state == "running" }
            ContainerFilter.STOPPED -> list.filter { it.state == "exited" || it.state == "dead" }
        }
        if (query.isNotBlank()) {
            val q = query.lowercase()
            list = list.filter { it.displayName.lowercase().contains(q) || it.image.lowercase().contains(q) }
        }
        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val counts: StateFlow<Map<ContainerFilter, Int>> = _containers.map { list ->
        mapOf(
            ContainerFilter.ALL to list.size,
            ContainerFilter.RUNNING to list.count { it.state == "running" },
            ContainerFilter.STOPPED to list.count { it.state == "exited" || it.state == "dead" }
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    init {
        fetchContainers()
    }

    fun fetchContainers() {
        viewModelScope.launch {
            if (_containers.value.isEmpty()) _isLoading.value = true
            _error.value = null
            try {
                _containers.value = repository.getContainers(endpointId)
            } catch (e: Exception) {
                if (_containers.value.isEmpty()) {
                    _error.value = e.localizedMessage ?: "Errore recupero containers"
                }
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setFilter(filter: ContainerFilter) {
        _filter.value = filter
    }

    fun clearError() {
        _error.value = null
    }

    fun performAction(containerId: String, action: ContainerAction) {
        viewModelScope.launch {
            _actionInProgress.value = containerId
            try {
                when (action) {
                    ContainerAction.start -> repository.startContainer(endpointId, containerId)
                    ContainerAction.stop -> repository.stopContainer(endpointId, containerId)
                    ContainerAction.restart -> repository.restartContainer(endpointId, containerId)
                    ContainerAction.kill -> repository.killContainer(endpointId, containerId)
                    ContainerAction.pause -> repository.pauseContainer(endpointId, containerId)
                    ContainerAction.unpause -> repository.unpauseContainer(endpointId, containerId)
                }
                fetchContainers()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Impossibile eseguire l'azione"
            } finally {
                _actionInProgress.value = null
            }
        }
    }
}
