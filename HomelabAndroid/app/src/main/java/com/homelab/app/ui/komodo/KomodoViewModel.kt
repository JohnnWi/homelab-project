package com.homelab.app.ui.komodo

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.KomodoDashboardData
import com.homelab.app.data.repository.KomodoRepository
import com.homelab.app.data.repository.KomodoStackAction
import com.homelab.app.data.repository.KomodoStackDetail
import com.homelab.app.data.repository.KomodoStackItem
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class KomodoViewModel @Inject constructor(
    private val repository: KomodoRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _uiState = MutableStateFlow<UiState<KomodoDashboardData>>(UiState.Loading)
    val uiState: StateFlow<UiState<KomodoDashboardData>> = _uiState.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private val _stacksState = MutableStateFlow<UiState<List<KomodoStackItem>>>(UiState.Idle)
    val stacksState: StateFlow<UiState<List<KomodoStackItem>>> = _stacksState.asStateFlow()

    private val _stackDetailState = MutableStateFlow<UiState<KomodoStackDetail>>(UiState.Idle)
    val stackDetailState: StateFlow<UiState<KomodoStackDetail>> = _stackDetailState.asStateFlow()

    private val _isRunningStackAction = MutableStateFlow(false)
    val isRunningStackAction: StateFlow<Boolean> = _isRunningStackAction.asStateFlow()

    private val _events = MutableSharedFlow<KomodoUiEvent>()
    val events: SharedFlow<KomodoUiEvent> = _events

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.KOMODO].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        fetchDashboard(forceLoading = true)
    }

    fun fetchDashboard(forceLoading: Boolean = false) {
        viewModelScope.launch {
            if (forceLoading || _uiState.value !is UiState.Success) {
                _uiState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                val data = repository.getDashboard(instanceId)
                _uiState.value = UiState.Success(data)
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

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.KOMODO, newInstanceId)
        }
    }

    fun loadStacks() {
        viewModelScope.launch {
            _stacksState.value = UiState.Loading
            try {
                _stacksState.value = UiState.Success(repository.getStacks(instanceId))
            } catch (error: Exception) {
                _stacksState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadStacks() }
                )
            }
        }
    }

    fun loadStackDetail(stack: KomodoStackItem) {
        loadStackDetail(stack.id, stack)
    }

    private fun loadStackDetail(stackId: String, fallbackStack: KomodoStackItem? = null) {
        viewModelScope.launch {
            _stackDetailState.value = UiState.Loading
            try {
                val detail = repository.getStackDetail(instanceId, stackId)
                _stackDetailState.value = UiState.Success(detail.withFallback(fallbackStack))
            } catch (error: Exception) {
                _stackDetailState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { loadStackDetail(stackId, fallbackStack) }
                )
            }
        }
    }

    fun clearStackDetail() {
        _stackDetailState.value = UiState.Idle
    }

    fun runStackAction(stackId: String, action: KomodoStackAction) {
        viewModelScope.launch {
            _isRunningStackAction.value = true
            try {
                repository.executeStackAction(instanceId, stackId, action)
                _events.emit(KomodoUiEvent.StackActionSucceeded(action))
                val fallbackStack = (_stackDetailState.value as? UiState.Success)?.data?.stack
                    ?.takeIf { it.id == stackId }
                loadStackDetail(stackId, fallbackStack)
                loadStacks()
                fetchDashboard(forceLoading = false)
            } catch (error: Exception) {
                _events.emit(KomodoUiEvent.StackActionFailed(ErrorHandler.getMessage(context, error)))
            } finally {
                _isRunningStackAction.value = false
            }
        }
    }
}

private fun KomodoStackDetail.withFallback(fallback: KomodoStackItem?): KomodoStackDetail {
    if (fallback == null) return this
    val mergedStack = stack.copy(
        name = stack.name.takeUnless { it == stack.id || it.isBlank() } ?: fallback.name,
        status = stack.status.takeUnless { it.isBlank() || it.equals("Unknown", ignoreCase = true) } ?: fallback.status,
        server = stack.server ?: fallback.server,
        project = stack.project ?: fallback.project,
        updateAvailable = stack.updateAvailable || fallback.updateAvailable
    )
    return copy(stack = mergedStack)
}

sealed interface KomodoUiEvent {
    data class StackActionSucceeded(val action: KomodoStackAction) : KomodoUiEvent
    data class StackActionFailed(val message: String) : KomodoUiEvent
}
