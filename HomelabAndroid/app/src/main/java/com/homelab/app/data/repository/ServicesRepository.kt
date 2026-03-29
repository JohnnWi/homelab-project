package com.homelab.app.data.repository

import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.GlobalEventBus
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicesRepository @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val okHttpClient: OkHttpClient,
    private val globalEventBus: GlobalEventBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false
    private val lastReachabilityCheckMs = mutableMapOf<String, Long>()
    private val minReachabilityIntervalMs = 20_000L

    private val _reachability = MutableStateFlow<Map<String, Boolean?>>(emptyMap())
    private val _pinging = MutableStateFlow<Map<String, Boolean>>(emptyMap())
    private val _isTailscaleConnected = MutableStateFlow(false)

    val reachability: Flow<Map<String, Boolean?>> = _reachability
    val pinging: Flow<Map<String, Boolean>> = _pinging
    val isTailscaleConnected: Flow<Boolean> = _isTailscaleConnected

    val allInstances: Flow<List<ServiceInstance>> = serviceInstancesRepository.allInstances
    val instancesByType = serviceInstancesRepository.instancesByType
    val preferredInstanceIdByType = serviceInstancesRepository.preferredInstanceIdByType
    val preferredInstancesByType = serviceInstancesRepository.preferredInstancesByType

    suspend fun initialize() {
        serviceInstancesRepository.initialize()
        if (initialized) return
        initialized = true

        scope.launch {
            globalEventBus.authErrors.collect { instanceId ->
                markInstanceUnauthorized(instanceId)
            }
        }
    }

    suspend fun getInstance(id: String): ServiceInstance? = serviceInstancesRepository.getInstance(id)

    suspend fun saveInstance(instance: ServiceInstance) {
        serviceInstancesRepository.saveInstance(instance)
    }

    suspend fun disconnectInstance(instanceId: String) {
        serviceInstancesRepository.deleteInstance(instanceId)
        updateReachabilityMap(instanceId, null, remove = true)
        updatePingingMap(instanceId, false, remove = true)
        lastReachabilityCheckMs.remove(instanceId)
    }

    suspend fun markInstanceUnauthorized(instanceId: String) {
        if (serviceInstancesRepository.getInstance(instanceId) == null) return
        updateReachabilityMap(instanceId, false)
        updatePingingMap(instanceId, false)
        lastReachabilityCheckMs.remove(instanceId)
    }

    suspend fun setPreferredInstance(type: ServiceType, instanceId: String?) {
        serviceInstancesRepository.setPreferredInstance(type, instanceId)
    }

    suspend fun checkReachability(instanceId: String, force: Boolean = false) {
        if (_pinging.value[instanceId] == true) return
        val now = System.currentTimeMillis()
        if (!force) {
            val last = lastReachabilityCheckMs[instanceId]
            if (last != null && (now - last) < minReachabilityIntervalMs) return
        }

        val instance = serviceInstancesRepository.getInstance(instanceId) ?: return

        val previousReachability = _reachability.value[instanceId]
        updatePingingMap(instanceId, true)
        if (previousReachability == null) {
            updateReachabilityMap(instanceId, null)
        }

        val reachable = withContext(Dispatchers.IO) {
            val baseUrl = instance.url.trimEnd('/').takeIf { it.isNotBlank() } ?: return@withContext false
            val pathsToTry = when (instance.type) {
                ServiceType.PIHOLE -> listOf("/api/info/version", "/admin/index.php", "", "/admin/api.php")
                ServiceType.ADGUARD_HOME -> listOf("/control/status", "/control/", "")
                ServiceType.RADARR, ServiceType.SONARR -> listOf("/api/v3/system/status", "/api/v3/health", "")
                ServiceType.LIDARR -> listOf("/api/v1/system/status", "/api/v1/health", "")
                ServiceType.QBITTORRENT -> listOf("/api/v2/app/version", "/api/v2/app/buildInfo", "")
                ServiceType.JELLYSEERR -> listOf("/api/v1/status", "/api/v1/settings/public", "")
                ServiceType.PROWLARR -> listOf("/api/v1/system/status", "/api/v1/health", "")
                ServiceType.BAZARR -> listOf("/api/system/status", "/api/badges", "")
                ServiceType.GLUETUN -> listOf("/v1/openvpn/status", "/v1/publicip/ip", "")
                ServiceType.FLARESOLVERR -> listOf("/health", "/v1", "")
                ServiceType.LINUX_UPDATE -> listOf("/api/dashboard/stats", "")
                ServiceType.TECHNITIUM -> listOf("/api/user/login", "/api/dashboard/stats/get", "")
                ServiceType.DOCKHAND -> listOf("/api/dashboard/stats", "/api/containers", "")
                ServiceType.PANGOLIN -> listOf("/v1/orgs", "/v1/openapi.json", "/v1/")
                else -> listOf("")
            }

            pathsToTry.any { path ->
                runCatching {
                    okHttpClient.newCall(
                        Request.Builder()
                            .url(baseUrl + path)
                            .addHeader("X-Homelab-Instance-Id", instance.id)
                            .build()
                    ).execute().use { true }
                }.getOrDefault(false)
            }
        }

        updatePingingMap(instanceId, false)
        updateReachabilityMap(instanceId, reachable)
        lastReachabilityCheckMs[instanceId] = System.currentTimeMillis()
    }

    suspend fun checkAllReachability(force: Boolean = false) {
        val instances = allInstances.firstOrNull().orEmpty()
        instances.forEach { checkReachability(it.id, force = force) }
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
                    val hostAddress = address.hostAddress ?: continue
                    if (!address.isLoopbackAddress && hostAddress.startsWith("100.")) {
                        if (networkInterface.name.startsWith("tun")) {
                            found = true
                            break
                        }
                    }
                }
                if (found) break
            }
            found
        } catch (_: Exception) {
            false
        }
        _isTailscaleConnected.value = connected
    }

    private fun updateReachabilityMap(instanceId: String, value: Boolean?, remove: Boolean = false) {
        _reachability.value = _reachability.value.toMutableMap().apply {
            if (remove) remove(instanceId) else put(instanceId, value)
        }
    }

    private fun updatePingingMap(instanceId: String, value: Boolean, remove: Boolean = false) {
        _pinging.value = _pinging.value.toMutableMap().apply {
            if (remove) remove(instanceId) else put(instanceId, value)
        }
    }
}
