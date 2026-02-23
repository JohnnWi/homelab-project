package com.homelab.app.data.remote.interceptor

import com.homelab.app.data.local.SettingsManager
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import javax.inject.Inject

class SmartFallbackInterceptor @Inject constructor(
    private val settingsManager: SettingsManager
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        val originalRequest = chain.request()

        if (originalRequest.header("X-Homelab-Bypass") == "true") {
            return chain.proceed(originalRequest)
        }

        val serviceType = serviceTypeFromHeader(originalRequest.header("X-Homelab-Service"))
        val connection = runBlocking { settingsManager.getConnection(serviceType).firstOrNull() }

        try {
            return chain.proceed(originalRequest)
        } catch (e: IOException) {
            connection?.fallbackUrl?.toHttpUrlOrNull()?.let { fallbackHttpUrl ->
                val newUrl = originalRequest.url.newBuilder()
                    .scheme(fallbackHttpUrl.scheme)
                    .host(fallbackHttpUrl.host)
                    .port(fallbackHttpUrl.port)
                    .build()
                val newRequest = originalRequest.newBuilder().url(newUrl).build()
                return chain.proceed(newRequest)
            }
            throw e
        }
    }

    private fun serviceTypeFromHeader(header: String?): ServiceType {
        return ServiceType.entries.find { it.name.equals(header, ignoreCase = true) } ?: ServiceType.UNKNOWN
    }
}
