package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.BeszelApi
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemDetails
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
import com.homelab.app.data.remote.dto.beszel.BeszelSmartDevice
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BeszelRepository @Inject constructor(
    private val api: BeszelApi
) {
    suspend fun authenticate(url: String, email: String, password: String): String {
        val cleanUrl = url.trimEnd('/') + "/api/collections/users/auth-with-password"
        try {
            val response = api.authenticate(
                url = cleanUrl,
                credentials = mapOf("identity" to email, "password" to password)
            )
            return response.token
        } catch (e: Exception) {
            if (e is retrofit2.HttpException && e.code() == 400) {
                // Pocketbase often throws 400 for bad auth
                throw Exception("Authentication failed. Check your credentials and URL.")
            }
            throw e
        }
    }

    suspend fun getSystems(instanceId: String): List<BeszelSystem> {
        val response = api.getSystems(instanceId = instanceId)
        return response.items.sortedBy { it.name.lowercase() }
    }

    suspend fun getSystem(instanceId: String, id: String): BeszelSystem {
        return api.getSystem(instanceId = instanceId, id = id)
    }

    suspend fun getSystemDetails(instanceId: String, systemId: String): BeszelSystemDetails? {
        val filter = "system='$systemId'"
        val response = api.getSystemDetails(instanceId = instanceId, filter = filter, limit = 1)
        return response.items.firstOrNull()
    }

    suspend fun getSystemRecords(instanceId: String, systemId: String, limit: Int = 60): List<BeszelSystemRecord> {
        val filter = "system='$systemId'"
        val response = api.getSystemRecords(instanceId = instanceId, filter = filter, limit = limit)
        return response.items
    }

    suspend fun getSmartDevices(instanceId: String, systemId: String, limit: Int = 10): List<BeszelSmartDevice> {
        val filter = "system='$systemId'"
        val response = api.getSmartDevices(instanceId = instanceId, filter = filter, limit = limit)
        return response.items
    }
}
