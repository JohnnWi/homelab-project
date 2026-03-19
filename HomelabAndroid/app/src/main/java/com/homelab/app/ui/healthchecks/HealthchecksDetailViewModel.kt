package com.homelab.app.ui.healthchecks

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.remote.dto.healthchecks.*
import com.homelab.app.data.repository.HealthchecksRepository
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.UiState
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class HealthchecksDetailViewModel @Inject constructor(
    private val repository: HealthchecksRepository,
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    val instanceId: String = savedStateHandle["instanceId"] ?: ""
    private val checkId: String? = savedStateHandle["checkId"]

    private val _detail = MutableStateFlow<HealthchecksCheck?>(null)
    val detail: StateFlow<HealthchecksCheck?> = _detail

    private val _pings = MutableStateFlow<List<HealthchecksPing>>(emptyList())
    val pings: StateFlow<List<HealthchecksPing>> = _pings

    private val _flips = MutableStateFlow<List<HealthchecksFlip>>(emptyList())
    val flips: StateFlow<List<HealthchecksFlip>> = _flips

    private val _channels = MutableStateFlow<List<HealthchecksChannel>>(emptyList())
    val channels: StateFlow<List<HealthchecksChannel>> = _channels

    private val _uiState = MutableStateFlow<UiState<Unit>>(UiState.Loading)
    val uiState: StateFlow<UiState<Unit>> = _uiState

    private val _actionError = MutableStateFlow<String?>(null)
    val actionError: StateFlow<String?> = _actionError

    private val _pingBody = MutableStateFlow<String?>(null)
    val pingBody: StateFlow<String?> = _pingBody

    fun fetchDetail() {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                val id = checkId
                if (instanceId.isBlank() || id.isNullOrBlank()) {
                    _uiState.value = UiState.Error(
                        context.getString(com.homelab.app.R.string.error_not_found),
                        retryAction = null
                    )
                    return@launch
                }

                val detail = repository.getCheck(instanceId, id)
                _detail.value = detail

                val channelsTask = async { repository.listChannels(instanceId) }
                val flipsTask = async { repository.listFlips(instanceId, id) }
                val pingsTask = async {
                    val uuid = detail.uuid ?: return@async emptyList()
                    repository.listPings(instanceId, uuid)
                }

                _channels.value = channelsTask.await()
                _flips.value = flipsTask.await()
                _pings.value = pingsTask.await()
                _uiState.value = UiState.Success(Unit)
            } catch (error: Exception) {
                val message = ErrorHandler.getMessage(context, error)
                _uiState.value = UiState.Error(message, retryAction = { fetchDetail() })
            }
        }
    }

    fun loadPingBody(ping: HealthchecksPing) {
        val uuid = _detail.value?.uuid ?: return
        viewModelScope.launch {
            runCatching { repository.getPingBody(instanceId, uuid, ping.n) }
                .onSuccess { _pingBody.value = it }
                .onFailure { _actionError.value = ErrorHandler.getMessage(context, it) }
        }
    }

    fun clearPingBody() {
        _pingBody.value = null
    }

    fun clearActionError() {
        _actionError.value = null
    }

    fun togglePause() {
        val current = _detail.value ?: return
        val uuid = current.uuid ?: return
        viewModelScope.launch {
            try {
                if (current.isPaused) {
                    repository.resumeCheck(instanceId, uuid)
                } else {
                    repository.pauseCheck(instanceId, uuid)
                }
                fetchDetail()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun deleteCheck(onSuccess: () -> Unit) {
        val uuid = _detail.value?.uuid ?: return
        viewModelScope.launch {
            try {
                repository.deleteCheck(instanceId, uuid)
                onSuccess()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    fun updateChannels(selected: List<String>, custom: String, onComplete: () -> Unit) {
        val current = _detail.value ?: return
        val uuid = current.uuid ?: return
        val customTokens = custom.split(",")
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val combined = (selected + customTokens).distinct().joinToString(",").ifBlank { "" }
        viewModelScope.launch {
            try {
                repository.updateCheck(instanceId, uuid, payloadFromCheck(current, combined.ifBlank { null }))
                fetchDetail()
                onComplete()
            } catch (error: Exception) {
                _actionError.value = ErrorHandler.getMessage(context, error)
            }
        }
    }

    private fun payloadFromCheck(check: HealthchecksCheck, channels: String?): HealthchecksCheckPayload {
        return HealthchecksCheckPayload(
            name = check.name,
            slug = check.slug,
            tags = check.tags,
            desc = check.desc,
            timeout = check.timeout,
            grace = check.grace,
            schedule = check.schedule,
            tz = check.tz,
            manualResume = check.manualResume,
            methods = check.methods,
            channels = channels
        )
    }
}
