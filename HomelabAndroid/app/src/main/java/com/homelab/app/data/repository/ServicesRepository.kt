package com.homelab.app.data.repository

import com.homelab.app.data.remote.TlsClientSelector
import com.homelab.app.domain.model.ServiceInstance
import com.homelab.app.util.GlobalEventBus
import com.homelab.app.util.ServiceType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ServicesRepository @Inject constructor(
    private val serviceInstancesRepository: ServiceInstancesRepository,
    private val tlsClientSelector: TlsClientSelector,
    private val globalEventBus: GlobalEventBus
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var initialized = false
    private val lastReachabilityCheckMs = ConcurrentHashMap<String, Long>()
    private val minReachabilityIntervalMs = 20_000L
    private val lastBulkReachabilityCheckMs = AtomicLong(0L)
    private val minBulkReachabilityIntervalMs = 45_000L
    private val bulkReachabilityCheckInFlight = AtomicBoolean(false)
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

    suspend fun markInstanceReachable(instanceId: String) {
        if (serviceInstancesRepository.getInstance(instanceId) == null) return
        updatePingingMap(instanceId, false)
        updateReachabilityMap(instanceId, true)
        lastReachabilityCheckMs[instanceId] = System.currentTimeMillis()
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

        try {
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
                    ServiceType.CRAFTY_CONTROLLER -> listOf("/api/v2/servers", "/api/v2", "")
                    ServiceType.PANGOLIN -> listOf("/v1/orgs", "/v1/openapi.json", "/v1/")
                    ServiceType.WAKAPI -> listOf("/api/health", "/api/summary", "")
                    ServiceType.PROXMOX -> listOf("/api2/json/version", "")
                    else -> listOf("")
                }

                pathsToTry.any { path ->
                    runCatching {
                        val reachabilityClient = tlsClientSelector.forAllowSelfSigned(instance.allowSelfSigned)
                            .newBuilder()
                            .connectTimeout(4, TimeUnit.SECONDS)
                            .readTimeout(4, TimeUnit.SECONDS)
                            .writeTimeout(4, TimeUnit.SECONDS)
                            .callTimeout(6, TimeUnit.SECONDS)
                            .build()
                        reachabilityClient.newCall(
                            Request.Builder()
                                .url(baseUrl + path)
                                .addHeader("X-Homelab-Instance-Id", instance.id)
                                .build()
                        ).execute().use { true }
                    }.getOrDefault(false)
                }
            }

            updateReachabilityMap(instanceId, reachable)
            lastReachabilityCheckMs[instanceId] = System.currentTimeMillis()
        } finally {
            updatePingingMap(instanceId, false)
        }
    }

    suspend fun checkAllReachability(force: Boolean = false) {
        if (!bulkReachabilityCheckInFlight.compareAndSet(false, true)) return

        try {
            val now = System.currentTimeMillis()
            if (!force) {
                val last = lastBulkReachabilityCheckMs.get()
                if ((now - last) < minBulkReachabilityIntervalMs) return
            }

            lastBulkReachabilityCheckMs.set(now)
            val instances = allInstances.firstOrNull().orEmpty()
            coroutineScope {
                instances.map { instance ->
                    async { checkReachability(instance.id, force = force) }
                }.awaitAll()
            }
        } finally {
            bulkReachabilityCheckInFlight.set(false)
        }
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
        _reachability.update { current ->
            current.toMutableMap().apply {
                if (remove) remove(instanceId) else put(instanceId, value)
            }
        }
    }

    private fun updatePingingMap(instanceId: String, value: Boolean, remove: Boolean = false) {
        _pinging.update { current ->
            current.toMutableMap().apply {
                if (remove) remove(instanceId) else put(instanceId, value)
            }
        }
    }
}
