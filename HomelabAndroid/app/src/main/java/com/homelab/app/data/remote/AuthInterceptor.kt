package com.homelab.app.data.remote

import com.homelab.app.data.local.SettingsManager
import com.homelab.app.util.GlobalEventBus
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.Response
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthInterceptor @Inject constructor(
    private val globalEventBus: GlobalEventBus,
    private val settingsManager: SettingsManager
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()
        
        // Extract the custom header inserted by Retrofit interfaces to identify the service
        val serviceHeader = request.header("X-Homelab-Service")
        val bypassHeader = request.header("X-Homelab-Bypass")
        
        val serviceType = when (serviceHeader) {
            "Portainer" -> ServiceType.PORTAINER
            "Pihole" -> ServiceType.PIHOLE
            "Beszel" -> ServiceType.BESZEL
            "Gitea" -> ServiceType.GITEA
            else -> ServiceType.UNKNOWN
        }

        val requestBuilder = request.newBuilder()

        // Clean up internal headers before sending to real server
        if (serviceHeader != null) {
            requestBuilder.removeHeader("X-Homelab-Service")
        }
        if (bypassHeader != null) {
            requestBuilder.removeHeader("X-Homelab-Bypass")
        }

        // Attach Authorization headers if available for the specific service and NOT bypassing
        if (serviceType != ServiceType.UNKNOWN && bypassHeader != "true") {
            val connection = runBlocking { settingsManager.getConnection(serviceType).firstOrNull() }
            if (connection != null) {
                when (serviceType) {
                    ServiceType.PORTAINER -> {
                        if (!connection.apiKey.isNullOrBlank()) {
                            requestBuilder.addHeader("X-API-Key", connection.apiKey)
                        } else if (connection.token.isNotBlank()) {
                            requestBuilder.addHeader("Authorization", "Bearer ${connection.token}")
                        }
                    }
                    ServiceType.PIHOLE -> {
                        // Support Pi-hole v6 X-FTL-SID header and v5 sid query param fallback (handled in Repo)
                        if (connection.token.isNotBlank()) {
                            requestBuilder.addHeader("X-FTL-SID", connection.token)
                        }
                    }
                    ServiceType.BESZEL -> {
                        if (connection.token.isNotBlank()) {
                            // Pocketbase via BeszelAPIClient.swift expects literal token without 'Bearer ' prefix
                            requestBuilder.addHeader("Authorization", connection.token)
                        }
                    }
                    ServiceType.GITEA -> {
                        if (connection.token.isNotBlank()) {
                            if (connection.token.startsWith("basic:")) {
                                val credentials = connection.token.removePrefix("basic:")
                                requestBuilder.addHeader("Authorization", "Basic $credentials")
                            } else {
                                requestBuilder.addHeader("Authorization", "token ${connection.token}")
                            }
                        }
                    }
                    else -> {}
                }
            }
        }

        request = requestBuilder.build()
        val response = chain.proceed(request)

        if (response.code == 401 && bypassHeader != "true" && serviceType != ServiceType.UNKNOWN) {
            // Emits specific service failure replacing iOS NotificationCenter(.serviceUnauthorized)
            // Only if NOT bypassing (e.g. login) and service is known
            globalEventBus.emitAuthError(serviceType)
        }

        return response
    }
}
