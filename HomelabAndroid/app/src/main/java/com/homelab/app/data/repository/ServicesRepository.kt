package com.homelab.app.data.repository

import com.homelab.app.data.local.SettingsManager
import com.homelab.app.domain.model.ServiceConnection
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicesRepository @Inject constructor(
    private val settingsManager: SettingsManager,
    private val okHttpClient: OkHttpClient
) {
    // Current reachability status (true = up, false = down, null = checking/unknown)
    private val _reachability = MutableStateFlow<Map<ServiceType, Boolean?>>(emptyMap())
    private val _pinging = MutableStateFlow<Map<ServiceType, Boolean>>(emptyMap())
    private val _isTailscaleConnected = MutableStateFlow(false)
    
    val reachability: Flow<Map<ServiceType, Boolean?>> = _reachability
    val pinging: Flow<Map<ServiceType, Boolean>> = _pinging
    val isTailscaleConnected: Flow<Boolean> = _isTailscaleConnected

    val allConnections: Flow<Map<ServiceType, ServiceConnection?>> = settingsManager.allConnections

    fun getConnection(type: ServiceType): Flow<ServiceConnection?> = settingsManager.getConnection(type)

    fun getConnectedServicesCount(): Flow<Int> {
        val allFlows = ServiceType.entries
            .filter { it != ServiceType.UNKNOWN }
            .map { type -> getConnection(type).map { it != null } }
        
        return combine(allFlows) { connectedArray ->
            connectedArray.count { it }
        }
    }

    suspend fun disconnectService(type: ServiceType) {
        settingsManager.deleteConnection(type)
        updateReachabilityMap(type, null)
        updatePingingMap(type, false)
    }

    suspend fun checkReachability(type: ServiceType) {
        if (_pinging.value[type] == true) return
        
        val connection = settingsManager.getConnection(type).firstOrNull() ?: return
        
        updatePingingMap(type, true)
        
        android.util.Log.d("ServicesRepository", "Checking reachability for $type")
        // Use a real GET request on IO dispatcher to avoid blocking main thread
        // Some local servers (like Pi-hole) might handle HEAD differently or fail.
        val reachable = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            val baseUrl = connection.url.trimEnd('/')
            val pathsToTry = if (type == ServiceType.PIHOLE) {
                // For Pi-hole, try v6 info path, v5 admin path, and base URL
                listOf("/api/info/version", "/admin/index.php", "", "/admin/api.php")
            } else {
                listOf("")
            }

            var isAnyPathReachable = false
            for (path in pathsToTry) {
                try {
                    val request = Request.Builder()
                        .url(baseUrl + path)
                        .build()
                    
                    okHttpClient.newCall(request).execute().use { response ->
                        android.util.Log.d("ServicesRepository", "Reachability check for $type at $baseUrl$path: ${response.code}")
                        // ANY HTTP response means the server is reachable
                        isAnyPathReachable = true
                        break
                    }
                } catch (e: Exception) {
                    android.util.Log.w("ServicesRepository", "Path $baseUrl$path failed for $type: ${e.message}")
                }
                if (isAnyPathReachable) break
            }
            isAnyPathReachable
        }
        
        updatePingingMap(type, false)
        updateReachabilityMap(type, reachable)
    }

    fun checkTailscale() {
        val connected = try {
            val interfaces = java.net.NetworkInterface.getNetworkInterfaces()
            var found = false
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                val addresses = networkInterface.inetAddresses
                while (addresses.hasMoreElements()) {
                    val address = addresses.nextElement()
                    if (!address.isLoopbackAddress && address.hostAddress.startsWith("100.")) {
                        found = true
                        break
                    }
                }
                if (found) break
            }
            found
        } catch (e: Exception) {
            false
        }
        _isTailscaleConnected.value = connected
    }

    private fun updateReachabilityMap(type: ServiceType, value: Boolean?) {
        _reachability.value = _reachability.value.toMutableMap().apply { put(type, value) }
    }

    private fun updatePingingMap(type: ServiceType, value: Boolean) {
        _pinging.value = _pinging.value.toMutableMap().apply { put(type, value) }
    }
}
