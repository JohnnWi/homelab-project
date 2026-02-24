package com.homelab.app.ui.beszel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetails
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import com.homelab.app.data.repository.BeszelRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class BeszelViewModel @Inject constructor(
    private val repository: BeszelRepository
) : ViewModel() {

    private val _systems = MutableStateFlow<List<BeszelSystem>>(emptyList())
    val systems: StateFlow<List<BeszelSystem>> = _systems

    private val _selectedSystem = MutableStateFlow<BeszelSystem?>(null)
    val selectedSystem: StateFlow<BeszelSystem?> = _selectedSystem

    private val _systemDetails = MutableStateFlow<BeszelSystemDetails?>(null)
    val systemDetails: StateFlow<BeszelSystemDetails?> = _systemDetails

    private val _records = MutableStateFlow<List<BeszelSystemRecord>>(emptyList())
    val records: StateFlow<List<BeszelSystemRecord>> = _records

    private val _smartDevices = MutableStateFlow<List<BeszelSmartDevice>>(emptyList())
    val smartDevices: StateFlow<List<BeszelSmartDevice>> = _smartDevices

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun fetchSystems() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                _systems.value = repository.getSystems()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore caricamento server Beszel"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun fetchSystemDetail(systemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            try {
                // Fetch basic system info first
                val s = repository.getSystem(systemId)
                _selectedSystem.value = s

                // Fire-and-forget: extended system details (non-critical)
                launch {
                    try {
                        _systemDetails.value = repository.getSystemDetails(systemId)
                    } catch (_: Exception) {
                        // Ignore non-critical details failure
                    }
                }

                // Fire-and-forget: records (non-critical)
                launch {
                    try {
                        val rawRecords = repository.getSystemRecords(systemId)
                        // The API returns newest records first. Sort chronologically so graphs plot left to right natively.
                        _records.value = rawRecords.sortedBy { it.created }
                    } catch (_: Exception) {
                        // Ignore non-critical records failure
                    }
                }

                // Fire-and-forget: SMART devices (non-critical, may not be configured)
                launch {
                    try {
                        _smartDevices.value = repository.getSmartDevices(systemId)
                    } catch (_: Exception) {
                        _smartDevices.value = emptyList()
                    }
                }
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore caricamento dettagli sistema"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
