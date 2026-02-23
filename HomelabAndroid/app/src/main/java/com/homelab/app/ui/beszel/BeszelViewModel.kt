package com.homelab.app.ui.beszel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
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

    private val _records = MutableStateFlow<List<BeszelSystemRecord>>(emptyList())
    val records: StateFlow<List<BeszelSystemRecord>> = _records

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
                
                // Fetch records (non-critical, don't block if they fail)
                try {
                    val rawRecords = repository.getSystemRecords(systemId, limit = 60)
                    // The API returns newest records first. Sort chronologically so graphs plot left to right natively.
                    _records.value = rawRecords.sortedBy { it.created }
                } catch (e: Exception) {
                    // Log or ignore non-critical records failure
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
