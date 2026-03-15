package com.homelab.app.ui.beszel

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.beszel.BeszelContainer
import com.homelab.app.data.remote.dto.beszel.BeszelRecordStats
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetails
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.Logger
import com.homelab.app.util.ServiceType
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class BeszelViewModel @Inject constructor(
    private val repository: BeszelRepository,
    private val servicesRepository: ServicesRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private var systemDetailRequestToken: Long = 0

    val instanceId: String = checkNotNull(savedStateHandle["instanceId"])

    private val _systemsState = MutableStateFlow<UiState<List<BeszelSystem>>>(UiState.Loading)
    val systemsState: StateFlow<UiState<List<BeszelSystem>>> = _systemsState

    private val _systemDetailState = MutableStateFlow<UiState<BeszelSystem>>(UiState.Loading)
    val systemDetailState: StateFlow<UiState<BeszelSystem>> = _systemDetailState

    private val _systemDetails = MutableStateFlow<BeszelSystemDetails?>(null)
    val systemDetails: StateFlow<BeszelSystemDetails?> = _systemDetails

    private val _records = MutableStateFlow<List<BeszelSystemRecord>>(emptyList())
    val records: StateFlow<List<BeszelSystemRecord>> = _records

    private val _smartDevices = MutableStateFlow<List<BeszelSmartDevice>>(emptyList())
    val smartDevices: StateFlow<List<BeszelSmartDevice>> = _smartDevices

    val instances: StateFlow<List<ServiceInstance>> = servicesRepository.instancesByType
        .map { it[ServiceType.BESZEL].orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    internal val systemDetailUiModel: StateFlow<BeszelSystemDetailUiModel?> = combine(
        _systemDetailState,
        _systemDetails,
        _records,
        _smartDevices
    ) { state, details, records, devices ->
        val system = (state as? UiState.Success)?.data ?: return@combine null
        buildSystemDetailUiModel(
            system = system,
            details = details,
            records = records,
            smartDevices = devices
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        _systemsState.onEach { Logger.stateTransition("BeszelViewModel", "systemsState", it) }
            .launchIn(viewModelScope)
        _systemDetailState.onEach { Logger.stateTransition("BeszelViewModel", "systemDetailState", it) }
            .launchIn(viewModelScope)
    }

    fun fetchSystems() {
        viewModelScope.launch {
            _systemsState.value = UiState.Loading
            try {
                _systemsState.value = UiState.Success(repository.getSystems(instanceId))
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _systemsState.value = UiState.Error(message, retryAction = { fetchSystems() })
            }
        }
    }

    fun fetchSystemDetail(systemId: String) {
        viewModelScope.launch {
            val requestToken = ++systemDetailRequestToken
            _systemDetailState.value = UiState.Loading
            _systemDetails.value = null
            _records.value = emptyList()
            _smartDevices.value = emptyList()

            try {
                val system = repository.getSystem(instanceId, systemId)
                if (requestToken != systemDetailRequestToken) return@launch
                _systemDetailState.value = UiState.Success(system)

                launch {
                    try {
                        val details = repository.getSystemDetails(instanceId, systemId)
                        if (requestToken == systemDetailRequestToken) {
                            _systemDetails.value = details
                        }
                    } catch (_: Exception) {
                        if (requestToken == systemDetailRequestToken) {
                            _systemDetails.value = null
                        }
                    }
                }

                launch {
                    try {
                        val rawRecords = repository.getSystemRecords(instanceId, systemId, limit = 60)
                        if (requestToken == systemDetailRequestToken) {
                            _records.value = rawRecords.sortedBy { it.created }
                        }
                    } catch (_: Exception) {
                        if (requestToken == systemDetailRequestToken) {
                            _records.value = emptyList()
                        }
                    }
                }

                launch {
                    try {
                        val devices = repository.getSmartDevices(instanceId, systemId)
                        if (requestToken == systemDetailRequestToken) {
                            _smartDevices.value = devices
                        }
                    } catch (_: Exception) {
                        if (requestToken == systemDetailRequestToken) {
                            _smartDevices.value = emptyList()
                        }
                    }
                }
            } catch (error: Exception) {
                if (requestToken != systemDetailRequestToken) return@launch
                val message = ErrorHandler.getMessage(context, error)
                _systemDetailState.value = UiState.Error(message, retryAction = { fetchSystemDetail(systemId) })
            }
        }
    }

    fun setPreferredInstance(newInstanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(ServiceType.BESZEL, newInstanceId)
        }
    }

    private fun buildSystemDetailUiModel(
        system: BeszelSystem,
        details: BeszelSystemDetails?,
        records: List<BeszelSystemRecord>,
        smartDevices: List<BeszelSmartDevice>
    ): BeszelSystemDetailUiModel {
        val info = system.info
        val statsHistory = records.map(BeszelSystemRecord::stats)
        val recentStats = statsHistory.takeLast(30)
        val latestStats = statsHistory.lastOrNull()
        val diskUsedGb = (latestStats?.duValue ?: info?.duValue)?.takeIf { it > 0.0 }
        val diskTotalGb = (latestStats?.dValue ?: info?.dValue)?.takeIf { it > 0.0 }
        val memoryUsedGb = latestStats?.memoryUsedGb
        val memoryTotalGb = latestStats?.memoryTotalGb ?: info?.mValue?.takeIf { it > 0.0 }
        val externalFileSystems = latestStats?.efs
            ?.mapNotNull { (label, entry) ->
                val total = entry.d ?: return@mapNotNull null
                val used = entry.du ?: return@mapNotNull null
                if (total <= 0.0 || used < 0.0) return@mapNotNull null
                DiskFsUsage(label = label, usedGb = used, totalGb = total)
            }
            .orEmpty()
        val dockerSummary = latestStats?.dockerSummary
        val dockerUploadRateHistory = recentStats.containerSeries { it.bandwidthUpBytesPerSec }
        val dockerDownloadRateHistory = recentStats.containerSeries { it.bandwidthDownBytesPerSec }

        return BeszelSystemDetailUiModel(
            system = system,
            systemDetails = details,
            statsHistory = statsHistory,
            latestStats = latestStats,
            smartDevices = smartDevices,
            cpuHistoryPercent = recentStats.map { it.cpuValue },
            memoryHistoryPercent = recentStats.map { it.mpValue },
            memoryUsedHistoryGb = recentStats.mapNotNull { it.memoryUsedGb },
            diskUsedGb = diskUsedGb,
            diskTotalGb = diskTotalGb,
            memoryUsedGb = memoryUsedGb,
            memoryTotalGb = memoryTotalGb,
            externalFileSystems = externalFileSystems,
            dockerSummary = dockerSummary,
            dockerCpuHistoryPercent = recentStats.containerSeries { it.cpuValue },
            dockerMemoryUsedHistoryMb = recentStats.containerSeries { it.mValue },
            dockerUploadRateHistoryBytesPerSec = dockerUploadRateHistory,
            dockerDownloadRateHistoryBytesPerSec = dockerDownloadRateHistory,
            hasDockerNetwork = dockerSummary?.let { summary ->
                summary.uploadRateBytesPerSec != null &&
                    summary.downloadRateBytesPerSec != null &&
                    dockerUploadRateHistory.isNotEmpty() &&
                    dockerDownloadRateHistory.size == dockerUploadRateHistory.size
            } == true,
            containers = latestStats?.dc.orEmpty(),
            perCoreCpuPercent = latestStats?.cpuCoreUsageValues.orEmpty()
        )
    }
}

private val BeszelRecordStats.dockerSummary: DockerMetricSummary?
    get() = dc?.takeIf { it.isNotEmpty() }?.toDockerMetricSummary()

private fun List<BeszelRecordStats>.containerSeries(
    selector: (BeszelContainer) -> Double?
): List<Double> = mapNotNull { stats ->
    stats.dc?.sumNullable(selector)
}

private fun List<BeszelContainer>.toDockerMetricSummary(): DockerMetricSummary = DockerMetricSummary(
    cpuPercent = sumOf(BeszelContainer::cpuValue),
    memoryUsedMb = sumOf(BeszelContainer::mValue),
    uploadRateBytesPerSec = sumNullable(BeszelContainer::bandwidthUpBytesPerSec),
    downloadRateBytesPerSec = sumNullable(BeszelContainer::bandwidthDownBytesPerSec)
)

private fun List<BeszelContainer>.sumNullable(
    selector: (BeszelContainer) -> Double?
): Double? = mapNotNull(selector).takeIf { it.isNotEmpty() }?.sum()
