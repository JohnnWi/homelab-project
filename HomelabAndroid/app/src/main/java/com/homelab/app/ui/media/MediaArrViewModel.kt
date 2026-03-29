package com.homelab.app.ui.media

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.repository.MediaArrCardPreview
import com.homelab.app.data.repository.MediaArrRepository
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

private const val MEDIA_PREVIEW_MIN_INTERVAL_MS = 90_000L

data class MediaArrCardPreviewUiState(
    val preview: MediaArrCardPreview? = null,
    val isLoading: Boolean = false,
    val hasError: Boolean = false
)

@HiltViewModel
class MediaArrViewModel @Inject constructor(
    private val servicesRepository: ServicesRepository,
    private val localPreferencesRepository: LocalPreferencesRepository,
    private val mediaArrRepository: MediaArrRepository
) : ViewModel() {

    private val _cardPreviewState = MutableStateFlow<Map<String, MediaArrCardPreviewUiState>>(emptyMap())
    val cardPreviewState: StateFlow<Map<String, MediaArrCardPreviewUiState>> = _cardPreviewState

    private val lastPreviewLoadedAt = mutableMapOf<String, Long>()

    val instancesByType: StateFlow<Map<ServiceType, List<ServiceInstance>>> = servicesRepository.instancesByType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val reachability: StateFlow<Map<String, Boolean?>> = servicesRepository.reachability
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val pinging: StateFlow<Map<String, Boolean>> = servicesRepository.pinging
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val hiddenServices: StateFlow<Set<String>> = localPreferencesRepository.hiddenServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val homeCyberpunkCardsEnabled: StateFlow<Boolean> = localPreferencesRepository.homeCyberpunkCardsEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val serviceOrder: StateFlow<List<ServiceType>> = localPreferencesRepository.serviceOrder
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServiceType.entries.filter { it != ServiceType.UNKNOWN })

    val tutorialDismissed: StateFlow<Boolean> = localPreferencesRepository.mediaArrTutorialDismissed
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isTailscaleConnected: StateFlow<Boolean> = servicesRepository.isTailscaleConnected
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val mediaServiceOrder: StateFlow<List<ServiceType>> = combine(serviceOrder, hiddenServices) { order, hidden ->
        order.filter { it.isArrStack && !hidden.contains(it.name) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ServiceType.arrStackTypes)

    fun moveMediaService(serviceType: ServiceType, offset: Int) {
        if (!serviceType.isArrStack) return
        viewModelScope.launch {
            localPreferencesRepository.moveServiceWithin(
                serviceType = serviceType,
                offset = offset,
                within = ServiceType.arrStackTypes.toSet()
            )
        }
    }

    fun toggleServiceVisibility(serviceType: ServiceType) {
        if (serviceType == ServiceType.UNKNOWN) return
        viewModelScope.launch {
            localPreferencesRepository.toggleServiceVisibility(serviceType.name)
        }
    }

    fun dismissTutorial() {
        viewModelScope.launch {
            localPreferencesRepository.setMediaArrTutorialDismissed(true)
        }
    }

    fun resetTutorial() {
        viewModelScope.launch {
            localPreferencesRepository.setMediaArrTutorialDismissed(false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            servicesRepository.checkAllReachability()
            servicesRepository.checkTailscale()
            cleanupPreviewCache()
        }
    }

    fun requestCardPreview(instanceId: String, force: Boolean = false) {
        if (reachability.value[instanceId] == false) return
        val currentState = _cardPreviewState.value[instanceId]
        if (currentState?.isLoading == true) return

        val now = System.currentTimeMillis()
        if (!force) {
            val lastLoadedAt = lastPreviewLoadedAt[instanceId] ?: 0L
            if (now - lastLoadedAt < MEDIA_PREVIEW_MIN_INTERVAL_MS && currentState?.preview != null) {
                return
            }
        }

        val knownInstance = instancesByType.value.values.flatten().any { it.id == instanceId }
        if (!knownInstance) return

        _cardPreviewState.update { map ->
            map + (instanceId to MediaArrCardPreviewUiState(
                preview = currentState?.preview,
                isLoading = true,
                hasError = false
            ))
        }

        viewModelScope.launch {
            runCatching { mediaArrRepository.loadCardPreview(instanceId) }
                .onSuccess { preview ->
                    lastPreviewLoadedAt[instanceId] = System.currentTimeMillis()
                    _cardPreviewState.update { map ->
                        map + (instanceId to MediaArrCardPreviewUiState(
                            preview = preview,
                            isLoading = false,
                            hasError = false
                        ))
                    }
                }
                .onFailure {
                    _cardPreviewState.update { map ->
                        val cached = map[instanceId]?.preview
                        map + (instanceId to MediaArrCardPreviewUiState(
                            preview = cached,
                            isLoading = false,
                            hasError = true
                        ))
                    }
                }
        }
    }

    fun refreshInstance(instanceId: String) {
        viewModelScope.launch {
            servicesRepository.checkReachability(instanceId, force = true)
            if (reachability.value[instanceId] != false) {
                requestCardPreview(instanceId, force = true)
            }
        }
    }

    private fun cleanupPreviewCache() {
        val validIds = instancesByType.value.values.flatten().map { it.id }.toSet()
        lastPreviewLoadedAt.keys.retainAll(validIds)
        _cardPreviewState.update { current ->
            current.filterKeys { it in validIds }
        }
    }
}
