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

    suspend fun getSystems(): List<BeszelSystem> {
        val response = api.getSystems()
        // Keep a stable, predictable ordering on the dashboard.
        // Sort by name (case-insensitive) so items don't jump around
        // when their "updated" timestamp changes.
        return response.items.sortedBy { it.name.lowercase() }
    }

    suspend fun getSystem(id: String): BeszelSystem {
        return api.getSystem(id = id)
    }

    suspend fun getSystemDetails(systemId: String): BeszelSystemDetails? {
        val filter = "system='$systemId'"
        val response = api.getSystemDetails(filter = filter, limit = 1)
        return response.items.firstOrNull()
    }

    suspend fun getSystemRecords(systemId: String, limit: Int = 30): List<BeszelSystemRecord> {
        // Formattazione manuale del filter di Pocketbase
        val filter = "system='$systemId'"
        val response = api.getSystemRecords(filter = filter, limit = limit)
        return response.items
    }

    suspend fun getSmartDevices(systemId: String, limit: Int = 10): List<BeszelSmartDevice> {
        val filter = "system='$systemId'"
        val response = api.getSmartDevices(filter = filter, limit = limit)
        return response.items
    }
}
