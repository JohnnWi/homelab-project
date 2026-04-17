package com.homelab.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.BuildConfig
import com.homelab.app.data.repository.LanguageMode
import com.homelab.app.data.repository.LocalPreferencesRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.ThemeMode
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.AppIconManager
import com.homelab.app.util.AppIconOption
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val servicesRepository: ServicesRepository,
    private val localPreferencesRepository: LocalPreferencesRepository,
    private val appIconManager: AppIconManager
) : ViewModel() {

    data class UpdateBannerState(
        val latestVersion: String,
        val currentVersion: String,
        val updateUrl: String
    )

    data class UpdatePopupState(
        val latestVersion: String,
        val changelog: String?,
        val updateUrl: String
    )

    private val _updateBannerState = MutableStateFlow<UpdateBannerState?>(null)
    val updateBannerState: StateFlow<UpdateBannerState?> = _updateBannerState

    private val _updatePopupState = MutableStateFlow<UpdatePopupState?>(null)
    val updatePopupState: StateFlow<UpdatePopupState?> = _updatePopupState

    private val _appIconApplying = MutableStateFlow(false)
    val appIconApplying: StateFlow<Boolean> = _appIconApplying

    private val updateManifestUrl = "https://raw.githubusercontent.com/JohnnWi/homelab-project/main/app-version.json"
    private val defaultUpdateUrl = "https://github.com/JohnnWi/homelab-project/releases"
    private val updateCheckIntervalMs = 15 * 60 * 1000L

    val instancesByType: StateFlow<Map<ServiceType, List<ServiceInstance>>> = servicesRepository.instancesByType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val preferredInstanceIdByType: StateFlow<Map<ServiceType, String?>> = servicesRepository.preferredInstanceIdByType
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val themeMode: StateFlow<ThemeMode> = localPreferencesRepository.themeMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ThemeMode.SYSTEM)

    val languageMode: StateFlow<LanguageMode> = localPreferencesRepository.languageMode
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), LanguageMode.ENGLISH)

    val hiddenServices: StateFlow<Set<String>> = localPreferencesRepository.hiddenServices
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val appIcon: StateFlow<AppIconOption> = localPreferencesRepository.appIcon
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), AppIconOption.DEFAULT)

    val serviceOrder: StateFlow<List<ServiceType>> = localPreferencesRepository.serviceOrder
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            ServiceType.entries.filter { it != ServiceType.UNKNOWN }
        )

    val biometricEnabled: StateFlow<Boolean> = localPreferencesRepository.biometricEnabled
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val isPinSet: StateFlow<Boolean> = localPreferencesRepository.appPin
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
        .let { flow ->
            kotlinx.coroutines.flow.combine(flow, kotlinx.coroutines.flow.flowOf(Unit)) { pin, _ -> pin != null }
                .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
        }

    private val _storedPin = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch {
            localPreferencesRepository.appPin.collect { _storedPin.value = it }
        }
        viewModelScope.launch {
            checkForUpdateBanner(force = false)
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            localPreferencesRepository.setThemeMode(mode)
        }
    }

    fun setLanguageMode(mode: LanguageMode) {
        viewModelScope.launch {
            localPreferencesRepository.setLanguageMode(mode)
        }
        // Apply locale change on all API levels
        androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
            androidx.core.os.LocaleListCompat.forLanguageTags(mode.code)
        )
    }

    fun setAppIcon(icon: AppIconOption) {
        viewModelScope.launch {
            if (appIcon.value == icon) return@launch
            _appIconApplying.value = true
            localPreferencesRepository.setAppIcon(icon)
            try {
                appIconManager.apply(icon)
            } finally {
                _appIconApplying.value = false
            }
        }
    }

    fun toggleServiceVisibility(type: ServiceType) {
        viewModelScope.launch {
            localPreferencesRepository.toggleServiceVisibility(type.name)
        }
    }

    fun moveService(type: ServiceType, offset: Int) {
        viewModelScope.launch {
            localPreferencesRepository.moveService(type, offset)
        }
    }

    fun deleteInstance(instanceId: String) {
        viewModelScope.launch {
            servicesRepository.disconnectInstance(instanceId)
        }
    }

    fun setPreferredInstance(type: ServiceType, instanceId: String) {
        viewModelScope.launch {
            servicesRepository.setPreferredInstance(type, instanceId)
        }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        viewModelScope.launch {
            localPreferencesRepository.setBiometricEnabled(enabled)
        }
    }

    fun savePin(pin: String) {
        viewModelScope.launch {
            localPreferencesRepository.savePin(pin)
        }
    }

    fun verifyPin(pin: String): Boolean {
        return _storedPin.value == pin
    }

    fun clearSecurity() {
        viewModelScope.launch {
            localPreferencesRepository.clearSecurity()
        }
    }

    fun dismissUpdateBanner() {
        val latest = _updateBannerState.value?.latestVersion ?: return
        viewModelScope.launch {
            localPreferencesRepository.setDismissedUpdateVersion(latest)
            _updateBannerState.value = null
        }
    }

    fun dismissUpdatePopup() {
        val latest = _updatePopupState.value?.latestVersion ?: return
        viewModelScope.launch {
            localPreferencesRepository.setDismissedPopupVersion(latest)
            _updatePopupState.value = null
        }
    }

    fun refreshUpdateBanner(force: Boolean = true) {
        viewModelScope.launch {
            checkForUpdateBanner(force = force)
        }
    }

    private suspend fun checkForUpdateBanner(force: Boolean) {
        val current = BuildConfig.VERSION_NAME
        val dismissed = localPreferencesRepository.dismissedUpdateVersion.firstOrNull()
        val dismissedPopup = localPreferencesRepository.dismissedPopupVersion.firstOrNull()
        val lastCheckedAt = localPreferencesRepository.updateLastCheckedAt.firstOrNull()
        val cachedLatest = localPreferencesRepository.updateAvailableVersion.firstOrNull()
        val cachedUrl = localPreferencesRepository.updateAvailableUrl.firstOrNull()
        val cachedChangelog = localPreferencesRepository.updateAvailableChangelog.firstOrNull()
        val now = System.currentTimeMillis()

        val cachedState = cachedLatest
            ?.trim()
            ?.takeIf { it.isNotEmpty() && compareVersions(it, current) > 0 && dismissed != it }
            ?.let {
                UpdateBannerState(
                    latestVersion = it,
                    currentVersion = current,
                    updateUrl = cachedUrl?.takeIf { url -> url.isNotBlank() } ?: defaultUpdateUrl
                )
            }

        _updateBannerState.value = cachedState

        // Restore popup from cache
        cachedLatest?.trim()
            ?.takeIf { it.isNotEmpty() && compareVersions(it, current) > 0 && dismissed != it && dismissedPopup != it }
            ?.let {
                _updatePopupState.value = UpdatePopupState(
                    latestVersion = it,
                    changelog = cachedChangelog,
                    updateUrl = cachedUrl?.takeIf { url -> url.isNotBlank() } ?: defaultUpdateUrl
                )
            }

        if (!force && lastCheckedAt != null && (now - lastCheckedAt) < updateCheckIntervalMs) {
            return
        }

        val payload = fetchUpdateManifest() ?: run {
            return
        }

        localPreferencesRepository.setUpdateLastCheckedAt(now)

        val latest = payload.latest.trim()
        if (latest.isEmpty()) {
            localPreferencesRepository.setAvailableUpdate(version = null, url = null, changelog = null)
            _updateBannerState.value = null
            _updatePopupState.value = null
            return
        }

        val updateUrl = payload.androidUrl?.takeIf { it.isNotBlank() } ?: defaultUpdateUrl
        val isNewer = compareVersions(latest, current) > 0
        if (!isNewer) {
            localPreferencesRepository.setAvailableUpdate(version = null, url = null, changelog = null)
            _updateBannerState.value = null
            _updatePopupState.value = null
            return
        }

        localPreferencesRepository.setAvailableUpdate(version = latest, url = updateUrl, changelog = payload.changelog)
        val shouldShow = dismissed != latest

        _updateBannerState.value = if (shouldShow) {
            UpdateBannerState(
                latestVersion = latest,
                currentVersion = current,
                updateUrl = updateUrl
            )
        } else {
            null
        }

        // Popup: only if not dismissed for this version
        _updatePopupState.value = if (shouldShow && dismissedPopup != latest) {
            UpdatePopupState(
                latestVersion = latest,
                changelog = payload.changelog,
                updateUrl = updateUrl
            )
        } else {
            null
        }
    }

    private suspend fun fetchUpdateManifest(): UpdateManifest? = withContext(Dispatchers.IO) {
        runCatching {
            val connection = (URL(updateManifestUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                requestMethod = "GET"
            }
            try {
                if (connection.responseCode !in 200..299) return@runCatching null
                val body = connection.inputStream.bufferedReader().use { it.readText() }
                val json = JSONObject(body)
                UpdateManifest(
                    latest = json.optString("latest"),
                    changelog = json.optString("changelog").takeIf { it.isNotBlank() },
                    androidUrl = json.optString("android_url").takeIf { it.isNotBlank() }
                )
            } finally {
                connection.disconnect()
            }
        }.getOrNull()
    }

    private fun compareVersions(leftVersion: String, rightVersion: String): Int {
        val left = leftVersion.split('.').map { it.toIntOrNull() ?: 0 }
        val right = rightVersion.split('.').map { it.toIntOrNull() ?: 0 }
        val maxSize = maxOf(left.size, right.size)
        for (index in 0 until maxSize) {
            val l = left.getOrElse(index) { 0 }
            val r = right.getOrElse(index) { 0 }
            if (l != r) return l.compareTo(r)
        }
        return 0
    }

    private data class UpdateManifest(
        val latest: String,
        val changelog: String?,
        val androidUrl: String?
    )
}
