package com.homelab.app.data.remote

import com.homelab.app.data.repository.BeszelRepository
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
    private val beszelRepository: dagger.Lazy<BeszelRepository>
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        val instanceIdHeader = request.header("X-Homelab-Instance-Id")
        val bypassHeader = request.header("X-Homelab-Bypass")

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

        val instance = if (bypassHeader == "true" || instanceIdHeader.isNullOrBlank()) {
            null
        } else {
            runBlocking { serviceInstancesRepository.getInstance(instanceIdHeader) }
        }

        if (instance != null) {
            val hasAuthorization = request.header("Authorization") != null
            addAuthHeaders(requestBuilder, instance, hasAuthorization)
        }

        request = requestBuilder.build()
        var response = chain.proceed(request)

        // Auto-retry for Beszel on auth failure (401 or 400 for PocketBase)
        if (instance != null &&
            instance.type == ServiceType.BESZEL &&
            (response.code == 401 || response.code == 400) &&
            bypassHeader != "true" &&
            !instance.username.isNullOrBlank() &&
            !instance.password.isNullOrBlank()
        ) {
            val newToken = try {
                runBlocking {
                    beszelRepository.get().authenticate(instance.url, instance.username, instance.password)
                }
            } catch (_: Exception) { null }

            if (newToken != null) {
                // Persist the refreshed token
                runBlocking {
                    serviceInstancesRepository.saveInstance(instance.copy(token = newToken))
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

        if (response.code == 401 &&
            bypassHeader != "true" &&
            instance != null &&
            instance.type != ServiceType.PIHOLE &&
            !instanceIdHeader.isNullOrBlank()
        ) {
            globalEventBus.emitAuthError(instanceIdHeader)
        }

        return response
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
                }
            }
            else -> {}
        }
    }
}
