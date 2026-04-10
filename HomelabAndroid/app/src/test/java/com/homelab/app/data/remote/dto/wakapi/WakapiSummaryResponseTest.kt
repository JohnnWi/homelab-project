package com.homelab.app.data.remote.dto.wakapi

import junit.framework.TestCase.assertEquals
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import org.junit.Test

class WakapiSummaryResponseTest {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    @Test
    fun `decodes native wakapi summary payload`() {
        val payload = """
            {
              "user_id": "writeuser",
              "from": "2026-04-07T00:00:00Z",
              "to": "2026-04-07T23:59:59Z",
              "projects": [
                { "key": "Homelab", "total": 5400 }
              ],
              "languages": [
                { "key": "Swift", "total": 3600 },
                { "key": "Kotlin", "total": 1800 }
              ],
              "editors": [
                { "key": "Android Studio", "total": 5400 }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<WakapiSummaryResponse>(payload)

        assertEquals("writeuser", decoded.userId)
        assertEquals("Homelab", decoded.projects?.first()?.displayName)
        assertEquals(5400.0, decoded.projects?.first()?.resolvedTotalSeconds ?: 0.0)
        assertEquals(1, decoded.effectiveGrandTotal().resolvedHours)
        assertEquals(30, decoded.effectiveGrandTotal().resolvedMinutes)
    }

    @Test
    fun `keeps legacy compatible summary payload`() {
        val payload = """
            {
              "grand_total": {
                "hours": 2,
                "minutes": 15,
                "text": "2 hrs 15 mins",
                "total_seconds": 8100
              },
              "projects": [
                {
                  "name": "Homelab",
                  "hours": 2,
                  "minutes": 15,
                  "text": "2 hrs 15 mins",
                  "total_seconds": 8100,
                  "percent": 100
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<WakapiSummaryResponse>(payload)
        val total = decoded.effectiveGrandTotal()
        val project = decoded.projects?.first()

        assertEquals(2, total.resolvedHours)
        assertEquals(15, total.resolvedMinutes)
        assertEquals("Homelab", project?.displayName)
        assertEquals(100.0, project?.resolvedPercent(8100.0) ?: 0.0)
    }

    @Test
    fun `decodes compat daily summaries payload`() {
        val payload = """
            {
              "start": "2026-03-01T00:00:00Z",
              "end": "2026-04-07T23:59:59Z",
              "cumulative_total": {
                "decimal": "11.50",
                "digital": "11:30",
                "seconds": 41400,
                "text": "11 hrs 30 mins"
              },
              "daily_average": {
                "days_including_holidays": 30,
                "days_minus_holidays": 30,
                "holidays": 0,
                "seconds": 1380,
                "seconds_including_other_language": 1380,
                "text": "23 mins",
                "text_including_other_language": "23 mins"
              },
              "data": [
                {
                  "grand_total": {
                    "hours": 1,
                    "minutes": 30,
                    "text": "1 hr 30 mins",
                    "total_seconds": 5400
                  },
                  "languages": [
                    {
                      "name": "Swift",
                      "percent": 100,
                      "total_seconds": 5400,
                      "text": "1 hr 30 mins"
                    }
                  ],
                  "range": {
                    "date": "2026-04-06T00:00:00Z",
                    "start": "2026-04-06T00:00:00Z",
                    "end": "2026-04-06T23:59:59Z",
                    "timezone": "UTC"
                  }
                }
              ]
            }
        """.trimIndent()

        val decoded = json.decodeFromString<WakapiDailySummariesResponse>(payload)
        val firstDay = decoded.data.first()

        assertEquals(1, decoded.data.size)
        assertEquals(41400.0, decoded.cumulativeTotal?.seconds ?: 0.0)
        assertEquals(1380, decoded.dailyAverage?.seconds ?: 0)
        assertEquals(5400.0, firstDay.totalSeconds)
        assertEquals("Swift", firstDay.languages?.first()?.displayName)
        assertEquals("UTC", firstDay.range?.timezone)
    }
}
