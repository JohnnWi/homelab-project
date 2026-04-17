package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.NginxProxyManagerApi
import com.homelab.app.data.remote.dto.nginxpm.NpmAccessList
import com.homelab.app.data.remote.dto.nginxpm.NpmAccessListRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmAuditLog
import com.homelab.app.data.remote.dto.nginxpm.NpmCertificate
import com.homelab.app.data.remote.dto.nginxpm.NpmCertificateRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmDeadHost
import com.homelab.app.data.remote.dto.nginxpm.NpmDeadHostRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmHostReport
import com.homelab.app.data.remote.dto.nginxpm.NpmProxyHost
import com.homelab.app.data.remote.dto.nginxpm.NpmProxyHostRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmRedirectionHost
import com.homelab.app.data.remote.dto.nginxpm.NpmRedirectionHostRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmSetting
import com.homelab.app.data.remote.dto.nginxpm.NpmStream
import com.homelab.app.data.remote.dto.nginxpm.NpmStreamRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmUser
import com.homelab.app.data.remote.dto.nginxpm.NpmUserRequest
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class NginxProxyManagerRepository @Inject constructor(
    private val api: NginxProxyManagerApi
) {
    suspend fun authenticate(url: String, email: String, password: String, allowSelfSigned: Boolean = false): String {
        val cleanUrl = url.trimEnd('/') + "/api/tokens"
        try {
            val response = api.authenticate(
                url = cleanUrl,
                allowSelfSigned = allowSelfSigned.toString(),
                credentials = mapOf("identity" to email, "secret" to password)
            )
            
            if (!response.isSuccessful) {
                if (response.code() == 401 || response.code() == 403) {
                    throw Exception("Authentication failed. Check your email and password.")
                }
                throw Exception("HTTP Error: ${response.code()}")
            }
            
            val body = response.body() ?: throw Exception("Empty response body")
            var token = body.resolvedToken
            
            // Per NPMplus: Se il token nel JSON è vuoto, cerca nei cookie.
            if (token.isBlank()) {
                val cookies = response.headers().values("Set-Cookie")
                for (cookie in cookies) {
                    if (cookie.contains("token=")) {
                        // Estrae il valore tra "token=" e il prossimo ";"
                        token = cookie.substringAfter("token=").substringBefore(";")
                        break
                    }
                }
            }

            if (token.isBlank()) {
                throw Exception("Authentication failed. Empty token received.")
            }
            return token
        } catch (e: Exception) {
            if (e is retrofit2.HttpException && (e.code() == 401 || e.code() == 403)) {
                throw Exception("Authentication failed. Check your email and password.")
            }
            throw e
        }
    }

    suspend fun getHostReport(instanceId: String): NpmHostReport {
        return api.getHostReport(instanceId = instanceId)
    }

    suspend fun getProxyHosts(instanceId: String): List<NpmProxyHost> {
        return api.getProxyHosts(instanceId = instanceId)
    }

    suspend fun createProxyHost(instanceId: String, request: NpmProxyHostRequest): NpmProxyHost {
        return api.createProxyHost(instanceId = instanceId, request = request)
    }

    suspend fun updateProxyHost(instanceId: String, hostId: Int, request: NpmProxyHostRequest): NpmProxyHost {
        return api.updateProxyHost(instanceId = instanceId, hostId = hostId, request = request)
    }

    suspend fun deleteProxyHost(instanceId: String, hostId: Int) {
        api.deleteProxyHost(instanceId = instanceId, hostId = hostId)
    }

    suspend fun enableProxyHost(instanceId: String, hostId: Int) {
        api.enableProxyHost(instanceId = instanceId, hostId = hostId)
    }

    suspend fun disableProxyHost(instanceId: String, hostId: Int) {
        api.disableProxyHost(instanceId = instanceId, hostId = hostId)
    }

    suspend fun getRedirectionHosts(instanceId: String): List<NpmRedirectionHost> {
        return api.getRedirectionHosts(instanceId = instanceId)
    }

    suspend fun createRedirectionHost(instanceId: String, request: NpmRedirectionHostRequest): NpmRedirectionHost {
        return api.createRedirectionHost(instanceId = instanceId, request = request)
    }

    suspend fun updateRedirectionHost(instanceId: String, hostId: Int, request: NpmRedirectionHostRequest): NpmRedirectionHost {
        return api.updateRedirectionHost(instanceId = instanceId, hostId = hostId, request = request)
    }

    suspend fun deleteRedirectionHost(instanceId: String, hostId: Int) {
        api.deleteRedirectionHost(instanceId = instanceId, hostId = hostId)
    }

    suspend fun getStreams(instanceId: String): List<NpmStream> {
        return api.getStreams(instanceId = instanceId)
    }

    suspend fun createStream(instanceId: String, request: NpmStreamRequest): NpmStream {
        return api.createStream(instanceId = instanceId, request = request)
    }

    suspend fun updateStream(instanceId: String, streamId: Int, request: NpmStreamRequest): NpmStream {
        return api.updateStream(instanceId = instanceId, streamId = streamId, request = request)
    }

    suspend fun deleteStream(instanceId: String, streamId: Int) {
        api.deleteStream(instanceId = instanceId, streamId = streamId)
    }

    suspend fun getDeadHosts(instanceId: String): List<NpmDeadHost> {
        return api.getDeadHosts(instanceId = instanceId)
    }

    suspend fun createDeadHost(instanceId: String, request: NpmDeadHostRequest): NpmDeadHost {
        return api.createDeadHost(instanceId = instanceId, request = request)
    }

    suspend fun updateDeadHost(instanceId: String, hostId: Int, request: NpmDeadHostRequest): NpmDeadHost {
        return api.updateDeadHost(instanceId = instanceId, hostId = hostId, request = request)
    }

    suspend fun deleteDeadHost(instanceId: String, hostId: Int) {
        api.deleteDeadHost(instanceId = instanceId, hostId = hostId)
    }

    suspend fun getCertificates(instanceId: String): List<NpmCertificate> {
        return api.getCertificates(instanceId = instanceId)
    }

    suspend fun createCertificate(instanceId: String, request: NpmCertificateRequest): NpmCertificate {
        return api.createCertificate(instanceId = instanceId, request = request)
    }

    suspend fun deleteCertificate(instanceId: String, certId: Int) {
        api.deleteCertificate(instanceId = instanceId, certId = certId)
    }

    suspend fun renewCertificate(instanceId: String, certId: Int): NpmCertificate {
        return api.renewCertificate(instanceId = instanceId, certId = certId)
    }

    suspend fun getAccessLists(instanceId: String): List<NpmAccessList> {
        return api.getAccessLists(instanceId = instanceId)
    }

    suspend fun createAccessList(instanceId: String, request: NpmAccessListRequest): NpmAccessList {
        return api.createAccessList(instanceId = instanceId, request = request)
    }

    suspend fun updateAccessList(instanceId: String, id: Int, request: NpmAccessListRequest): NpmAccessList {
        return api.updateAccessList(instanceId = instanceId, listId = id, request = request)
    }

    suspend fun deleteAccessList(instanceId: String, id: Int) {
        api.deleteAccessList(instanceId = instanceId, listId = id)
    }

    suspend fun getUsers(instanceId: String): List<NpmUser> {
        return api.getUsers(instanceId = instanceId)
    }

    suspend fun createUser(instanceId: String, request: NpmUserRequest): NpmUser {
        return api.createUser(instanceId = instanceId, request = request)
    }

    suspend fun updateUser(instanceId: String, userId: Int, request: NpmUserRequest): NpmUser {
        return api.updateUser(instanceId = instanceId, userId = userId, request = request)
    }

    suspend fun deleteUser(instanceId: String, userId: Int) {
        api.deleteUser(instanceId = instanceId, userId = userId)
    }

    suspend fun getAuditLogs(instanceId: String): List<NpmAuditLog> {
        return api.getAuditLogs(instanceId = instanceId)
    }

    suspend fun getSettings(instanceId: String): List<NpmSetting> {
        return api.getSettings(instanceId = instanceId)
    }
}
