package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.util.ServiceType
import io.mockk.every
import io.mockk.mockk
import java.io.IOException
import kotlinx.coroutines.test.runTest
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaArrRepositoryTest {

    @Test
    fun `servarr authentication only sends api key header`() = runTest {
        val requests = mutableListOf<Request>()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor { chain ->
                requests += chain.request()
                success(chain.request())
            }
            .build()
        val tlsClientSelector = mockk<TlsClientSelector>()
        every { tlsClientSelector.forAllowSelfSigned(false) } returns okHttpClient
        val repository = MediaArrRepository(
            serviceInstancesRepository = mockk(relaxed = true),
            tlsClientSelector = tlsClientSelector
        )

        repository.authenticateWithApiKey(
            url = "https://radarr.example.com",
            serviceType = ServiceType.RADARR,
            apiKey = "radarr-key"
        )

        val request = requests.single()
        assertEquals("radarr-key", request.header("X-Api-Key"))
        assertNull(request.header("Authorization"))
        assertEquals("true", request.header("X-Homelab-Bypass"))
        assertEquals("/api/v3/system/status", request.url.encodedPath)
    }

    @Test
    fun `authentication falls back to secondary url when primary probe fails`() = runTest {
        val hosts = mutableListOf<String>()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                hosts += request.url.host
                if (request.url.host == "primary.example.com") {
                    throw IOException("primary down")
                }
                success(request)
            })
            .build()
        val tlsClientSelector = mockk<TlsClientSelector>()
        every { tlsClientSelector.forAllowSelfSigned(false) } returns okHttpClient
        val repository = MediaArrRepository(
            serviceInstancesRepository = mockk(relaxed = true),
            tlsClientSelector = tlsClientSelector
        )

        repository.authenticateWithApiKey(
            url = "https://primary.example.com/sonarr",
            serviceType = ServiceType.SONARR,
            apiKey = "sonarr-key",
            fallbackUrl = "https://fallback.example.com/sonarr"
        )

        assertEquals(listOf("primary.example.com", "fallback.example.com"), hosts)
    }

    @Test
    fun `qbittorrent authentication falls back to secondary url`() = runTest {
        val hosts = mutableListOf<String>()
        val okHttpClient = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                hosts += request.url.host
                if (request.url.host == "primary.example.com") {
                    throw IOException("primary down")
                }
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .addHeader("Set-Cookie", "SID=abc123; HttpOnly")
                    .body("Ok.".toResponseBody("text/plain".toMediaType()))
                    .build()
            })
            .build()
        val tlsClientSelector = mockk<TlsClientSelector>()
        every { tlsClientSelector.forAllowSelfSigned(false) } returns okHttpClient
        val repository = MediaArrRepository(
            serviceInstancesRepository = mockk(relaxed = true),
            tlsClientSelector = tlsClientSelector
        )

        val sid = repository.authenticateQbittorrent(
            url = "https://primary.example.com/qbt",
            username = "user",
            password = "pass",
            fallbackUrl = "https://fallback.example.com/qbt"
        )

        assertEquals("abc123", sid)
        assertEquals(listOf("primary.example.com", "fallback.example.com"), hosts)
    }

    private fun success(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
