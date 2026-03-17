package com.homelab.app.ui.login

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.R
import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.GiteaRepository
import com.homelab.app.data.repository.AdGuardHomeRepository
import com.homelab.app.data.repository.NginxProxyManagerRepository
import com.homelab.app.data.repository.PiholeRepository
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.domain.model.PiHoleAuthMode
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ErrorHandler
import com.homelab.app.util.ServiceType
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class ServiceLoginViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @param:ApplicationContext private val context: Context,
    private val servicesRepository: ServicesRepository,
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val portainerRepository: PortainerRepository,
    private val piholeRepository: PiholeRepository,
    private val adGuardHomeRepository: AdGuardHomeRepository,
    private val beszelRepository: BeszelRepository,
    private val giteaRepository: GiteaRepository,
    private val nginxProxyManagerRepository: NginxProxyManagerRepository
) : ViewModel() {

    private val existingInstanceId: String? = savedStateHandle["instanceId"]

    private val _existingInstance = MutableStateFlow<ServiceInstance?>(null)
    val existingInstance: StateFlow<ServiceInstance?> = _existingInstance

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error

    init {
        if (!existingInstanceId.isNullOrBlank()) {
            viewModelScope.launch {
                _existingInstance.value = serviceInstancesRepository.getInstance(existingInstanceId)
            }
        }
    }

    fun saveInstance(
        serviceType: ServiceType,
        label: String,
        url: String,
        username: String = "",
        password: String = "",
        apiKey: String = "",
        fallbackUrl: String = ""
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            val existing = _existingInstance.value
            val instanceId = existing?.id ?: UUID.randomUUID().toString()
            val normalizedLabel = label.trim().ifBlank { serviceType.displayName }
            val cleanUrl = cleanUrl(url)
            val cleanFallbackUrl = cleanOptionalUrl(fallbackUrl)
            val trimmedUsername = username.trim()
            val trimmedPassword = password.trim()
            val trimmedApiKey = apiKey.trim()

            try {
                val metadataOnly = existing != null &&
                    existing.url == cleanUrl &&
                    existing.username.orEmpty() == trimmedUsername &&
                    existing.apiKey.orEmpty() == trimmedApiKey &&
                    existing.piHoleStoredSecret.orEmpty() == trimmedPassword

                val instance = if (metadataOnly) {
                    existing.copy(
                        label = normalizedLabel,
                        fallbackUrl = cleanFallbackUrl
                    )
                } else {
                    when (serviceType) {
                        ServiceType.PORTAINER -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            portainerRepository.authenticateWithApiKey(cleanUrl, trimmedApiKey)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = existing?.token.orEmpty(),
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.PIHOLE -> {
                            val secret = trimmedPassword.ifBlank {
                                existing?.piHoleStoredSecret ?: throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            val token = piholeRepository.authenticate(cleanUrl, secret)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = token,
                                piholePassword = secret,
                                piholeAuthMode = if (token == secret) PiHoleAuthMode.LEGACY else PiHoleAuthMode.SESSION,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.ADGUARD_HOME -> {
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_username_required) }
                            val authPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank existing.password.orEmpty()
                                }
                                throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            require(authPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }
                            adGuardHomeRepository.authenticate(cleanUrl, trimmedUsername, authPassword)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                username = trimmedUsername,
                                password = authPassword,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.BESZEL -> {
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_email_required) }
                            val authPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank ""
                                }
                                throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            require(authPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }
                            val token = beszelRepository.authenticate(cleanUrl, trimmedUsername, authPassword)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = token,
                                username = trimmedUsername,
                                fallbackUrl = cleanFallbackUrl,
                                password = authPassword
                            )
                        }
                        ServiceType.GITEA -> {
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_username_required) }
                            val authPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank ""
                                }
                                throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            require(authPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }
                            val token = giteaRepository.authenticate(cleanUrl, trimmedUsername, authPassword)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = token,
                                username = trimmedUsername,
                                fallbackUrl = cleanFallbackUrl,
                                password = authPassword
                            )
                        }
                        ServiceType.NGINX_PROXY_MANAGER -> {
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_email_required) }
                            val authPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank ""
                                }
                                throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            require(authPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }
                            val token = nginxProxyManagerRepository.authenticate(cleanUrl, trimmedUsername, authPassword)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = token,
                                username = trimmedUsername,
                                fallbackUrl = cleanFallbackUrl,
                                password = authPassword
                            )
                        }
                        ServiceType.UNKNOWN -> throw IllegalArgumentException(context.getString(R.string.error_unknown))
                    }
                }

                servicesRepository.saveInstance(instance)
                _existingInstance.value = instance
            } catch (error: Exception) {
                _error.value = ErrorHandler.getMessage(context, error)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun cleanUrl(url: String): String {
        var clean = url.trim()
        clean = clean.trimEnd { it == ')' || it == ']' || it == '}' || it == ',' || it == ';' }
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "")
    }

    private fun cleanOptionalUrl(url: String): String? {
        val trimmed = url.trim()
        if (trimmed.isBlank()) return null
        return cleanUrl(trimmed)
    }

    fun clearError() {
        _error.value = null
    }
}
