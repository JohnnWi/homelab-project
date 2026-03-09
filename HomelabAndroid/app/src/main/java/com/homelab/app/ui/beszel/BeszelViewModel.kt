package com.homelab.app.ui.beszel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetails
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.Logger
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import javax.inject.Inject
import android.content.Context

@HiltViewModel
class BeszelViewModel @Inject constructor(
    private val repository: BeszelRepository,
    @param:ApplicationContext private val context: Context
) : ViewModel() {
    private var systemDetailRequestToken: Long = 0

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
                val systems = repository.getSystems()
                _systemsState.value = UiState.Success(systems)
            } catch (e: Exception) {
                val message = ErrorHandler.getMessage(context, e)
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
                val system = repository.getSystem(systemId)
                if (requestToken != systemDetailRequestToken) return@launch
                _systemDetailState.value = UiState.Success(system)

                // Fire-and-forget: extended system details (non-critical)
                launch {
                    try {
                        val details = repository.getSystemDetails(systemId)
                        if (requestToken == systemDetailRequestToken) {
                            _systemDetails.value = details
                        }
                    } catch (_: Exception) {
                        if (requestToken == systemDetailRequestToken) {
                            _systemDetails.value = null
                        }
                    }
                }

                // Fire-and-forget: records (non-critical)
                launch {
                    try {
                        val rawRecords = repository.getSystemRecords(systemId, limit = 60)
                        // The API returns newest records first. Sort chronologically so graphs plot left to right natively.
                        if (requestToken == systemDetailRequestToken) {
                            _records.value = rawRecords.sortedBy { it.created }
                        }
                    } catch (_: Exception) {
                        if (requestToken == systemDetailRequestToken) {
                            _records.value = emptyList()
                        }
                    }
                }

                // Fire-and-forget: SMART devices (non-critical, may not be configured)
                launch {
                    try {
                        val devices = repository.getSmartDevices(systemId)
                        if (requestToken == systemDetailRequestToken) {
                            _smartDevices.value = devices
                        }
                    } catch (_: Exception) {
                        if (requestToken == systemDetailRequestToken) {
                            _smartDevices.value = emptyList()
                        }
                    }
                }
            } catch (e: Exception) {
                if (requestToken != systemDetailRequestToken) return@launch
                val message = ErrorHandler.getMessage(context, e)
                _systemDetailState.value = UiState.Error(message, retryAction = { fetchSystemDetail(systemId) })
            }
        }
    }
}
