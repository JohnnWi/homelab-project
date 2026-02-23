package com.homelab.app.ui.pihole

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.pihole.*
import com.homelab.app.data.repository.PiholeRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PiholeViewModel @Inject constructor(
    private val repository: PiholeRepository
) : ViewModel() {

    private val _stats = MutableStateFlow<PiholeStats?>(null)
    val stats: StateFlow<PiholeStats?> = _stats

    private val _blocking = MutableStateFlow<PiholeBlockingStatus?>(null)
    val blocking: StateFlow<PiholeBlockingStatus?> = _blocking

    private val _topBlocked = MutableStateFlow<List<PiholeTopItem>>(emptyList())
    val topBlocked: StateFlow<List<PiholeTopItem>> = _topBlocked

    private val _topDomains = MutableStateFlow<List<PiholeTopItem>>(emptyList())
    val topDomains: StateFlow<List<PiholeTopItem>> = _topDomains

    private val _topClients = MutableStateFlow<List<PiholeTopClient>>(emptyList())
    val topClients: StateFlow<List<PiholeTopClient>> = _topClients

    private val _history = MutableStateFlow<List<PiholeHistoryEntry>>(emptyList())
    val history: StateFlow<List<PiholeHistoryEntry>> = _history

    private val _isLoading = MutableStateFlow(true)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _isToggling = MutableStateFlow(false)
    val isToggling: StateFlow<Boolean> = _isToggling

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun fetchAll() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // Critical requests first
                val s = repository.getStats()
                val b = repository.getBlockingStatus()
                
                _stats.value = s
                _blocking.value = b

                // Parallel non-critical requests
                val tbDeferred = async { runCatching { repository.getTopBlocked(8) }.getOrDefault(emptyList()) }
                val tdDeferred = async { runCatching { repository.getTopDomains(10) }.getOrDefault(emptyList()) }
                val tcDeferred = async { runCatching { repository.getTopClients(10) }.getOrDefault(emptyList()) }
                val qhDeferred = async { runCatching { repository.getQueryHistory() }.getOrNull() }

                _topBlocked.value = tbDeferred.await()
                _topDomains.value = tdDeferred.await()
                _topClients.value = tcDeferred.await()
                _history.value = qhDeferred.await()?.history ?: emptyList()

            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore caricamento dati Pi-hole"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun toggleBlocking() {
        if (_isToggling.value) return
        val currentEnabled = _blocking.value?.isEnabled ?: return

        viewModelScope.launch {
            _isToggling.value = true
            try {
                repository.setBlocking(enabled = !currentEnabled)
                _blocking.value = repository.getBlockingStatus()
                _stats.value = repository.getStats()
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Errore durante il cambio di stato"
            } finally {
                _isToggling.value = false
            }
        }
    }

    fun clearError() {
        _error.value = null
    }
}
