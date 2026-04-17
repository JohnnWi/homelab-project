package com.homelab.app.data.remote

import com.homelab.app.data.repository.BeszelRepository
import com.homelab.app.data.repository.NginxProxyManagerRepository
import com.homelab.app.data.repository.ProxmoxRepository
import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.util.GlobalEventBus
import com.homelab.app.domain.model.PiHoleAuthMode
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val globalEventBus: GlobalEventBus,
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val beszelRepository: dagger.Lazy<BeszelRepository>,
    private val nginxProxyManagerRepository: dagger.Lazy<NginxProxyManagerRepository>,
    private val proxmoxRepository: dagger.Lazy<ProxmoxRepository>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val instanceIdHeader = request.header("X-Homelab-Instance-Id")
        val bypassHeader = request.header("X-Homelab-Bypass")
        val proxmoxRetryHeader = request.header(PROXMOX_RETRY_HEADER)

        val requestBuilder = request.newBuilder()

        // Clean up internal headers before sending to real server
        if (request.header("X-Homelab-Service") != null) {
            requestBuilder.removeHeader("X-Homelab-Service")
        }
        if (instanceIdHeader != null) {
            requestBuilder.removeHeader("X-Homelab-Instance-Id")
        }
        if (bypassHeader != null) {
            requestBuilder.removeHeader("X-Homelab-Bypass")
        }
        if (proxmoxRetryHeader != null) {
            requestBuilder.removeHeader(PROXMOX_RETRY_HEADER)
        }
        if (request.header("X-Homelab-Username") != null) {
            requestBuilder.removeHeader("X-Homelab-Username")
        }
        if (request.header("X-Homelab-Password") != null) {
            requestBuilder.removeHeader("X-Homelab-Password")
        }

        val instance = if (bypassHeader == "true" || instanceIdHeader.isNullOrBlank()) {
            null
        } else {
            runBlocking { serviceInstancesRepository.getInstance(instanceIdHeader) }
        }

        val effectiveInstance = if (
            instance != null &&
            instance.type == ServiceType.PROXMOX &&
            bypassHeader != "true" &&
            proxmoxRetryHeader != "true"
        ) {
            proactivelyRefreshProxmoxTicket(instance)
        } else {
            instance
        }

        if (effectiveInstance != null) {
            val hasAuthorization = request.header("Authorization") != null
            addAuthHeaders(requestBuilder, effectiveInstance, hasAuthorization)
        }

        request = requestBuilder.build()
        var response = chain.proceed(request)

        // Auto-retry for Beszel on auth failure (401 or 400 for PocketBase)
        if (effectiveInstance != null &&
            effectiveInstance.type == ServiceType.BESZEL &&
            (response.code == 401 || response.code == 400) &&
            bypassHeader != "true" &&
            !effectiveInstance.username.isNullOrBlank() &&
            !effectiveInstance.password.isNullOrBlank()
        ) {
            val newToken = try {
                runBlocking {
                    beszelRepository.get().authenticate(
                        effectiveInstance.url,
                        effectiveInstance.username,
                        effectiveInstance.password,
                        allowSelfSigned = effectiveInstance.allowSelfSigned
                    )
                }
            } catch (_: Exception) { null }

            if (newToken != null) {
                // Persist the refreshed token
                runBlocking {
                    serviceInstancesRepository.saveInstance(effectiveInstance.copy(token = newToken))
                }

                // Retry with new token
                response.close()
                val retryBuilder = request.newBuilder()
                    .removeHeader("Authorization")
                    .addHeader("Authorization", "Bearer $newToken")
                response = chain.proceed(retryBuilder.build())

                return response
            }
        }

        // Auto-retry for Nginx Proxy Manager on token expiration/auth failure.
        if (effectiveInstance != null &&
            effectiveInstance.type == ServiceType.NGINX_PROXY_MANAGER &&
            bypassHeader != "true" &&
            !effectiveInstance.username.isNullOrBlank() &&
            !effectiveInstance.password.isNullOrBlank() &&
            shouldAttemptNpmReauth(response)
        ) {
            val newToken = try {
                runBlocking {
                    nginxProxyManagerRepository.get().authenticate(
                        effectiveInstance.url,
                        effectiveInstance.username.orEmpty(),
                        effectiveInstance.password.orEmpty(),
                        allowSelfSigned = effectiveInstance.allowSelfSigned
                    )
                }
            } catch (_: Exception) { null }

            if (newToken != null) {
                runBlocking {
                    serviceInstancesRepository.saveInstance(effectiveInstance.copy(token = newToken))
                }

                response.close()
                val retryBuilder = request.newBuilder()
                    .removeHeader("Authorization")
                    .removeHeader("Cookie")
                    .addHeader("Authorization", "Bearer $newToken")
                    .addHeader("Cookie", "token=$newToken")
                return chain.proceed(retryBuilder.build())
            }
        }

        // Auto-refresh or re-authenticate Proxmox ticket on auth failure.
        if (effectiveInstance != null &&
            effectiveInstance.type == ServiceType.PROXMOX &&
            response.code == 401 &&
            bypassHeader != "true" &&
            proxmoxRetryHeader != "true" &&
            effectiveInstance.apiKey.isNullOrBlank() &&
            !effectiveInstance.username.isNullOrBlank() &&
            (effectiveInstance.token.isNotBlank() || !effectiveInstance.password.isNullOrBlank())
        ) {
            val refreshed = try {
                runBlocking {
                    val refreshedTicket = if (effectiveInstance.token.isNotBlank()) {
                        try {
                            proxmoxRepository.get().refreshTicket(
                                url = effectiveInstance.url,
                                username = effectiveInstance.username.orEmpty(),
                                currentTicket = effectiveInstance.token,
                                allowSelfSigned = effectiveInstance.allowSelfSigned
                            )
                        } catch (_: Exception) {
                            null
                        }
                    } else {
                        null
                    }

                    refreshedTicket ?: if (!effectiveInstance.password.isNullOrBlank()) {
                        proxmoxRepository.get().authenticate(
                            url = effectiveInstance.url,
                            username = effectiveInstance.username.orEmpty(),
                            password = effectiveInstance.password.orEmpty(),
                            otp = effectiveInstance.proxmoxOtp,
                            allowSelfSigned = effectiveInstance.allowSelfSigned
                        )
                    } else {
                        null
                    }
                }
            } catch (_: Exception) { null }

            if (refreshed != null) {
                val refreshedInstance = effectiveInstance.copy(
                    token = refreshed.ticket,
                    proxmoxCsrfToken = refreshed.csrfPreventionToken,
                    username = refreshed.username
                )
                runBlocking {
                    serviceInstancesRepository.saveInstance(refreshedInstance)
                }

                response.close()
                val retryBuilder = request.newBuilder()
                    .removeHeader("Cookie")
                    .removeHeader("CSRFPreventionToken")
                    .addHeader(PROXMOX_RETRY_HEADER, "true")
                    .addHeader("Cookie", "PVEAuthCookie=${refreshed.ticket}")

                if (request.method.uppercase() != "GET" && !refreshed.csrfPreventionToken.isBlank()) {
                    retryBuilder.addHeader("CSRFPreventionToken", refreshed.csrfPreventionToken)
                }

                return chain.proceed(retryBuilder.build())
            }
        }

        if (response.code == 401 &&
            bypassHeader != "true" &&
            effectiveInstance != null &&
            effectiveInstance.type != ServiceType.PIHOLE &&
            effectiveInstance.type != ServiceType.BESZEL &&
            effectiveInstance.type != ServiceType.NGINX_PROXY_MANAGER &&
            !instanceIdHeader.isNullOrBlank()
        ) {
            globalEventBus.emitAuthError(instanceIdHeader)
        }

        return response
    }

    private fun shouldAttemptNpmReauth(response: Response): Boolean {
        if (response.code == 401) {
            return true
        }
        if (response.code != 400) {
            return false
        }
        val body = try {
            response.peekBody(4096).string()
        } catch (_: Exception) {
            return false
        }
        val lowered = body.lowercase()
        return lowered.contains("token has expired") ||
            lowered.contains("jwt expired") ||
            lowered.contains("tokenexpirederror")
    }

    private fun addAuthHeaders(
        builder: okhttp3.Request.Builder,
        instance: com.homelab.app.domain.model.ServiceInstance,
        hasAuthorization: Boolean
    ) {
        when (instance.type) {
            ServiceType.PORTAINER -> {
                if (!instance.apiKey.isNullOrBlank()) {
                    builder.addHeader("X-API-Key", instance.apiKey)
                } else if (!hasAuthorization && instance.token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer ${instance.token}")
                }
            }
            ServiceType.PIHOLE -> {
                if (instance.token.isNotBlank() && instance.piholeAuthMode != PiHoleAuthMode.LEGACY) {
                    builder.addHeader("X-FTL-SID", instance.token)
                }
            }
            ServiceType.ADGUARD_HOME -> {
                if (!hasAuthorization) {
                    val username = instance.username.orEmpty()
                    val password = instance.password.orEmpty()
                    if (username.isNotBlank() || password.isNotBlank()) {
                        val creds = "$username:$password"
                        val encoded = java.util.Base64.getEncoder().encodeToString(creds.toByteArray(Charsets.UTF_8))
                        builder.addHeader("Authorization", "Basic $encoded")
                    } else if (instance.token.isNotBlank()) {
                        if (instance.token.startsWith("basic:")) {
                            val encoded = instance.token.removePrefix("basic:")
                            builder.addHeader("Authorization", "Basic $encoded")
                        } else {
                            builder.addHeader("Authorization", "Basic ${instance.token}")
                        }
                    }
                }
            }
            ServiceType.BESZEL -> {
                if (!hasAuthorization && instance.token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer ${instance.token}")
                }
            }
            ServiceType.GITEA -> {
                if (!hasAuthorization && instance.token.isNotBlank()) {
                    if (instance.token.startsWith("basic:")) {
                        val credentials = instance.token.removePrefix("basic:")
                        builder.addHeader("Authorization", "Basic $credentials")
                    } else {
                        builder.addHeader("Authorization", "token ${instance.token}")
                    }
                }
            }
            ServiceType.NGINX_PROXY_MANAGER -> {
                if (!hasAuthorization && instance.token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer ${instance.token}")
                    // NPMplus uses cookie-based auth instead of Bearer
                    builder.addHeader("Cookie", "token=${instance.token}")
                }
            }
            ServiceType.PANGOLIN -> {
                if (!hasAuthorization && !instance.apiKey.isNullOrBlank()) {
                    val token = instance.apiKey.trim().let { raw ->
                        if (raw.startsWith("bearer ", ignoreCase = true)) raw.substring(7).trim() else raw
                    }
                    if (token.isNotBlank()) {
                        builder.addHeader("Authorization", "Bearer $token")
                    }
                }
            }
            ServiceType.HEALTHCHECKS -> {
                if (!instance.apiKey.isNullOrBlank()) {
                    builder.addHeader("X-Api-Key", instance.apiKey)
                }
            }
            ServiceType.LINUX_UPDATE -> {
                if (!hasAuthorization && !instance.apiKey.isNullOrBlank()) {
                    val token = instance.apiKey.trim().let { raw ->
                        if (raw.startsWith("bearer ", ignoreCase = true)) raw.substring(7).trim() else raw
                    }
                    if (token.isNotBlank()) {
                        builder.addHeader("Authorization", "Bearer $token")
                    }
                }
            }
            ServiceType.DOCKHAND -> {
                if (!hasAuthorization && instance.token.isNotBlank()) {
                    builder.addHeader("Cookie", instance.token)
                }
            }
            ServiceType.CRAFTY_CONTROLLER -> {
                if (!hasAuthorization && instance.token.isNotBlank()) {
                    builder.addHeader("Authorization", "Bearer ${instance.token}")
                }
            }
            ServiceType.JELLYSTAT -> {
                if (!instance.apiKey.isNullOrBlank()) {
                    builder.addHeader("X-API-Token", instance.apiKey)
                }
            }
            ServiceType.PATCHMON -> {
                if (!hasAuthorization) {
                    val tokenKey = instance.username.orEmpty()
                    val tokenSecret = instance.password.orEmpty()
                    if (tokenKey.isNotBlank() || tokenSecret.isNotBlank()) {
                        val creds = "$tokenKey:$tokenSecret"
                        val encoded = java.util.Base64.getEncoder().encodeToString(creds.toByteArray(Charsets.UTF_8))
                        builder.addHeader("Authorization", "Basic $encoded")
                    }
                }
            }
            ServiceType.PLEX -> {
                if (!instance.apiKey.isNullOrBlank()) {
                    builder.addHeader("X-Plex-Token", instance.apiKey)
                }
            }
            ServiceType.RADARR,
            ServiceType.SONARR,
            ServiceType.LIDARR,
            ServiceType.JELLYSEERR,
            ServiceType.PROWLARR,
            ServiceType.BAZARR -> {
                if (!instance.apiKey.isNullOrBlank()) {
                    builder.addHeader("X-Api-Key", instance.apiKey)
                }
            }
            ServiceType.GLUETUN,
            ServiceType.FLARESOLVERR -> {
                if (!instance.apiKey.isNullOrBlank()) {
                    builder.addHeader("X-Api-Key", instance.apiKey)
                    if (!hasAuthorization) {
                        builder.addHeader("Authorization", "Bearer ${instance.apiKey}")
                    }
                }
            }
            ServiceType.QBITTORRENT -> {
                if (instance.token.isNotBlank()) {
                    builder.addHeader("Cookie", "SID=${instance.token}")
                }
            }
            ServiceType.WAKAPI -> {
                if (!hasAuthorization) {
                    val apiKey = instance.apiKey.orEmpty()
                    if (apiKey.isNotBlank()) {
                        val encoded = java.util.Base64.getEncoder().encodeToString(apiKey.toByteArray(Charsets.UTF_8))
                        builder.addHeader("Authorization", "Basic $encoded")
                    }
                }
            }
            ServiceType.PROXMOX -> {
                if (!hasAuthorization && !instance.apiKey.isNullOrBlank()) {
                    builder.addHeader("Authorization", "PVEAPIToken=${instance.apiKey}")
                } else if (!hasAuthorization && instance.token.isNotBlank()) {
                    builder.addHeader("Cookie", "PVEAuthCookie=${instance.token}")
                    if (builder.build().method.uppercase() != "GET" && !instance.proxmoxCsrfToken.isNullOrBlank()) {
                        builder.addHeader("CSRFPreventionToken", instance.proxmoxCsrfToken)
                    }
                }
            }
            else -> {}
        }
    }

    private fun proactivelyRefreshProxmoxTicket(
        instance: com.homelab.app.domain.model.ServiceInstance
    ): com.homelab.app.domain.model.ServiceInstance {
        if (!shouldProactivelyRefreshProxmoxTicket(instance.token)) {
            return instance
        }

        val refreshed = try {
            runBlocking {
                proxmoxRepository.get().refreshTicket(
                    url = instance.url,
                    username = instance.username.orEmpty(),
                    currentTicket = instance.token,
                    allowSelfSigned = instance.allowSelfSigned
                )
            }
        } catch (_: Exception) {
            null
        }

        if (refreshed == null) {
            return instance
        }

        val refreshedInstance = instance.copy(
            token = refreshed.ticket,
            proxmoxCsrfToken = refreshed.csrfPreventionToken,
            username = refreshed.username
        )
        runBlocking {
            serviceInstancesRepository.saveInstance(refreshedInstance)
        }
        return refreshedInstance
    }

    private fun shouldProactivelyRefreshProxmoxTicket(ticket: String): Boolean {
        val issuedAt = proxmoxTicketIssuedAt(ticket) ?: return false
        val ageMillis = System.currentTimeMillis() - issuedAt
        val refreshLeadMillis = PROXMOX_TICKET_LIFETIME_MS - PROXMOX_REFRESH_LEAD_MS
        return ageMillis >= refreshLeadMillis
    }

    private fun proxmoxTicketIssuedAt(ticket: String): Long? {
        val parts = ticket.split(':')
        if (parts.size < 3) return null
        val issuedAtSeconds = parts[2].toLongOrNull(16) ?: return null
        if (issuedAtSeconds <= 0L) return null
        return issuedAtSeconds * 1000L
    }

    private companion object {
        const val PROXMOX_RETRY_HEADER = "X-Homelab-Proxmox-Retry"
        const val PROXMOX_TICKET_LIFETIME_MS = 2 * 60 * 60 * 1000L
        const val PROXMOX_REFRESH_LEAD_MS = 10 * 60 * 1000L
    }
}
