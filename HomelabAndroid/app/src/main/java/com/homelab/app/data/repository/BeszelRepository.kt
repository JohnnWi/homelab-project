package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.BeszelApi
import com.homelab.app.data.remote.dto.beszel.BeszelSystem
import com.homelab.app.data.remote.dto.beszel.BeszelSystemRecord
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
        return response.items
    }

    suspend fun getSystem(id: String): BeszelSystem {
        return api.getSystem(id = id)
    }

    suspend fun getSystemRecords(systemId: String, limit: Int = 60): List<BeszelSystemRecord> {
        // Formattazione manuale del filter di Pocketbase
        val filter = "system='$systemId'"
        val response = api.getSystemRecords(filter = filter, limit = limit)
        return response.items
    }
}
