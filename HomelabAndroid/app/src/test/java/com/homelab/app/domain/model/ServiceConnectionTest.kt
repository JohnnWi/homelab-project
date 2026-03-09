package com.homelab.app.domain.model

import com.homelab.app.util.ServiceType
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNull
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Test

class ServiceConnectionTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun `serializes pihole auth fields`() {
        val connection = ServiceConnection(
            type = ServiceType.PIHOLE,
            url = "https://pihole.local",
            token = "sid123",
            piholePassword = "secret",
            piholeAuthMode = PiHoleAuthMode.SESSION
        )

        val encoded = json.encodeToString(connection)
        val decoded = json.decodeFromString<ServiceConnection>(encoded)

        assertEquals("sid123", decoded.token)
        assertEquals("secret", decoded.piHoleStoredSecret)
        assertEquals(PiHoleAuthMode.SESSION, decoded.piholeAuthMode)
    }

    @Test
    fun `old pihole payload falls back to apiKey secret`() {
        val payload = """
            {
              "type": "PIHOLE",
              "url": "https://pihole.local",
              "token": "legacy-token",
              "apiKey": "legacy-secret"
            }
        """.trimIndent()

        val decoded = json.decodeFromString<ServiceConnection>(payload)

        assertEquals(ServiceType.PIHOLE, decoded.type)
        assertEquals("legacy-secret", decoded.piHoleStoredSecret)
        assertNull(decoded.piholePassword)
        assertNull(decoded.piholeAuthMode)
    }
}
