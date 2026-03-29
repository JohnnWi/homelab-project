package com.homelab.app.data.remote

import android.content.Context
import com.homelab.app.data.local.SettingsManager
import com.homelab.app.data.repository.ServiceInstancesRepository
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.ServiceType
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test

class SmartFallbackInterceptorTest {

    @Test
    fun `rewrites request using instance id even when service header is missing`() {
        val context = mockk<Context>(relaxed = true)
        val settingsManager = mockk<SettingsManager>()
        val instancesRepository = mockk<ServiceInstancesRepository>()
        val interceptor = SmartFallbackInterceptor(context, settingsManager, instancesRepository)
        val chain = mockk<Interceptor.Chain>()
        val capturedRequest = slot<Request>()
        val request = Request.Builder()
            .url("https://placeholder.local/api/system")
            .header("X-Homelab-Instance-Id", "instance-9")
            .build()

        every { settingsManager.internalSsid } returns flowOf(null)
        coEvery { instancesRepository.getInstance("instance-9") } returns ServiceInstance(
            id = "instance-9",
            type = ServiceType.BESZEL,
            label = "Lab",
            url = "https://beszel.lab.local",
            token = "token"
        )
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } answers {
            response(capturedRequest.captured)
        }

        interceptor.intercept(chain)

        assertEquals("beszel.lab.local", capturedRequest.captured.url.host)
        assertEquals("https", capturedRequest.captured.url.scheme)
        assertEquals("/api/system", capturedRequest.captured.url.encodedPath)
    }

    @Test
    fun `preserves fallback base path when routing external requests`() {
        val context = mockk<Context>(relaxed = true)
        val settingsManager = mockk<SettingsManager>()
        val instancesRepository = mockk<ServiceInstancesRepository>()
        val interceptor = SmartFallbackInterceptor(context, settingsManager, instancesRepository)
        val chain = mockk<Interceptor.Chain>()
        val capturedRequest = slot<Request>()
        val request = Request.Builder()
            .url("https://placeholder.local/api/v3/system/status")
            .header("X-Homelab-Instance-Id", "instance-servarr")
            .build()

        every { settingsManager.internalSsid } returns flowOf(null)
        coEvery { instancesRepository.getInstance("instance-servarr") } returns ServiceInstance(
            id = "instance-servarr",
            type = ServiceType.SONARR,
            label = "Sonarr",
            url = "http://192.168.1.20:8989",
            fallbackUrl = "https://example.com/sonarr",
            apiKey = "token"
        )
        every { chain.request() } returns request
        every { chain.proceed(capture(capturedRequest)) } answers {
            response(capturedRequest.captured)
        }

        interceptor.intercept(chain)

        assertEquals("example.com", capturedRequest.captured.url.host)
        assertEquals("/sonarr/api/v3/system/status", capturedRequest.captured.url.encodedPath)
    }

    private fun response(request: Request): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(200)
            .message("OK")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
