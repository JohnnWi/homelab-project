package com.homelab.app.ui.pihole

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.pihole.*
import com.homelab.app.data.repository.PiholeRepository
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import com.homelab.app.util.Logger
import java.util.Date
import javax.inject.Inject
import android.content.Context

@HiltViewModel
class PiholeViewModel @Inject constructor(
    private val repository: PiholeRepository,
    @param:ApplicationContext private val context: Context
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

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState

    private val _domainsState = MutableStateFlow<UiState<List<PiholeDomainDto>>>(UiState.Loading)
    val domainsState: StateFlow<UiState<List<PiholeDomainDto>>> = _domainsState

    private val _queriesState = MutableStateFlow<UiState<List<PiholeQueryLogEntry>>>(UiState.Loading)
    val queriesState: StateFlow<UiState<List<PiholeQueryLogEntry>>> = _queriesState

    private val _isToggling = MutableStateFlow(false)
    val isToggling: StateFlow<Boolean> = _isToggling

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    init {
        _uiState.onEach { Logger.stateTransition("PiholeViewModel", "uiState", it) }.launchIn(viewModelScope)
        _domainsState.onEach { Logger.stateTransition("PiholeViewModel", "domainsState", it) }.launchIn(viewModelScope)
        _queriesState.onEach { Logger.stateTransition("PiholeViewModel", "queriesState", it) }.launchIn(viewModelScope)
    }

    fun fetchAll() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
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
                
                _uiState.value = UiState.Success(Unit)
            } catch (e: Exception) {
                val message = ErrorHandler.getMessage(context, e)
                _uiState.value = UiState.Error(message, retryAction = { fetchAll() })
            }
        }
    }

    fun toggleBlocking(timer: Int? = null) {
        if (_isToggling.value) return
        val currentEnabled = _blocking.value?.isEnabled ?: return

        viewModelScope.launch {
            _isToggling.value = true
            try {
                if (timer != null) {
                    repository.setBlocking(enabled = false, timer = timer)
                } else {
                    repository.setBlocking(enabled = !currentEnabled)
                }
                _blocking.value = repository.getBlockingStatus()
                _stats.value = repository.getStats()
            } catch (e: Exception) {
                val message = ErrorHandler.getMessage(context, e)
                _actionError.value = message
            } finally {
                _isToggling.value = false
            }
        }
    }

    fun fetchDomains() {
        viewModelScope.launch {
            if (_domainsState.value !is UiState.Success) {
                _domainsState.value = UiState.Loading
            }
            try {
                val d = repository.getDomains()
                _domainsState.value = UiState.Success(d)
            } catch (e: Exception) {
                val message = ErrorHandler.getMessage(context, e)
                _domainsState.value = UiState.Error(message, retryAction = { fetchDomains() })
            }
        }
    }

    fun addDomain(domain: String, listType: PiholeDomainListType) {
        viewModelScope.launch {
            try {
                repository.addDomain(domain, listType)
                _domainsState.value = UiState.Success(repository.getDomains())
            } catch (e: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, e)
            }
        }
    }

    fun removeDomain(domain: String, listType: PiholeDomainListType) {
        viewModelScope.launch {
            try {
                repository.removeDomain(domain, listType)
                _domainsState.value = UiState.Success(repository.getDomains())
            } catch (e: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, e)
            }
        }
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun fetchRecentQueries(windowSeconds: Long = 15 * 60) {
        viewModelScope.launch {
            val until = Date().time / 1000
            val currentQueries = (_queriesState.value as? UiState.Success)?.data ?: emptyList()
            
            if (currentQueries.isEmpty() && _queriesState.value !is UiState.Success) {
                _queriesState.value = UiState.Loading
            }
            
            val from = if (currentQueries.isEmpty()) {
                until - windowSeconds
            } else {
                (until - 90).coerceAtLeast(0)
            }
            try {
                val fetched = repository.getQueries(from = from, until = until)
                val merged = (currentQueries + fetched)
                    .distinctBy { it.id }
                    .sortedByDescending { it.timestamp }
                    .take(500)
                _queriesState.value = UiState.Success(merged)
            } catch (e: Exception) {
                if (currentQueries.isEmpty()) {
                    val message = ErrorHandler.getMessage(context, e)
                    _queriesState.value = UiState.Error(message, retryAction = { fetchRecentQueries() })
                } else {
                    // Just silently fail if we already have queries showing and polling fails
                }
            }
        }
    }
}
