package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.data.remote.api.MaltrailApi
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.security.MessageDigest

data class MaltrailCountPoint(
    val timestamp: Long,
    val count: Int
) {
    val apiDate: String
        get() = Instant.ofEpochSecond(timestamp)
            .atZone(ZoneOffset.UTC)
            .toLocalDate()
            .format(DateTimeFormatter.ISO_LOCAL_DATE)

    val displayDate: String
        get() = displayDateFormatter.format(
            Instant.ofEpochSecond(timestamp).atZone(ZoneId.systemDefault())
        )

    companion object {
        private val displayDateFormatter: DateTimeFormatter =
            DateTimeFormatter.ofLocalizedDate(FormatStyle.MEDIUM).withLocale(Locale.getDefault())
    }
}

data class MaltrailEvent(
    val id: String,
    val timestamp: String?,
    val source: String?,
    val destination: String?,
    val protocolName: String?,
    val trail: String?,
    val severity: String?,
    val sensor: String?,
    val info: String?,
    val rawFields: Map<String, String>
) {
    val title: String
        get() = trail?.takeIf { it.isNotBlank() }
            ?: info?.takeIf { it.isNotBlank() }
            ?: destination?.takeIf { it.isNotBlank() }
            ?: "Maltrail event"

    val route: String
        get() = listOfNotNull(source, destination)
            .filter { it.isNotBlank() }
            .joinToString(" -> ")

    val normalizedSeverity: String
        get() = severity?.lowercase(Locale.ROOT).orEmpty()
}

data class MaltrailDashboardData(
    val counts: List<MaltrailCountPoint>,
    val selectedDate: String,
    val events: List<MaltrailEvent>
) {
    val latestCount: Int get() = counts.firstOrNull()?.count ?: 0
    val totalFindings: Int get() = counts.sumOf { it.count }
}

data class MaltrailSummary(
    val latestCount: Int,
    val latestDayLabel: String,
    val totalFindings: Int
)

@Singleton
class MaltrailRepository @Inject constructor(
    private val api: MaltrailApi,
    private val tlsClientSelector: TlsClientSelector
) {

    suspend fun authenticate(
        url: String,
        username: String? = null,
        password: String? = null,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ): String {
        val baseCandidates = listOf(cleanUrl(url), cleanOptionalUrl(fallbackUrl))
            .filterNotNull()
            .distinct()
        val cleanUsername = username?.trim().orEmpty()
        val cleanPassword = password.orEmpty()

        var lastError: Exception? = null
        for (base in baseCandidates) {
            try {
                return if (cleanUsername.isBlank() && cleanPassword.isBlank()) {
                    validateCounts(base, allowSelfSigned)
                    ""
                } else {
                    require(cleanUsername.isNotBlank() && cleanPassword.isNotBlank()) {
                        "Maltrail username and password must both be provided."
                    }
                    val cookie = authenticateAgainst(base, cleanUsername, cleanPassword, allowSelfSigned)
                    validateCounts(base, allowSelfSigned, cookie)
                    cookie
                }
            } catch (error: Exception) {
                lastError = when (error) {
                    is IllegalArgumentException -> error
                    else -> Exception(error.message ?: "Maltrail validation failed.", error)
                }
            }
        }
        throw lastError ?: IllegalStateException("Maltrail validation failed.")
    }

    suspend fun getDashboard(instanceId: String, selectedDate: String? = null): MaltrailDashboardData = coroutineScope {
        val countsDeferred = async { getCounts(instanceId) }
        val counts = countsDeferred.await()
        val date = selectedDate?.takeIf { it.isNotBlank() }
            ?: counts.firstOrNull()?.apiDate
            ?: LocalDate.now(ZoneOffset.UTC).format(DateTimeFormatter.ISO_LOCAL_DATE)
        val events = runCatching { getEvents(instanceId, date) }.getOrDefault(emptyList())
        MaltrailDashboardData(counts = counts, selectedDate = date, events = events)
    }

    suspend fun getCounts(instanceId: String): List<MaltrailCountPoint> {
        return parseCounts(api.getCounts(instanceId = instanceId))
    }

    suspend fun getEvents(instanceId: String, date: String): List<MaltrailEvent> {
        return parseEvents(api.getEvents(instanceId = instanceId, date = date))
    }

    suspend fun getSummary(instanceId: String): MaltrailSummary {
        val counts = getCounts(instanceId)
        val latest = counts.firstOrNull()
        return MaltrailSummary(
            latestCount = latest?.count ?: 0,
            latestDayLabel = latest?.displayDate.orEmpty(),
            totalFindings = counts.sumOf { it.count }
        )
    }

    private suspend fun validateCounts(
        baseUrl: String,
        allowSelfSigned: Boolean,
        cookie: String? = null
    ) = withContext(Dispatchers.IO) {
        val requestBuilder = Request.Builder()
            .url("$baseUrl/counts")
            .get()
            .addHeader("Accept", "application/json")
        if (!cookie.isNullOrBlank()) {
            requestBuilder.addHeader("Cookie", cookie)
        }
        val request = requestBuilder.build()

        tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
            .newCall(request)
            .execute()
            .use { response ->
                when (response.code) {
                    in 200..399 -> Unit
                    401 -> throw IllegalStateException("Maltrail returned HTTP 401.")
                    else -> throw IllegalStateException("Maltrail returned HTTP ${response.code}.")
                }
            }
    }

    private suspend fun authenticateAgainst(
        baseUrl: String,
        username: String,
        password: String,
        allowSelfSigned: Boolean
    ): String = withContext(Dispatchers.IO) {
        val nonce = createNonce()
        val payload = FormBody.Builder()
            .add("username", username)
            .add("nonce", nonce)
            .add("hash", createLoginHash(password, nonce))
            .build()

        val request = Request.Builder()
            .url("$baseUrl/login")
            .post(payload)
            .addHeader("Accept", "text/plain")
            .build()

        tlsClientSelector.forAllowSelfSigned(allowSelfSigned)
            .newCall(request)
            .execute()
            .use { response ->
                when (response.code) {
                    in 200..399 -> extractCookie(response)
                        ?: throw IllegalStateException("Maltrail login succeeded without a session cookie.")
                    401 -> throw IllegalStateException("Maltrail returned HTTP 401.")
                    else -> throw IllegalStateException("Maltrail returned HTTP ${response.code}.")
                }
            }
    }

    private fun parseCounts(element: JsonElement): List<MaltrailCountPoint> {
        val source = when (element) {
            is JsonObject -> {
                val nested = element["counts"] ?: element["data"] ?: element["results"]
                nested as? JsonObject ?: element
            }
            else -> return emptyList()
        }

        return source.mapNotNull { (key, value) ->
            val timestamp = key.toLongOrNull() ?: return@mapNotNull null
            val count = value.intValueOrNull() ?: return@mapNotNull null
            MaltrailCountPoint(timestamp = timestamp, count = count)
        }.sortedByDescending { it.timestamp }
    }

    private fun parseEvents(element: JsonElement): List<MaltrailEvent> {
        return extractObjectArray(element, "events", "data", "items", "results").mapIndexed { index, obj ->
            val raw = obj.toFlatStringMap()
            val timestamp = obj.stringOrNull("timestamp", "time", "datetime", "date")
            val source = obj.stringOrNull("src_ip", "source_ip", "source", "src", "client", "ip")
            val destination = obj.stringOrNull("dst_ip", "destination_ip", "destination", "dst", "server", "host")
            val protocol = obj.stringOrNull("proto", "protocol", "protocolName")
            val trail = obj.stringOrNull("trail", "indicator", "ioc", "signature", "threat")
            val severity = obj.stringOrNull("severity", "level", "priority", "risk")
            val sensor = obj.stringOrNull("sensor", "sensor_name", "node")
            val info = obj.stringOrNull("info", "message", "description", "details")
            MaltrailEvent(
                id = obj.stringOrNull("id", "event_id", "uid")
                    ?: "$timestamp|$source|$destination|$trail|$index",
                timestamp = timestamp,
                source = source,
                destination = destination,
                protocolName = protocol,
                trail = trail,
                severity = severity,
                sensor = sensor,
                info = info,
                rawFields = raw
            )
        }
    }

    private fun extractObjectArray(element: JsonElement, vararg keys: String): List<JsonObject> {
        when (element) {
            is JsonArray -> return element.mapNotNull { it as? JsonObject }
            is JsonObject -> {
                for (key in keys) {
                    when (val candidate = element[key]) {
                        is JsonArray -> return candidate.mapNotNull { it as? JsonObject }
                        is JsonObject -> {
                            val nested = extractObjectArray(candidate, *keys)
                            if (nested.isNotEmpty()) return nested
                        }
                        else -> Unit
                    }
                }
                val objectValues = element.values.mapNotNull { it as? JsonObject }
                if (objectValues.isNotEmpty() && objectValues.size == element.size) return objectValues
            }
            else -> Unit
        }
        return emptyList()
    }

    private fun JsonElement.intValueOrNull(): Int? {
        return when (this) {
            is JsonPrimitive -> intOrNull ?: contentOrNull?.toDoubleOrNull()?.toInt()
            else -> null
        }
    }

    private fun JsonObject.stringOrNull(vararg keys: String): String? {
        for (key in keys) {
            val value = this[key] ?: continue
            when (value) {
                is JsonPrimitive -> value.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                is JsonObject -> value.stringOrNull("name", "id", "value", "text")?.let { return it }
                is JsonArray -> value.firstOrNull()?.let { first ->
                    when (first) {
                        is JsonPrimitive -> first.contentOrNull?.takeIf { it.isNotBlank() }?.let { return it }
                        is JsonObject -> first.stringOrNull("name", "id", "value", "text")?.let { return it }
                        else -> Unit
                    }
                }
                is JsonNull -> Unit
            }
        }
        return null
    }

    private fun JsonObject.toFlatStringMap(): Map<String, String> {
        return mapNotNull { (key, value) ->
            val text = when (value) {
                is JsonPrimitive -> value.contentOrNull
                is JsonObject -> value.stringOrNull("name", "id", "value", "text")
                is JsonArray -> value.joinToString(", ") { item ->
                    when (item) {
                        is JsonPrimitive -> item.contentOrNull.orEmpty()
                        is JsonObject -> item.stringOrNull("name", "id", "value", "text").orEmpty()
                        else -> ""
                    }
                }.ifBlank { null }
                is JsonNull -> null
            }
            text?.takeIf { it.isNotBlank() }?.let { key to it }
        }.toMap()
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.trim()
        clean = clean.trimEnd { it == ')' || it == ']' || it == '}' || it == ',' || it == ';' }
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) {
            clean = "https://$clean"
        }
        return clean.replace(Regex("/+$"), "")
    }

    private fun cleanOptionalUrl(raw: String?): String? {
        val value = raw?.trim().orEmpty()
        return if (value.isBlank()) null else cleanUrl(value)
    }

    private fun extractCookie(response: Response): String? {
        return response.headers("Set-Cookie")
            .firstOrNull()
            ?.substringBefore(';')
            ?.trim()
            ?.ifBlank { null }
    }

    private fun createNonce(): String {
        val bytes = ByteArray(16)
        java.security.SecureRandom().nextBytes(bytes)
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun createLoginHash(password: String, nonce: String): String {
        return sha256("${sha256(password)}$nonce")
    }

    private fun sha256(value: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}
