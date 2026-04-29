package com.homelab.app.ui.login

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.homelab.app.R
import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.DockhandRepository
import com.homelab.app.data.repository.DockmonRepository
import com.homelab.app.data.repository.GiteaRepository
import com.homelab.app.data.repository.LinuxUpdateRepository
import com.homelab.app.data.repository.CraftyRepository
import com.homelab.app.data.repository.HealthchecksRepository
import com.homelab.app.data.repository.JellystatRepository
import com.homelab.app.data.repository.KomodoRepository
import com.homelab.app.data.repository.MaltrailRepository
import com.homelab.app.data.repository.MediaArrRepository
import com.homelab.app.data.repository.AdGuardHomeRepository
import com.homelab.app.data.repository.NginxProxyManagerRepository
import com.homelab.app.data.repository.PatchmonRepository
import com.homelab.app.data.repository.PangolinRepository
import com.homelab.app.data.repository.PiholeRepository
import com.homelab.app.data.repository.PlexRepository
import com.homelab.app.data.repository.PortainerRepository
import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.data.repository.ServicesRepository
import com.homelab.app.data.repository.TechnitiumRepository
import com.homelab.app.data.repository.UnifiRepository
import com.homelab.app.data.repository.UptimeKumaRepository
import com.homelab.app.data.repository.WakapiRepository
import com.homelab.app.data.repository.ProxmoxRepository
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
    private val linuxUpdateRepository: LinuxUpdateRepository,
    private val craftyRepository: CraftyRepository,
    private val technitiumRepository: TechnitiumRepository,
    private val dockhandRepository: DockhandRepository,
    private val dockmonRepository: DockmonRepository,
    private val komodoRepository: KomodoRepository,
    private val maltrailRepository: MaltrailRepository,
    private val uptimeKumaRepository: UptimeKumaRepository,
    private val unifiRepository: UnifiRepository,
    private val nginxProxyManagerRepository: NginxProxyManagerRepository,
    private val healthchecksRepository: HealthchecksRepository,
    private val jellystatRepository: JellystatRepository,
    private val patchmonRepository: PatchmonRepository,
    private val pangolinRepository: PangolinRepository,
    private val plexRepository: PlexRepository,
    private val mediaArrRepository: MediaArrRepository,
    private val wakapiRepository: WakapiRepository,
    private val proxmoxRepository: ProxmoxRepository
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
        fallbackUrl: String = "",
        mfaCode: String = "",
        allowSelfSigned: Boolean = true,
        proxmoxRealm: String = "pam",
        proxmoxOtp: String = "",
        proxmoxUseApiToken: Boolean = false
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
            val trimmedMfaCode = mfaCode.trim()
            val normalizedStoredUsername = when (serviceType) {
                ServiceType.PROXMOX -> {
                    if (trimmedUsername.isBlank()) ""
                    else if (trimmedUsername.contains("@")) trimmedUsername
                    else "${trimmedUsername}@${proxmoxRealm.ifBlank { "pam" }}"
                }
                else -> trimmedUsername
            }

            try {
                val metadataOnly = existing != null &&
                    existing.url == cleanUrl &&
                    existing.username.orEmpty() == normalizedStoredUsername &&
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
                            portainerRepository.authenticateWithApiKey(cleanUrl, trimmedApiKey, allowSelfSigned = allowSelfSigned)
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
                            val token = piholeRepository.authenticate(cleanUrl, secret, allowSelfSigned = allowSelfSigned)
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
                            adGuardHomeRepository.authenticate(cleanUrl, trimmedUsername, authPassword, allowSelfSigned = allowSelfSigned)
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
                            val token = beszelRepository.authenticate(cleanUrl, trimmedUsername, authPassword, allowSelfSigned = allowSelfSigned)
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
                            val token = giteaRepository.authenticate(cleanUrl, trimmedUsername, authPassword, allowSelfSigned = allowSelfSigned)
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
                            val token = nginxProxyManagerRepository.authenticate(cleanUrl, trimmedUsername, authPassword, allowSelfSigned = allowSelfSigned)
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
                        ServiceType.HEALTHCHECKS -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            healthchecksRepository.validateApiKey(cleanUrl, trimmedApiKey, allowSelfSigned = allowSelfSigned)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.PANGOLIN -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            val orgId = trimmedUsername.ifBlank { null }
                            pangolinRepository.authenticate(cleanUrl, trimmedApiKey, orgId, allowSelfSigned = allowSelfSigned)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                username = orgId,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.LINUX_UPDATE -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            linuxUpdateRepository.authenticate(cleanUrl, trimmedApiKey, allowSelfSigned = allowSelfSigned)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.TECHNITIUM -> {
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_username_required) }
                            val authPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank existing.password.orEmpty()
                                }
                                throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            require(authPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }

                            val sessionToken = technitiumRepository.authenticate(
                                url = cleanUrl,
                                username = trimmedUsername,
                                password = authPassword,
                                totp = trimmedMfaCode,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )

                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = sessionToken,
                                username = trimmedUsername,
                                password = authPassword,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.DOCKHAND -> {
                            val authPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank existing.password.orEmpty()
                                }
                                throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_username_required) }
                            require(authPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }

                            val cookieHeader = dockhandRepository.authenticate(
                                url = cleanUrl,
                                username = trimmedUsername,
                                password = authPassword,
                                mfaCode = trimmedMfaCode,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )

                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = cookieHeader,
                                username = trimmedUsername,
                                password = authPassword,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.DOCKMON -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            dockmonRepository.authenticate(
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.KOMODO -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            val apiSecret = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.apiKey == trimmedApiKey) {
                                    return@ifBlank existing.password.orEmpty()
                                }
                                throw IllegalArgumentException(context.getString(R.string.komodo_login_error_api_secret_required))
                            }
                            require(apiSecret.isNotBlank()) { context.getString(R.string.komodo_login_error_api_secret_required) }

                            komodoRepository.authenticate(
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                apiSecret = apiSecret,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                password = apiSecret,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.MALTRAIL -> {
                            if (trimmedUsername.isBlank() != trimmedPassword.isBlank()) {
                                throw IllegalArgumentException(
                                    context.getString(
                                        if (trimmedUsername.isBlank()) {
                                            R.string.login_error_username_required
                                        } else {
                                            R.string.login_error_password_required
                                        }
                                    )
                                )
                            }
                            val resolvedUsername = trimmedUsername.ifBlank { existing?.username.orEmpty() }
                            val resolvedPassword = if (trimmedUsername.isBlank() && trimmedPassword.isBlank()) {
                                ""
                            } else {
                                trimmedPassword.ifBlank { existing?.password.orEmpty() }
                            }
                            val sessionCookie = maltrailRepository.authenticate(
                                url = cleanUrl,
                                username = resolvedUsername.ifBlank { null },
                                password = resolvedPassword.ifBlank { null },
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = sessionCookie,
                                username = resolvedUsername.ifBlank { null },
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned,
                                password = resolvedPassword.ifBlank { null }
                            )
                        }
                        ServiceType.UPTIME_KUMA -> {
                            val resolvedPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username.orEmpty() == trimmedUsername) {
                                    return@ifBlank existing.password.orEmpty()
                                }
                                ""
                            }
                            uptimeKumaRepository.authenticate(
                                url = cleanUrl,
                                username = trimmedUsername.ifBlank { null },
                                passwordOrApiKey = resolvedPassword.ifBlank { null },
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                username = trimmedUsername.ifBlank { null },
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned,
                                password = resolvedPassword.ifBlank { null }
                            )
                        }
                        ServiceType.UNIFI_NETWORK -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            unifiRepository.authenticate(
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = canonicalUnifiUrl(cleanUrl),
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                        }
                        ServiceType.CRAFTY_CONTROLLER -> {
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_username_required) }
                            val authPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank existing.password.orEmpty()
                                }
                                throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            require(authPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }

                            val token = craftyRepository.authenticate(
                                url = cleanUrl,
                                username = trimmedUsername,
                                password = authPassword,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = token,
                                username = trimmedUsername,
                                password = authPassword,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.JELLYSTAT -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            jellystatRepository.authenticate(cleanUrl, trimmedApiKey, allowSelfSigned = allowSelfSigned)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.PATCHMON -> {
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.patchmon_login_error_token_key_required) }
                            val tokenSecret = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank existing.password.orEmpty()
                                }
                                throw IllegalArgumentException(context.getString(R.string.patchmon_login_error_token_secret_required))
                            }
                            require(tokenSecret.isNotBlank()) { context.getString(R.string.patchmon_login_error_token_secret_required) }
                            patchmonRepository.authenticate(cleanUrl, trimmedUsername, tokenSecret, allowSelfSigned = allowSelfSigned)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                username = trimmedUsername,
                                password = tokenSecret,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.PLEX -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            plexRepository.authenticate(cleanUrl, trimmedApiKey, allowSelfSigned = allowSelfSigned)
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.WAKAPI -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            wakapiRepository.authenticate(
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.PROXMOX -> {
                            if (proxmoxUseApiToken) {
                                require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_api_key_label) }
                                proxmoxRepository.authenticateWithApiToken(
                                    url = cleanUrl,
                                    apiToken = trimmedApiKey,
                                    allowSelfSigned = allowSelfSigned
                                )
                                ServiceInstance(
                                    id = instanceId,
                                    type = serviceType,
                                    label = normalizedLabel,
                                    url = cleanUrl,
                                    apiKey = trimmedApiKey,
                                    fallbackUrl = cleanFallbackUrl
                                )
                            } else {
                                require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_username_required) }
                                val resolvedPassword = trimmedPassword.ifBlank {
                                    if (existing != null && existing.url == cleanUrl && existing.username == normalizedStoredUsername) {
                                        return@ifBlank existing.password.orEmpty()
                                    }
                                    throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                                }
                                require(resolvedPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }
                                val authTicket = proxmoxRepository.authenticate(
                                    url = cleanUrl,
                                    username = trimmedUsername,
                                    password = resolvedPassword,
                                    otp = proxmoxOtp.ifBlank { null },
                                    realm = proxmoxRealm.ifBlank { "pam" },
                                    allowSelfSigned = allowSelfSigned
                                )
                                ServiceInstance(
                                    id = instanceId,
                                    type = serviceType,
                                    label = normalizedLabel,
                                    url = cleanUrl,
                                    token = authTicket.ticket,
                                    proxmoxCsrfToken = authTicket.csrfPreventionToken,
                                    proxmoxOtp = proxmoxOtp.ifBlank { null },
                                    username = authTicket.username,
                                    password = resolvedPassword,
                                    fallbackUrl = cleanFallbackUrl
                                )
                            }
                        }
                        ServiceType.QBITTORRENT -> {
                            require(trimmedUsername.isNotBlank()) { context.getString(R.string.login_error_username_required) }
                            val resolvedPassword = trimmedPassword.ifBlank {
                                if (existing != null && existing.url == cleanUrl && existing.username == trimmedUsername) {
                                    return@ifBlank existing.password.orEmpty()
                                }
                                throw IllegalArgumentException(context.getString(R.string.login_error_password_required))
                            }
                            require(resolvedPassword.isNotBlank()) { context.getString(R.string.login_error_password_required) }
                            val sid = mediaArrRepository.authenticateQbittorrent(
                                url = cleanUrl,
                                username = trimmedUsername,
                                password = resolvedPassword,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                token = sid,
                                username = trimmedUsername,
                                fallbackUrl = cleanFallbackUrl,
                                password = resolvedPassword
                            )
                        }
                        ServiceType.RADARR,
                        ServiceType.SONARR,
                        ServiceType.LIDARR,
                        ServiceType.JELLYSEERR,
                        ServiceType.PROWLARR,
                        ServiceType.BAZARR -> {
                            require(trimmedApiKey.isNotBlank()) { context.getString(R.string.login_error_api_key_required) }
                            mediaArrRepository.authenticateWithApiKey(
                                url = cleanUrl,
                                serviceType = serviceType,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.GLUETUN,
                        ServiceType.FLARESOLVERR -> {
                            mediaArrRepository.authenticateWithApiKey(
                                url = cleanUrl,
                                serviceType = serviceType,
                                apiKey = trimmedApiKey,
                                fallbackUrl = cleanFallbackUrl,
                                allowSelfSigned = allowSelfSigned
                            )
                            ServiceInstance(
                                id = instanceId,
                                type = serviceType,
                                label = normalizedLabel,
                                url = cleanUrl,
                                apiKey = trimmedApiKey.ifBlank { null },
                                fallbackUrl = cleanFallbackUrl
                            )
                        }
                        ServiceType.UNKNOWN -> throw IllegalArgumentException(context.getString(R.string.error_unknown))
                    }
                }.copy(allowSelfSigned = allowSelfSigned)

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

    private fun canonicalUnifiUrl(url: String): String {
        val lowered = url.lowercase()
        return if (lowered.contains("unifi.ui.com") || lowered.contains("api.ui.com")) {
            "https://api.ui.com"
        } else {
            url
        }
    }

    fun clearError() {
        _error.value = null
    }
}
