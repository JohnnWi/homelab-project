package com.homelab.app.ui.backup

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.R
import com.homelab.app.domain.manager.BackupManager
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

sealed class BackupUiState {
    object Main : BackupUiState()
    object Exporting : BackupUiState()

    // Import flow
    object ImportPasswordRequired : BackupUiState()
    object ImportDecrypting : BackupUiState()
    data class ImportPreview(val previewInfo: BackupManager.PreviewInfo) : BackupUiState()
    object ImportApplying : BackupUiState()
    object ImportSuccess : BackupUiState()

    // Error state
    data class Error(val message: String) : BackupUiState()
}

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    @param:ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow<BackupUiState>(BackupUiState.Main)
    val uiState: StateFlow<BackupUiState> = _uiState.asStateFlow()

    private var pendingImportData: ByteArray? = null

    // For one-off events
    private val _exportDataEvent = MutableStateFlow<ByteArray?>(null)
    val exportDataEvent: StateFlow<ByteArray?> = _exportDataEvent.asStateFlow()

    fun onExportDataConsumed() {
        _exportDataEvent.value = null
    }

    fun dismissError() {
        _uiState.value = BackupUiState.Main
    }

    fun resetState() {
        pendingImportData = null
        _uiState.value = BackupUiState.Main
    }

    fun startExport(password: String) {
        if (password.length < 6) {
            _uiState.value = BackupUiState.Error(context.getString(R.string.backupPasswordTooShort))
            return
        }
        _uiState.value = BackupUiState.Exporting
        viewModelScope.launch {
            try {
                val data = withContext(Dispatchers.IO) { backupManager.exportBackup(password) }
                _exportDataEvent.value = data
                _uiState.value = BackupUiState.Main
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(
                    context.getString(R.string.backupExportError, e.localizedMessage ?: "")
                )
            }
        }
    }

    fun onFileSelectedForImport(data: ByteArray) {
        pendingImportData = data
        _uiState.value = BackupUiState.ImportPasswordRequired
    }

    fun decryptAndPreview(password: String) {
        val data = pendingImportData ?: return
        if (password.isBlank()) {
            _uiState.value = BackupUiState.Error(context.getString(R.string.backupPasswordRequired))
            return
        }

        _uiState.value = BackupUiState.ImportDecrypting
        viewModelScope.launch {
            try {
                val info = withContext(Dispatchers.IO) { backupManager.decryptAndPreview(data, password) }
                _uiState.value = BackupUiState.ImportPreview(info)
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(context.getString(R.string.backupDecryptError))
            }
        }
    }

    fun applyImport() {
        val currentState = _uiState.value
        if (currentState !is BackupUiState.ImportPreview) return

        _uiState.value = BackupUiState.ImportApplying
        viewModelScope.launch {
            try {
                backupManager.applyBackup(currentState.previewInfo.envelope)
                pendingImportData = null
                _uiState.value = BackupUiState.ImportSuccess
            } catch (e: Exception) {
                _uiState.value = BackupUiState.Error(
                    context.getString(R.string.backupApplyError, e.localizedMessage ?: "")
                )
            }
        }
    }
}
