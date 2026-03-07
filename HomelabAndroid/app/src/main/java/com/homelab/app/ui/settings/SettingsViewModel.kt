package com.homelab.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.ServiceConnection
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.ThemeMode
import com.homelab.app.data.repository.LanguageMode
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val servicesRepository: ServicesRepository,
    private val localPreferencesRepository: LocalPreferencesRepository
) : ViewModel() {

    val connections: StateFlow<Map<ServiceType, ServiceConnection?>> = settingsManager.allConnections
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    fun getConnection(type: ServiceType): Flow<ServiceConnection?> = settingsManager.getConnection(type)

    fun saveFallbackUrl(type: ServiceType, fallbackUrl: String) {
        viewModelScope.launch {
            val conn = settingsManager.getConnection(type).firstOrNull() ?: return@launch
            var processedUrl = fallbackUrl.trim().takeIf { it.isNotBlank() }
            if (processedUrl != null && !processedUrl.startsWith("http")) {
                processedUrl = "https://$processedUrl"
            }
            val updated = conn.copy(fallbackUrl = processedUrl)
            settingsManager.saveConnection(updated)
        }
    }

    fun disconnectService(type: ServiceType) {
        viewModelScope.launch {
            servicesRepository.disconnectService(type)
        }
    }

    val themeMode: StateFlow<ThemeMode> = localPreferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val languageMode: StateFlow<LanguageMode> = localPreferencesRepository.languageMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LanguageMode.ENGLISH)

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            localPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            localPreferencesRepository.setLanguageMode(mode)
            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                androidx.core.os.LocaleListCompat.forLanguageTags(mode.code)
            )
        }
    }

    val hiddenServices: StateFlow<Set<String>> = localPreferencesRepository.hiddenServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    fun toggleServiceVisibility(type: ServiceType) {
        viewModelScope.launch {
            localPreferencesRepository.toggleServiceVisibility(type.name)
        }
    }
}
