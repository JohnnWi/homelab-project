@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package com.homelab.app.data.remote.dto.nginxpm

import java.time.Instant
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonEncoder
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.KSerializer

@Serializable
data class NpmTokenResponse(
    val token: String,
    val expires: String? = null
)

@Serializable
data class NpmTokenResult(
    val result: NpmTokenResponse? = null,
    val token: String? = null,
    val expires: String? = null
) {
    val resolvedToken: String
        get() = result?.token ?: token ?: ""
}

@Serializable
data class NpmHostReport(
    val proxy: Int = 0,
    val redirection: Int = 0,
    val stream: Int = 0,
    val dead: Int = 0
) {
    val total: Int get() = proxy + redirection + stream + dead
}

@Serializable
data class NpmProxyHost(
    val id: Int,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("domain_names") val domainNames: List<String> = emptyList(),
    @SerialName("forward_host") val forwardHost: String = "",
    @SerialName("forward_port") val forwardPort: Int = 80,
    @SerialName("forward_scheme") val forwardScheme: String = "http",
    @SerialName("certificate_id") val certificateId: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("ssl_forced") val sslForced: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("caching_enabled") val cachingEnabled: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("block_exploits") val blockExploits: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("allow_websocket_upgrade") val allowWebsocketUpgrade: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("http2_support") val http2Support: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("hsts_enabled") val hstsEnabled: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    val enabled: Int = 1,
    val meta: NpmProxyHostMeta? = null,
    @SerialName("access_list_id") val accessListId: Int = 0
) {
    val isEnabled: Boolean get() = enabled == 1
    val hasSSL: Boolean get() = certificateId > 0
    val isOnline: Boolean get() = meta?.nginxOnline == true
    val forwardTarget: String get() = "$forwardScheme://$forwardHost:$forwardPort"
    val primaryDomain: String get() = domainNames.firstOrNull() ?: ""
}

@Serializable
data class NpmProxyHostMeta(
    @SerialName("letsencrypt_agree") val letsencryptAgree: Boolean = false,
    @SerialName("dns_challenge") val dnsChallenge: Boolean = false,
    @SerialName("nginx_online") val nginxOnline: Boolean = false,
    @SerialName("nginx_err") val nginxErr: String? = null
)

@Serializable
data class NpmHealthResponse(
    val status: String = "",
    @Serializable(with = NpmVersionSerializer::class)
    val version: NpmVersion? = null
)

@Serializable
data class NpmVersion(
    val major: Int = 0,
    val minor: Int = 0,
    val revision: Int = 0
) {
    val display: String get() = "$major.$minor.$revision"

    companion object {
        fun fromString(s: String): NpmVersion {
            val parts = s.split(".")
            return NpmVersion(
                major = parts.getOrNull(0)?.toIntOrNull() ?: 0,
                minor = parts.getOrNull(1)?.toIntOrNull() ?: 0,
                revision = parts.getOrNull(2)?.toIntOrNull() ?: 0
            )
        }
    }
}

/** Handles NPM (object) and NPMplus (string) version formats. */
object NpmVersionSerializer : KSerializer<NpmVersion?> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("NpmVersion", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): NpmVersion? {
        if (decoder is JsonDecoder) {
            return when (val element = decoder.decodeJsonElement()) {
                is JsonPrimitive -> {
                    val str = element.content
                    if (str.isBlank()) null else NpmVersion.fromString(str)
                }
                is JsonObject -> {
                    val major = (element["major"] as? JsonPrimitive)?.intOrNull ?: 0
                    val minor = (element["minor"] as? JsonPrimitive)?.intOrNull ?: 0
                    val revision = (element["revision"] as? JsonPrimitive)?.intOrNull ?: 0
                    NpmVersion(major, minor, revision)
                }
                else -> null
            }
        }
        return null
    }

    override fun serialize(encoder: Encoder, value: NpmVersion?) {
        if (value != null && encoder is JsonEncoder) {
            encoder.encodeJsonElement(
                JsonObject(mapOf(
                    "major" to JsonPrimitive(value.major),
                    "minor" to JsonPrimitive(value.minor),
                    "revision" to JsonPrimitive(value.revision)
                ))
            )
        }
    }
}

@Serializable
data class NpmRedirectionHost(
    val id: Int,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("domain_names") val domainNames: List<String> = emptyList(),
    @SerialName("forward_http_code") val forwardHttpCode: Int = 301,
    @SerialName("forward_scheme") val forwardScheme: String = "http",
    @SerialName("forward_domain_name") val forwardDomainName: String = "",
    @SerialName("preserve_path") val preservePath: Int = 0,
    @SerialName("certificate_id") val certificateId: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("ssl_forced") val sslForced: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("hsts_enabled") val hstsEnabled: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("hsts_subdomains") val hstsSubdomains: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("http2_support") val http2Support: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("block_exploits") val blockExploits: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    val enabled: Int = 1,
    val meta: NpmProxyHostMeta? = null,
    @SerialName("access_list_id") val accessListId: Int = 0
) {
    val isEnabled: Boolean get() = enabled == 1
    val hasSSL: Boolean get() = certificateId > 0
    val primaryDomain: String get() = domainNames.firstOrNull() ?: ""
}

@Serializable
data class NpmStream(
    val id: Int,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("incoming_port") val incomingPort: Int = 0,
    @SerialName("forwarding_host") val forwardingHost: String = "",
    @SerialName("forwarding_port") val forwardingPort: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("tcp_forwarding") val tcpForwarding: Int = 1,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("udp_forwarding") val udpForwarding: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    val enabled: Int = 1,
    val meta: NpmProxyHostMeta? = null
) {
    val isEnabled: Boolean get() = enabled == 1
    val isOnline: Boolean get() = meta?.nginxOnline == true
}

@Serializable
data class NpmDeadHost(
    val id: Int,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("domain_names") val domainNames: List<String> = emptyList(),
    @SerialName("certificate_id") val certificateId: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("ssl_forced") val sslForced: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("hsts_enabled") val hstsEnabled: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("hsts_subdomains") val hstsSubdomains: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("http2_support") val http2Support: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    val enabled: Int = 1,
    val meta: NpmProxyHostMeta? = null
) {
    val isEnabled: Boolean get() = enabled == 1
    val hasSSL: Boolean get() = certificateId > 0
    val primaryDomain: String get() = domainNames.firstOrNull() ?: ""
}

@Serializable
data class NpmCertificate(
    val id: Int,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    val provider: String = "letsencrypt",
    @SerialName("nice_name") val niceName: String = "",
    @SerialName("domain_names") val domainNames: List<String> = emptyList(),
    @SerialName("expires_on") val expiresOn: String? = null,
    val meta: JsonObject? = null
) {
    val isExpired: Boolean get() {
        val exp = expiresOn ?: return false
        return try {
            Instant.parse(exp).isBefore(Instant.now())
        } catch (_: Exception) {
            false
        }
    }
    val isLetsEncrypt: Boolean get() = provider == "letsencrypt"
    val primaryDomain: String get() = domainNames.firstOrNull() ?: ""
}

@Serializable
data class NpmAccessList(
    val id: Int,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    val name: String = "",
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("pass_auth") val passAuth: Int = 0,
    @Serializable(with = IntBooleanAsIntSerializer::class)
    @SerialName("satisfy_any") val satisfyAny: Int = 0,
    val items: List<NpmAccessListItem>? = null,
    val clients: List<NpmAccessListClient>? = null
)

@Serializable
data class NpmAccessListItem(
    val username: String = "",
    val password: String = ""
)

@Serializable
data class NpmAccessListClient(
    val address: String = "",
    val directive: String = "allow"
)

@Serializable
data class NpmAccessListRequest(
    val name: String,
    val items: List<NpmAccessListItem>,
    val clients: List<NpmAccessListClient>
)

@Serializable
data class NpmUser(
    val id: Int,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("is_disabled") val isDisabled: Boolean? = null,
    val email: String? = null,
    val name: String? = null,
    val nickname: String? = null,
    val avatar: String? = null,
    val roles: List<String>? = null
)

@Serializable
data class NpmUserRequest(
    val email: String,
    val name: String? = null,
    val nickname: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val password: String = "",
    val roles: List<String> = listOf("user"),
    @SerialName("is_disabled") val isDisabled: Boolean = false
)

@Serializable
data class NpmAuditLog(
    val id: Int,
    @SerialName("created_on") val createdOn: String? = null,
    @SerialName("modified_on") val modifiedOn: String? = null,
    @SerialName("user_id") val userId: Int? = null,
    @SerialName("object_type") val objectType: String? = null,
    @SerialName("object_id") val objectId: Int? = null,
    val action: String? = null,
    val meta: JsonElement? = null,
    val user: NpmUser? = null
)

@Serializable
data class NpmSetting(
    val id: String,
    val name: String = "",
    val description: String? = null,
    val value: JsonElement? = null,
    val meta: JsonObject? = null
)

@Serializable
data class NpmProxyHostRequest(
    @SerialName("domain_names") val domainNames: List<String>,
    @SerialName("forward_scheme") val forwardScheme: String = "http",
    @SerialName("forward_host") val forwardHost: String,
    @SerialName("forward_port") val forwardPort: Int,
    @SerialName("certificate_id") val certificateId: Int = 0,
    @SerialName("access_list_id") val accessListId: Int = 0,
    @SerialName("ssl_forced") val sslForced: Int = 0,
    @SerialName("caching_enabled") val cachingEnabled: Int = 0,
    @SerialName("block_exploits") val blockExploits: Int = 0,
    @SerialName("allow_websocket_upgrade") val allowWebsocketUpgrade: Int = 0,
    @SerialName("http2_support") val http2Support: Int = 0,
    @SerialName("hsts_enabled") val hstsEnabled: Int = 0,
    @SerialName("hsts_subdomains") val hstsSubdomains: Int = 0,
    val enabled: Int = 1,
    @SerialName("advanced_config") val advancedConfig: String = "",
    val locations: List<JsonObject> = emptyList(),
    val meta: NpmProxyHostMeta = NpmProxyHostMeta()
)

@Serializable
data class NpmRedirectionHostRequest(
    @SerialName("domain_names") val domainNames: List<String>,
    @SerialName("forward_http_code") val forwardHttpCode: Int = 301,
    @SerialName("forward_scheme") val forwardScheme: String = "http",
    @SerialName("forward_domain_name") val forwardDomainName: String,
    @SerialName("preserve_path") val preservePath: Int = 0,
    @SerialName("certificate_id") val certificateId: Int = 0,
    @SerialName("access_list_id") val accessListId: Int = 0,
    @SerialName("ssl_forced") val sslForced: Int = 0,
    @SerialName("hsts_enabled") val hstsEnabled: Int = 0,
    @SerialName("hsts_subdomains") val hstsSubdomains: Int = 0,
    @SerialName("http2_support") val http2Support: Int = 0,
    @SerialName("block_exploits") val blockExploits: Int = 0,
    val enabled: Int = 1,
    @SerialName("advanced_config") val advancedConfig: String = "",
    val meta: NpmProxyHostMeta = NpmProxyHostMeta()
)

@Serializable
data class NpmStreamRequest(
    @SerialName("incoming_port") val incomingPort: Int,
    @SerialName("forwarding_host") val forwardingHost: String,
    @SerialName("forwarding_port") val forwardingPort: Int,
    @SerialName("tcp_forwarding") val tcpForwarding: Int = 1,
    @SerialName("udp_forwarding") val udpForwarding: Int = 0,
    val enabled: Int = 1,
    val meta: JsonObject? = null
)

@Serializable
data class NpmDeadHostRequest(
    @SerialName("domain_names") val domainNames: List<String>,
    @SerialName("certificate_id") val certificateId: Int = 0,
    @SerialName("ssl_forced") val sslForced: Int = 0,
    @SerialName("hsts_enabled") val hstsEnabled: Int = 0,
    @SerialName("hsts_subdomains") val hstsSubdomains: Int = 0,
    @SerialName("http2_support") val http2Support: Int = 0,
    val enabled: Int = 1,
    @SerialName("advanced_config") val advancedConfig: String = "",
    val meta: NpmProxyHostMeta = NpmProxyHostMeta()
)

@Serializable
data class NpmCertificateRequest(
    val provider: String = "letsencrypt",
    @SerialName("nice_name") val niceName: String,
    @SerialName("domain_names") val domainNames: List<String>,
    val meta: NpmCertificateRequestMeta = NpmCertificateRequestMeta()
)

@Serializable
data class NpmCertificateRequestMeta(
    @SerialName("letsencrypt_agree") val letsencryptAgree: Boolean = true,
    @SerialName("letsencrypt_email") val letsencryptEmail: String = "",
    @SerialName("dns_challenge") val dnsChallenge: Boolean = false
)

object IntBooleanAsIntSerializer : KSerializer<Int> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("IntBooleanAsIntSerializer", PrimitiveKind.INT)

    override fun deserialize(decoder: Decoder): Int {
        return if (decoder is JsonDecoder) {
            val element = decoder.decodeJsonElement()
            if (element is JsonPrimitive) {
                element.intOrNull
                    ?: element.booleanOrNull?.let { if (it) 1 else 0 }
                    ?: 0
            } else {
                0
            }
        } else {
            decoder.decodeInt()
        }
    }

    override fun serialize(encoder: Encoder, value: Int) {
        if (encoder is JsonEncoder) {
            encoder.encodeJsonElement(JsonPrimitive(value))
        } else {
            encoder.encodeInt(value)
        }
    }
}
