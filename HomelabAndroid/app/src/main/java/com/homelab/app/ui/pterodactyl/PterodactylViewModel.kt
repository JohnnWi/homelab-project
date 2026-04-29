package com.homelab.app.ui.pterodactyl

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.pterodactyl.PterodactylResources
import com.homelab.app.data.remote.dto.pterodactyl.PterodactylServer
import com.homelab.app.data.repository.PterodactylDashboardData
import com.homelab.app.data.repository.PterodactylRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class PterodactylServerWithResources(
    val server: PterodactylServer,
    val resources: PterodactylResources?
)

@HiltViewModel
class PterodactylViewModel @Inject constructor(
    private val repository: PterodactylRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<List<PterodactylServerWithResources>>>(UiState.Loading)
    val uiState: StateFlow<UiState<List<PterodactylServerWithResources>>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _actionServerId = MutableStateFlow<String?>(null)
    val actionServerId: StateFlow<String?> = _actionServerId.asStateFlow()

    private val _messages = MutableSharedFlow<String>()
    val messages: SharedFlow<String> = _messages.asSharedFlow()

    private var refreshJob: Job? = null
    private var refreshRequestId: Long = 0L

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.PTERODACTYL].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refresh(forceLoading = true)
    }

    fun refresh(forceLoading: Boolean = false) {
        val requestId = ++refreshRequestId
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            if (forceLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                val servers = repository.getServers(instanceId)
                val enriched = coroutineScope {
                    servers.chunked(4).flatMap { chunk ->
                        chunk.map { server ->
                            async {
                                val res = runCatching {
                                    repository.getServerResources(instanceId, server.identifier)
                                }.getOrNull()
                                PterodactylServerWithResources(server = server, resources = res)
                            }
                        }.awaitAll()
                    }
                }
                _uiState.value = UiState.Success(enriched)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _uiState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, e),
                    retryAction = { refresh(forceLoading = true) }
                )
            } finally {
                if (requestId == refreshRequestId) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun sendPowerSignal(identifier: String, signal: String) {
        viewModelScope.launch {
            _actionServerId.value = identifier
            try {
                repository.sendPowerSignal(instanceId, identifier, signal)
                _messages.emit(context.getString(com.homelab.app.R.string.pterodactyl_action_sent))
                // Poll for state change
                repeat(6) {
                    delay(1500L)
                    runCatching {
                        val updated = repository.getServerResources(instanceId, identifier)
                        val current = _uiState.value
                        if (current is UiState.Success) {
                            _uiState.value = UiState.Success(
                                current.data.map { s ->
                                    if (s.server.identifier == identifier) s.copy(resources = updated) else s
                                }
                            )
                        }
                    }
                }
            } catch (e: Exception) {
                _messages.emit(ErrorHandler.getMessage(context, e))
            } finally {
                _actionServerId.value = null
            }
        }
    }
}
