package com.homelab.app.ui.wakapi

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.wakapi.WakapiDailySummariesResponse
import com.homelab.app.data.remote.dto.wakapi.WakapiSummaryResponse
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.WakapiSummaryFilter
import com.homelab.app.data.repository.WakapiRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WakapiViewModel @Inject constructor(
    private val wakapiRepository: WakapiRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _summaryState = MutableStateFlow<UiState<WakapiSummaryResponse>>(UiState.Loading)
    val summaryState: StateFlow<UiState<WakapiSummaryResponse>> = _summaryState.asStateFlow()

    private val _activityState = MutableStateFlow<WakapiDailySummariesResponse?>(null)
    val activityState: StateFlow<WakapiDailySummariesResponse?> = _activityState.asStateFlow()

    private val _selectedInterval = MutableStateFlow("today")
    val selectedInterval: StateFlow<String> = _selectedInterval.asStateFlow()

    private val _selectedFilter = MutableStateFlow<WakapiSummaryFilter?>(null)
    val selectedFilter: StateFlow<WakapiSummaryFilter?> = _selectedFilter.asStateFlow()

    private val _isRefreshing = MutableStateFlow(false)
    val isRefreshing: StateFlow<Boolean> = _isRefreshing.asStateFlow()

    private var fetchJob: Job? = null
    private var fetchRequestId: Long = 0L

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.WAKAPI].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        refreshAll(forceLoading = true)
    }

    fun fetchSummary(forceLoading: Boolean = false) {
        val requestId = ++fetchRequestId
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (forceLoading || _summaryState.value !is UiState.Success) {
                _summaryState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                val interval = _selectedInterval.value
                val filter = _selectedFilter.value
                val response = wakapiRepository.getSummary(
                    instanceId = instanceId,
                    interval = interval,
                    filter = filter,
                    forceRefresh = forceLoading
                )
                _summaryState.value = UiState.Success(response)
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _summaryState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { refreshAll(forceLoading = true) }
                )
            } finally {
                if (requestId == fetchRequestId) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun fetchRecentActivity(forceRefresh: Boolean = false) {
        viewModelScope.launch {
            val filter = _selectedFilter.value
            _activityState.value = runCatching {
                wakapiRepository.getDailySummaries(
                    instanceId = instanceId,
                    range = RECENT_ACTIVITY_RANGE,
                    filter = filter,
                    forceRefresh = forceRefresh
                )
            }.getOrNull()
        }
    }

    fun refreshAll(forceLoading: Boolean = false) {
        val requestId = ++fetchRequestId
        fetchJob?.cancel()
        fetchJob = viewModelScope.launch {
            if (forceLoading || _summaryState.value !is UiState.Success) {
                _summaryState.value = UiState.Loading
            }
            _isRefreshing.value = true
            try {
                val interval = _selectedInterval.value
                val filter = _selectedFilter.value
                val summaryDeferred = async {
                    wakapiRepository.getSummary(
                        instanceId = instanceId,
                        interval = interval,
                        filter = filter,
                        forceRefresh = forceLoading
                    )
                }
                val activityDeferred = async {
                    runCatching {
                        wakapiRepository.getDailySummaries(
                            instanceId = instanceId,
                            range = RECENT_ACTIVITY_RANGE,
                            filter = filter,
                            forceRefresh = forceLoading
                        )
                    }.getOrNull()
                }

                _summaryState.value = UiState.Success(summaryDeferred.await())
                _activityState.value = activityDeferred.await()
            } catch (cancellation: CancellationException) {
                throw cancellation
            } catch (error: Exception) {
                _summaryState.value = UiState.Error(
                    message = ErrorHandler.getMessage(context, error),
                    retryAction = { refreshAll(forceLoading = true) }
                )
            } finally {
                if (requestId == fetchRequestId) {
                    _isRefreshing.value = false
                }
            }
        }
    }

    fun setInterval(interval: String) {
        if (_selectedInterval.value == interval) return
        _selectedInterval.value = interval
        fetchSummary(forceLoading = true)
    }

    fun setFilter(filter: WakapiSummaryFilter) {
        if (_selectedFilter.value == filter) return
        _selectedFilter.value = filter
        refreshAll(forceLoading = true)
    }

    fun clearFilter() {
        if (_selectedFilter.value == null) return
        _selectedFilter.value = null
        refreshAll(forceLoading = true)
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.WAKAPI, newInstanceId)
        }
    }

    private companion object {
        const val RECENT_ACTIVITY_RANGE = "last_6_months"
    }
}
