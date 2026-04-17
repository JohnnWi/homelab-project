package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.JellystatApi
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class JellystatRepositoryTest {

    @Test
    fun `getWatchSummary parses payload and aggregates correctly`() = runTest {
        val api = mockk<JellystatApi>()
        val tlsClientSelector = mockk<TlsClientSelector>(relaxed = true)
        val repository = JellystatRepository(api, tlsClientSelector)

        coEvery { api.getViewsByLibraryType(instanceId = "instance-1", days = 1) } returns JsonObject(
            mapOf(
                "Audio" to JsonPrimitive("5"),
                "Movie" to JsonPrimitive(2),
                "Series" to JsonPrimitive("1"),
                "Other" to JsonPrimitive(0)
            )
        )
        coEvery { api.getViewsOverTime(instanceId = "instance-1", days = 1) } returns JsonObject(
            mapOf(
                "stats" to JsonArray(
                    listOf(
                        JsonObject(
                            mapOf(
                                "Key" to JsonPrimitive("Mar 2, 2026"),
                                "Movie" to JsonObject(
                                    mapOf(
                                        "count" to JsonPrimitive("2"),
                                        "duration" to JsonPrimitive("30")
                                    )
                                ),
                                "Audio" to JsonObject(
                                    mapOf(
                                        "count" to JsonPrimitive(1),
                                        "duration" to JsonPrimitive(10)
                                    )
                                )
                            )
                        ),
                        JsonObject(
                            mapOf(
                                "key" to JsonPrimitive("Mar 1, 2026"),
                                "Series" to JsonObject(
                                    mapOf(
                                        "Count" to JsonPrimitive("1"),
                                        "Duration" to JsonPrimitive("60")
                                    )
                                )
                            )
                        )
                    )
                )
            )
        )

        val summary = repository.getWatchSummary(instanceId = "instance-1", days = 0)

        assertEquals(1, summary.days)
        assertEquals(8, summary.totalViews)
        assertEquals(2, summary.activeDays)
        assertEquals("Series", summary.topLibraryName)
        assertEquals(1.0, summary.topLibraryHours, 0.0001)
        assertEquals(1.6666, summary.totalHours, 0.01)
        assertEquals("Mar 1, 2026", summary.points.first().key)
        assertEquals("Mar 2, 2026", summary.points.last().key)
        assertEquals(1, summary.points.first().totalViews)
        assertEquals(3, summary.points.last().totalViews)

        coVerify(exactly = 1) { api.getViewsByLibraryType(instanceId = "instance-1", days = 1) }
        coVerify(exactly = 1) { api.getViewsOverTime(instanceId = "instance-1", days = 1) }
    }

    @Test
    fun `getWatchSummary clamps days to max limit`() = runTest {
        val api = mockk<JellystatApi>()
        val tlsClientSelector = mockk<TlsClientSelector>(relaxed = true)
        val repository = JellystatRepository(api, tlsClientSelector)

        coEvery { api.getViewsByLibraryType(instanceId = "instance-2", days = 3650) } returns JsonObject(
            mapOf(
                "Audio" to JsonPrimitive(0),
                "Movie" to JsonPrimitive(0),
                "Series" to JsonPrimitive(0),
                "Other" to JsonPrimitive(0)
            )
        )
        coEvery { api.getViewsOverTime(instanceId = "instance-2", days = 3650) } returns JsonObject(
            mapOf("stats" to JsonArray(emptyList()))
        )

        val summary = repository.getWatchSummary(instanceId = "instance-2", days = 99999)

        assertEquals(3650, summary.days)
        coVerify(exactly = 1) { api.getViewsByLibraryType(instanceId = "instance-2", days = 3650) }
        coVerify(exactly = 1) { api.getViewsOverTime(instanceId = "instance-2", days = 3650) }
    }

    @Test
    fun `authenticate sends token header to jellystat endpoint`() = runTest {
        val api = mockk<JellystatApi>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>()
        val tlsClientSelector = mockk<TlsClientSelector>()
        val call = mockk<Call>()
        val requestSlot = slot<Request>()
        val repository = JellystatRepository(api, tlsClientSelector)

        every { tlsClientSelector.forAllowSelfSigned(false) } returns okHttpClient
        every { okHttpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers { response(requestSlot.captured, 200) }

        repository.authenticate(url = "jellystat.local/", apiKey = "  api-token  ")

        assertEquals(
            "https://jellystat.local/stats/getViewsByLibraryType?days=1",
            requestSlot.captured.url.toString()
        )
        assertEquals("api-token", requestSlot.captured.header("X-API-Token"))
        verify(exactly = 1) { call.execute() }
    }

    @Test
    fun `authenticate throws for non successful response`() = runTest {
        val api = mockk<JellystatApi>(relaxed = true)
        val okHttpClient = mockk<OkHttpClient>()
        val tlsClientSelector = mockk<TlsClientSelector>()
        val call = mockk<Call>()
        val requestSlot = slot<Request>()
        val repository = JellystatRepository(api, tlsClientSelector)

        every { tlsClientSelector.forAllowSelfSigned(false) } returns okHttpClient
        every { okHttpClient.newCall(capture(requestSlot)) } returns call
        every { call.execute() } answers { response(requestSlot.captured, 401) }

        val error = runCatching {
            repository.authenticate(url = "https://jellystat.local", apiKey = "bad-token")
        }.exceptionOrNull()

        assertTrue(error is IllegalStateException)
        assertEquals("Jellystat authentication failed", error?.message)
    }

    private fun response(request: Request, code: Int): Response {
        return Response.Builder()
            .request(request)
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message(if (code == 200) "OK" else "Unauthorized")
            .body("{}".toResponseBody("application/json".toMediaType()))
            .build()
    }
}
