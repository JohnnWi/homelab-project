package com.homelab.app.data.remote

import com.homelab.app.util.Logger
import okhttp3.Interceptor
import okhttp3.Response
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DebugLoggingInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val startNs = System.nanoTime()
        val tag = extractServiceTag(request.url.host)

        Logger.net(tag, "--> ${request.method} ${request.url}")

        val response = try {
            chain.proceed(request)
        } catch (e: Exception) {
            Logger.e(tag, "x ${request.method} ${request.url}", e)
            throw e
        }

        val tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNs)
        val contentType = response.header("Content-Type") ?: "unknown"
        val msg = "<-- ${response.code} ${request.method} ${request.url} (${tookMs}ms, $contentType)"
        if (response.code >= 400) {
            Logger.w(tag, msg)
        } else {
            Logger.net(tag, msg)
        }

        return response
    }

    private fun extractServiceTag(host: String): String {
        // For IP addresses or localhost, just use "Network"
        val parts = host.split(".")
        if (parts.size <= 1 || parts[0].all { it.isDigit() || it == ':' }) return "Network"
        // Extract first subdomain: "beszel.cipnas.org" -> "Beszel"
        return parts[0].replaceFirstChar { it.uppercase() }
    }
}
