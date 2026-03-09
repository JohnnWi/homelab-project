package com.homelab.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.domain.model.ServiceConnection
import com.homelab.app.domain.model.PiHoleAuthMode
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.PiholeRepository

@HiltViewModel
class ServiceLoginViewModel @Inject constructor(
    private val settingsManager: SettingsManager,
    private val portainerRepository: PortainerRepository,
    private val piholeRepository: PiholeRepository,
    private val beszelRepository: BeszelRepository,
    private val giteaRepository: com.homelab.app.data.repository.GiteaRepository
) : ViewModel() {

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    fun authenticate(
        serviceType: ServiceType,
        url: String,
        username: String = "",
        password: String = "",
        apiKey: String = ""
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val cleanUrl = cleanUrl(url)

            try {
                val connection = when (serviceType) {
                    ServiceType.PORTAINER -> {
                        portainerRepository.authenticateWithApiKey(url = cleanUrl, apiKey = apiKey.trim())
                        ServiceConnection(
                            type = ServiceType.PORTAINER,
                            url = cleanUrl,
                            apiKey = apiKey.trim()
                        )
                    }
                    ServiceType.PIHOLE -> {
                        val secret = password.trim()
                        val sid = piholeRepository.authenticate(url = cleanUrl, password = secret)
                        ServiceConnection(
                            type = ServiceType.PIHOLE,
                            url = cleanUrl,
                            token = sid,
                            piholePassword = secret,
                            piholeAuthMode = if (sid == secret) PiHoleAuthMode.LEGACY else PiHoleAuthMode.SESSION
                        )
                    }
                    ServiceType.BESZEL -> {
                        val token = beszelRepository.authenticate(url = cleanUrl, email = username, password = password)
                        ServiceConnection(
                            type = ServiceType.BESZEL,
                            url = cleanUrl,
                            token = token,
                            username = username
                        )
                    }
                    ServiceType.GITEA -> {
                        val token = giteaRepository.authenticate(url = cleanUrl, username = username, password = password)
                        ServiceConnection(
                            type = ServiceType.GITEA,
                            url = cleanUrl,
                            token = token,
                            username = username
                        )
                    }
                    else -> throw IllegalArgumentException("Unknown service type")
                }
                
                settingsManager.saveConnection(connection)
            } catch (e: Exception) {
                _error.value = e.localizedMessage ?: "Unknown error occurred"
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun cleanUrl(url: String): String {
        var clean = url.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            // iOS defaults to https but users can type http://
            // Here we prioritize https but don't force it if the user is on local network (usually http)
            clean = "https://$clean"
        }
        return clean.removeSuffix("/")
    }

    fun clearError() {
        _error.value = null
    }
}
