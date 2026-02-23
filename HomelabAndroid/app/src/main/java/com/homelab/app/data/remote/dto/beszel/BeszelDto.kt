package com.homelab.app.data.remote.dto.beszel

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class BeszelSystem(
    val id: String,
    val collectionId: String? = null,
    val collectionName: String? = null,
    val name: String,
    val host: String,
    // Port can be Int or String in the JSON, custom deserializer or raw element if needed.
    // We'll use JsonElement to handle both cases safely.
    val port: JsonElement? = null,
    val status: String,
    val info: BeszelSystemInfo? = null,
    val created: String? = null,
    val updated: String? = null
) {
    val isOnline: Boolean get() = status == "up"

    val portValue: Int? get() {
        return port?.jsonPrimitive?.intOrNull ?: port?.jsonPrimitive?.content?.toIntOrNull()
    }
}

@Serializable
data class BeszelSystemInfo(
    val cpu: Double? = null,
    val mp: Double? = null,
    val m: Double? = null,
    val mt: Double? = null,
    val dp: Double? = null,
    val d: Double? = null,
    val dt: Double? = null,
    val ns: Double? = null,
    val nr: Double? = null,
    val u: Double? = null,
    val cm: String? = null,
    val os: String? = null,
    val k: String? = null,
    val h: String? = null,
    val t: Double? = null,
    val c: Int? = null
) {
    val cpuValue: Double get() = cpu ?: 0.0
    val mpValue: Double get() = mp ?: 0.0
    val mValue: Double get() = m ?: 0.0
    val mtValue: Double get() = mt ?: 0.0
    val dpValue: Double get() = dp ?: 0.0
    val dValue: Double get() = d ?: 0.0
    val dtValue: Double get() = dt ?: 0.0
    val nsValue: Double get() = ns ?: 0.0
    val nrValue: Double get() = nr ?: 0.0
    val uValue: Double get() = u ?: 0.0
}

@Serializable
data class BeszelSystemsResponse(
    val items: List<BeszelSystem>,
    val totalItems: Int? = null,
    val page: Int? = null,
    val perPage: Int? = null
)

@Serializable
data class BeszelSystemRecord(
    val id: String,
    val system: String? = null,
    val stats: BeszelRecordStats,
    val created: String? = null,
    val updated: String? = null
)

@Serializable
data class BeszelRecordStats(
    val cpu: Double? = null,
    val mp: Double? = null,
    val m: Double? = null,
    val mt: Double? = null,
    val dp: Double? = null,
    val d: Double? = null,
    val dt: Double? = null,
    val ns: Double? = null,
    val nr: Double? = null,
    val t: List<Double>? = null,
    val dc: List<BeszelContainer>? = null
) {
    val cpuValue: Double get() = cpu ?: 0.0
    val mpValue: Double get() = mp ?: 0.0
    val mValue: Double get() = m ?: 0.0
    val mtValue: Double get() = mt ?: 0.0
    val dpValue: Double get() = dp ?: 0.0
    val dValue: Double get() = d ?: 0.0
    val dtValue: Double get() = dt ?: 0.0
    val nsValue: Double get() = ns ?: 0.0
    val nrValue: Double get() = nr ?: 0.0
}

@Serializable
data class BeszelContainer(
    val n: String,
    val cpu: Double? = null,
    val m: Double? = null
) {
    val id: String get() = n
    val name: String get() = n
    val cpuValue: Double get() = cpu ?: 0.0
    val mValue: Double get() = m ?: 0.0
}

@Serializable
data class BeszelRecordsResponse(
    val items: List<BeszelSystemRecord>,
    val totalItems: Int? = null,
    val page: Int? = null,
    val perPage: Int? = null
)

@Serializable
data class BeszelAuthResponse(
    val token: String,
    val record: BeszelUserRecord? = null
)

@Serializable
data class BeszelUserRecord(
    val id: String,
    val email: String? = null,
    val username: String? = null
)
