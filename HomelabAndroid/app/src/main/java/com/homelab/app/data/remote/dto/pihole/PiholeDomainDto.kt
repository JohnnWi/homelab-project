package com.homelab.app.data.remote.dto.pihole

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlin.math.abs

@Serializable
enum class PiholeDomainListType(val value: String) {
    ALLOW("allow"),
    DENY("deny")
}

@Serializable
data class PiholeDomainListResponse(
    val domains: List<PiholeDomainDto> = emptyList()
) {
    companion object {
        fun fromJson(json: JsonElement): PiholeDomainListResponse {
            if (json !is JsonObject) return PiholeDomainListResponse()

            val v6Domains = parseV6Domains(json["domains"])
            if (v6Domains.isNotEmpty()) return PiholeDomainListResponse(v6Domains)

            var nextId = 1
            val normalized = mutableListOf<PiholeDomainDto>()

            fun append(values: List<String>, listType: PiholeDomainListType, kind: String) {
                values.filter { it.isNotBlank() }.forEach { domain ->
                    normalized.add(
                        PiholeDomainDto(
                            id = nextId++,
                            domain = domain,
                            kind = kind,
                            list = listType.value
                        )
                    )
                }
            }

            append(decodeLegacyList(json["allow"]), PiholeDomainListType.ALLOW, "exact")
            append(decodeLegacyList(json["deny"]), PiholeDomainListType.DENY, "exact")
            append(decodeLegacyList(json["whitelist"]), PiholeDomainListType.ALLOW, "exact")
            append(decodeLegacyList(json["blacklist"]), PiholeDomainListType.DENY, "exact")
            append(decodeLegacyList(json["regex_whitelist"]), PiholeDomainListType.ALLOW, "regex")
            append(decodeLegacyList(json["regex_blacklist"]), PiholeDomainListType.DENY, "regex")

            return PiholeDomainListResponse(normalized)
        }

        private fun parseV6Domains(element: JsonElement?): List<PiholeDomainDto> {
            val entries = element as? JsonArray ?: return emptyList()
            return entries.mapNotNull { item ->
                val obj = item as? JsonObject ?: return@mapNotNull null
                val domain = obj["domain"]?.jsonPrimitive?.contentOrNull ?: return@mapNotNull null
                val kind = obj["kind"]?.jsonPrimitive?.contentOrNull ?: "exact"
                val list = obj["list"]?.jsonPrimitive?.contentOrNull
                val id = obj["id"]?.jsonPrimitive?.intOrNull
                    ?: obj["id"]?.jsonPrimitive?.contentOrNull?.toIntOrNull()
                    ?: syntheticId(domain = domain, kind = kind, list = list)
                PiholeDomainDto(id = id, domain = domain, kind = kind, list = list)
            }
        }

        private fun decodeLegacyList(element: JsonElement?): List<String> {
            val arr = element as? JsonArray ?: return emptyList()
            return arr.mapNotNull { item ->
                when (item) {
                    is JsonObject -> item["domain"]?.jsonPrimitive?.contentOrNull
                    else -> item.jsonPrimitive.contentOrNull
                }
            }
        }

        private fun syntheticId(domain: String, kind: String, list: String?): Int {
            val seed = "${list ?: "unknown"}|$kind|$domain"
            return abs(seed.fold(5381) { hash, ch -> ((hash shl 5) + hash) + ch.code })
        }
    }
}

@Serializable
data class PiholeDomainDto(
    val id: Int,
    val domain: String,
    val kind: String, // "exact" or "regex"
    val list: String? = null
) {
    val type: PiholeDomainListType?
        get() = PiholeDomainListType.entries.find { it.value == list }
}
