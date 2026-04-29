package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.UnifiApi
import com.homelab.app.domain.model.ServiceInstance
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

enum class UnifiAuthMode {
    SITE_MANAGER,
    LOCAL_NETWORK
}

data class UnifiSite(
    val id: String,
    val name: String,
    val hostId: String?,
    val timezone: String?,
    val gatewayMac: String?,
    val deviceCount: Int,
    val clientCount: Int,
    val health: String?
)

data class UnifiHost(
    val id: String,
    val name: String,
    val model: String?,
    val ipAddress: String?,
    val version: String?,
    val status: String?
)

data class UnifiDevice(
    val id: String,
    val siteId: String?,
    val name: String,
    val model: String?,
    val type: String,
    val macAddress: String?,
    val ipAddress: String?,
    val firmware: String?,
    val serial: String?,
    val online: Boolean,
    val upgradeable: Boolean,
    val rxBps: Double?,
    val txBps: Double?,
    val cpuPercent: Double?,
    val memoryPercent: Double?,
    val temperatureCelsius: Double?,
    val connectedClients: Int?,
    val ports: List<UnifiPort>,
    val radios: List<UnifiRadio>,
    val uplinkName: String?
)

data class UnifiPort(
    val number: Int,
    val name: String,
    val speedMbps: Int?,
    val online: Boolean,
    val poe: Boolean,
    val poePowerWatts: Double?,
    val rxBps: Double?,
    val txBps: Double?,
    val profileName: String?,
    val uplink: Boolean
)

data class UnifiRadio(
    val name: String,
    val channel: Int?,
    val band: String?,
    val width: String?,
    val txPower: String?,
    val utilizationPercent: Double?
)

data class UnifiClient(
    val id: String,
    val siteId: String?,
    val name: String,
    val macAddress: String?,
    val ipAddress: String?,
    val type: String?,
    val networkName: String?,
    val accessPointName: String?,
    val signalDbm: Int?,
    val experiencePercent: Double?,
    val rxBytes: Double?,
    val txBytes: Double?,
    val rxBps: Double?,
    val txBps: Double?,
    val connectedSeconds: Long?,
    val authorized: Boolean?
) {
    val isWireless: Boolean get() = type.equals("WIRELESS", ignoreCase = true)
    val isWired: Boolean get() = type.equals("WIRED", ignoreCase = true)
    val isGuestUnauthorized: Boolean get() = authorized != true && (authorized == false || type.equals("GUEST", ignoreCase = true))
    val liveTrafficBps: Double? get() = listOfNotNull(rxBps, txBps).takeIf { it.isNotEmpty() }?.sum()
}

data class UnifiNetwork(
    val id: String,
    val siteId: String?,
    val name: String,
    val purpose: String?,
    val subnet: String?,
    val vlanId: Int?
)

data class UnifiIspMetric(
    val siteId: String?,
    val hostId: String?,
    val timestamp: String?,
    val latencyMs: Double?,
    val packetLossPercent: Double?,
    val downloadMbps: Double?,
    val uploadMbps: Double?,
    val uptimePercent: Double?
)

data class UnifiDashboardData(
    val authMode: UnifiAuthMode,
    val sites: List<UnifiSite>,
    val hosts: List<UnifiHost>,
    val devices: List<UnifiDevice>,
    val clients: List<UnifiClient>,
    val networks: List<UnifiNetwork>,
    val ispMetrics: List<UnifiIspMetric>
) {
    val onlineDeviceCount: Int get() = devices.count { it.online }
    val offlineDeviceCount: Int get() = devices.count { !it.online }
    val totalClients: Int get() = clients.size.takeIf { it > 0 } ?: sites.sumOf { it.clientCount }

    fun scoped(siteId: String?): UnifiDashboardData {
        if (siteId.isNullOrBlank()) return this
        val selectedSite = sites.firstOrNull { it.id == siteId }
        val hostId = selectedSite?.hostId
        return copy(
            sites = sites.filter { it.id == siteId },
            hosts = if (hostId == null) hosts else hosts.filter { it.id == hostId || it.model == hostId },
            devices = devices.filter { it.siteId == siteId },
            clients = clients.filter { it.siteId == siteId },
            networks = networks.filter { it.siteId == siteId },
            ispMetrics = ispMetrics.filter { metric ->
                metric.siteId == siteId || (metric.siteId == null && hostId != null && metric.hostId == hostId)
            }
        )
    }
}

data class UnifiSummary(
    val onlineDevices: Int,
    val totalDevices: Int,
    val clients: Int,
    val sites: Int
)

@Singleton
class UnifiRepository @Inject constructor(
    private val api: UnifiApi,
    private val serviceInstancesRepository: ServiceInstancesRepository
) {

    suspend fun authenticate(
        url: String,
        apiKey: String,
        fallbackUrl: String? = null,
        allowSelfSigned: Boolean = false
    ) {
        require(apiKey.isNotBlank()) { "API key required." }
        val mode = authModeFor(url)
        val candidates = if (mode == UnifiAuthMode.SITE_MANAGER) {
            listOf(SITE_MANAGER_BASE)
        } else {
            listOf(cleanUrl(url), cleanOptionalUrl(fallbackUrl)).filterNotNull().distinct()
        }

        var lastError: Exception? = null
        for (baseUrl in candidates) {
            try {
                val path = if (mode == UnifiAuthMode.SITE_MANAGER) {
                    "/v1/sites?pageSize=1"
                } else {
                    "/proxy/network/integration/v1/sites"
                }
                val response = api.getJson(
                    url = absoluteUrl(baseUrl, path),
                    bypass = "true",
                    allowSelfSigned = allowSelfSigned.toString(),
                    apiKey = apiKey
                )
                if (response.objectArray().isEmpty()) {
                    // Empty deployments are valid, but a non-JSON or unauthorized response is not.
                    response.unwrapData()
                }
                return
            } catch (error: Exception) {
                lastError = error
            }
        }
        throw lastError ?: IllegalStateException("UniFi validation failed.")
    }

    suspend fun getDashboard(instanceId: String): UnifiDashboardData {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalArgumentException("UniFi instance not found.")
        return if (authModeFor(instance.url) == UnifiAuthMode.SITE_MANAGER) {
            getSiteManagerDashboard(instance)
        } else {
            getLocalDashboard(instance)
        }
    }

    suspend fun getSummary(instanceId: String): UnifiSummary {
        val data = getDashboard(instanceId)
        return UnifiSummary(
            onlineDevices = data.onlineDeviceCount,
            totalDevices = data.devices.size.takeIf { it > 0 } ?: data.sites.sumOf { it.deviceCount },
            clients = data.totalClients,
            sites = data.sites.size
        )
    }

    suspend fun authorizeGuest(
        instanceId: String,
        siteId: String,
        clientId: String,
        minutes: Int = 120
    ) {
        val instance = serviceInstancesRepository.getInstance(instanceId)
            ?: throw IllegalArgumentException("UniFi instance not found.")
        val body = buildJsonObject {
            put("action", "AUTHORIZE_GUEST_ACCESS")
            put("timeLimitMinutes", minutes.coerceAtLeast(1))
        }
        if (authModeFor(instance.url) == UnifiAuthMode.SITE_MANAGER) {
            api.postJson(
                url = "$SITE_MANAGER_BASE/v1/sites/${siteId.encodePath()}/clients/${clientId.encodePath()}/actions",
                body = body,
                bypass = "true",
                apiKey = instance.apiKey
            )
        } else {
            postLocalJson(instance.id, "sites/${siteId.encodePath()}/clients/${clientId.encodePath()}/actions", body)
        }
    }

    fun demoDashboard(): UnifiDashboardData {
        val sites = listOf(
            UnifiSite(
                id = "casa",
                name = "Casa",
                hostId = "host1",
                timezone = "Europe/Rome",
                gatewayMac = "78:45:58:aa:bb:01",
                deviceCount = 8,
                clientCount = 42,
                health = "attention"
            ),
            UnifiSite(
                id = "studio",
                name = "Studio",
                hostId = "host2",
                timezone = "Europe/Rome",
                gatewayMac = "78:45:58:aa:bb:11",
                deviceCount = 5,
                clientCount = 17,
                health = "online"
            )
        )
        val devices = listOf(
            demoDevice("d1", "casa", "UDM-Pro", "UDM-Pro", "UDM", "192.168.1.1", true, false, 5_242_880.0, 1_441_792.0, 21.0, 48.0, 53.0, 42, demoPorts("WAN", 2500, 5_242_880.0, 1_441_792.0)),
            demoDevice("d2", "casa", "USW-24-PoE", "USW-24-PoE", "USW", "192.168.1.2", true, false, 1_572_864.0, 786_432.0, ports = demoPorts("Uplink", 1000, 1_572_864.0, 786_432.0) + demoPoePort(5, "PoE Cam", 6.4)),
            demoDevice("d3", "casa", "UAP Soggiorno", "UAP-AC-Pro", "UAP", "192.168.1.3", true, true, 786_432.0, 655_360.0, temperature = 47.0, clients = 19, radios = demoRadios()),
            demoDevice("d4", "casa", "UAP Studio", "U7-Pro", "UAP", "192.168.1.4", true, true, 917_504.0, 524_288.0, clients = 12, radios = demoRadios()),
            demoDevice("d5", "casa", "USW-Flex Mini", "USW-Flex-Mini", "USW", "192.168.1.6", true, false, 32_768.0, 16_384.0, ports = demoPorts("Uplink", 1000, 32_768.0, 16_384.0)),
            demoDevice("d6", "casa", "UAP Cantina", "UAP-AC-Lite", "UAP", "192.168.1.7", false, false, 0.0, 0.0, clients = 0, radios = demoRadios()),
            demoDevice("d7", "studio", "UXG-Max", "UXG-Max", "GATEWAY", "10.1.0.1", true, false, 1_048_576.0, 524_288.0, 17.0, 39.0, 49.0, ports = demoPorts("WAN", 1000, 1_048_576.0, 524_288.0)),
            demoDevice("d8", "studio", "USW-Lite-16-PoE", "USW-Lite-16-PoE", "USW", "10.1.0.2", true, false, 524_288.0, 262_144.0, ports = demoPorts("Uplink", 1000, 524_288.0, 262_144.0) + demoPoePort(4, "Camera", 7.8)),
            demoDevice("d9", "studio", "U7 Wall Studio", "U7-Wall", "UAP", "10.1.0.3", true, false, 393_216.0, 327_680.0, clients = 6, radios = demoRadios())
        )
        val clients = listOf(
            demoClient("c1", "casa", "MacBook Pro Andrea", "192.168.1.101", "WIRELESS", "LAN", "UAP Soggiorno", -53, 98.0, 3_254_512_640.0, 982_345_678.0, 65_536.0, 131_072.0, true),
            demoClient("c2", "casa", "iPhone Andrea", "192.168.1.102", "WIRELESS", "LAN", "UAP Studio", -61, 95.0, 445_123_456.0, 123_456_789.0, 32_768.0, 49_152.0, true),
            demoClient("c3", "casa", "TV Samsung", "192.168.1.104", "WIRED", "LAN", null, null, null, 8_765_432_100.0, 123_456.0, null, null, true),
            demoClient("c4", "casa", "NAS Synology DS923+", "192.168.1.105", "WIRED", "LAN", null, null, null, 15_234_567_890.0, 9_876_543_210.0, 524_288.0, 458_752.0, true),
            demoClient("c5", "studio", "Meeting Room Display", "10.1.0.21", "WIRELESS", "Office", "U7 Wall Studio", -47, 99.0, 12_345_678.0, 3_456_789.0, 16_384.0, 8_192.0, true),
            demoClient("c6", "studio", "Door Controller", "10.1.0.20", "WIRED", "Office", null, null, null, 987_654_321.0, 456_789_012.0, null, null, true),
            demoClient("c7", "casa", "iPhone Marco", "192.168.100.1", "GUEST", "Guest", "UAP Soggiorno", -67, 88.0, 45_678_901.0, 12_345_678.0, null, null, false)
        )
        val networks = listOf(
            UnifiNetwork("n1", "casa", "LAN", "corporate", "192.168.1.0/24", null),
            UnifiNetwork("n2", "casa", "IoT", "corporate", "10.0.10.0/24", 10),
            UnifiNetwork("n3", "casa", "Ospiti", "guest", "192.168.100.0/24", 100),
            UnifiNetwork("n4", "studio", "Office", "corporate", "10.1.0.0/24", null),
            UnifiNetwork("n5", "studio", "Devices", "corporate", "10.1.20.0/24", 20)
        )
        return UnifiDashboardData(
            authMode = UnifiAuthMode.LOCAL_NETWORK,
            sites = sites,
            hosts = listOf(
                UnifiHost("host1", "UDM-Pro Casa", "UDM-Pro", "192.168.1.1", "3.2.12", "connected"),
                UnifiHost("host2", "UXG Max Studio", "UXG-Max", "10.1.0.1", "4.1.3", "connected")
            ),
            devices = devices,
            clients = clients,
            networks = networks,
            ispMetrics = demoIspMetrics("casa", "host1", 235.0, 89.0) + demoIspMetrics("studio", "host2", 126.0, 54.0)
        )
    }

    private suspend fun getSiteManagerDashboard(instance: ServiceInstance): UnifiDashboardData = coroutineScope {
        val sitesDeferred = async { fetchSiteManagerPaged(instance, "/v1/sites?pageSize=100").mapNotNull(::parseSite) }
        val hostsDeferred = async {
            runCatching { fetchSiteManagerPaged(instance, "/v1/hosts?pageSize=100").mapNotNull(::parseHost) }
                .getOrDefault(emptyList())
        }
        val devicesDeferred = async {
            runCatching { fetchSiteManagerPaged(instance, "/v1/devices?pageSize=100").mapNotNull(::parseDevice) }
                .getOrDefault(emptyList())
        }
        val ispDeferred = async {
            runCatching {
                api.getJson(
                    url = "$SITE_MANAGER_BASE/ea/isp-metrics/5m?duration=24h",
                    bypass = "true",
                    apiKey = instance.apiKey
                ).objectArray().flatMap(::parseIspMetricSeries)
            }.getOrDefault(emptyList())
        }

        UnifiDashboardData(
            authMode = UnifiAuthMode.SITE_MANAGER,
            sites = sitesDeferred.await(),
            hosts = hostsDeferred.await(),
            devices = devicesDeferred.await(),
            clients = emptyList(),
            networks = emptyList(),
            ispMetrics = ispDeferred.await()
        )
    }

    private suspend fun getLocalDashboard(instance: ServiceInstance): UnifiDashboardData {
        val sites = getLocalJson(instance.id, "sites").objectArray().mapNotNull(::parseSite)

        return coroutineScope {
            val siteFetchSemaphore = Semaphore(MAX_LOCAL_SITE_FETCH_CONCURRENCY)
            val sitePayloads = sites.map { site ->
                async {
                    siteFetchSemaphore.withPermit {
                        val sitePath = "sites/${site.id.encodePath()}"
                        val devices = runCatching {
                            getLocalJson(instance.id, "$sitePath/devices")
                                .objectArray()
                                .mapNotNull { parseDevice(it, fallbackSiteId = site.id) }
                        }.getOrDefault(emptyList())
                        val clients = runCatching {
                            getLocalJson(instance.id, "$sitePath/clients")
                                .objectArray()
                                .mapNotNull { parseClient(it, fallbackSiteId = site.id) }
                        }.getOrDefault(emptyList())
                        val networks = runCatching {
                            getLocalJson(instance.id, "$sitePath/networks")
                                .objectArray()
                                .mapNotNull { parseNetwork(it, fallbackSiteId = site.id) }
                        }.getOrDefault(emptyList())
                        Triple(devices, clients, networks)
                    }
                }
            }.awaitAll()

            UnifiDashboardData(
                authMode = UnifiAuthMode.LOCAL_NETWORK,
                sites = sites,
                hosts = emptyList(),
                devices = sitePayloads.flatMap { it.first },
                clients = sitePayloads.flatMap { it.second },
                networks = sitePayloads.flatMap { it.third },
                ispMetrics = emptyList()
            )
        }
    }

    private suspend fun getLocalJson(instanceId: String, path: String): JsonElement {
        val cleanPath = path.trim('/')
        var lastError: Throwable? = null
        localNetworkApiCandidates(cleanPath).forEach { candidate ->
            runCatching {
                return api.getJson(url = candidate, instanceId = instanceId)
            }.onFailure { lastError = it }
        }
        throw lastError ?: IllegalStateException("UniFi local API request failed.")
    }

    private suspend fun postLocalJson(instanceId: String, path: String, body: JsonObject): JsonElement {
        val cleanPath = path.trim('/')
        var lastError: Throwable? = null
        localNetworkApiCandidates(cleanPath).forEach { candidate ->
            runCatching {
                return api.postJson(url = candidate, body = body, instanceId = instanceId)
            }.onFailure { lastError = it }
        }
        throw lastError ?: IllegalStateException("UniFi local API action failed.")
    }

    private fun localNetworkApiCandidates(path: String): List<String> = listOf(
        "proxy/network/integration/v1/$path",
        "v1/$path"
    ).distinct()

    private suspend fun fetchSiteManagerPaged(instance: ServiceInstance, firstPath: String): List<JsonObject> {
        val collected = mutableListOf<JsonObject>()
        var nextPath: String? = firstPath
        repeat(20) {
            val path = nextPath ?: return@repeat
            val response = api.getJson(
                url = absoluteUrl(SITE_MANAGER_BASE, path),
                bypass = "true",
                apiKey = instance.apiKey
            )
            collected += response.objectArray()
            val token = (response as? JsonObject)?.string("nextToken", "next_token")
            nextPath = if (token.isNullOrBlank()) {
                null
            } else {
                val separator = if (firstPath.contains("?")) "&" else "?"
                "${firstPath.substringBefore("&nextToken=").substringBefore("?nextToken=")}$separator" +
                    "nextToken=${token.encodeQuery()}"
            }
        }
        return collected
    }

    private fun parseSite(raw: JsonObject): UnifiSite? {
        val meta = raw.obj("meta")
        val statistics = raw.obj("statistics")
        val counts = statistics.obj("counts")
        val id = raw.string("siteId", "id", "_id", "site_id") ?: return null
        return UnifiSite(
            id = id,
            name = meta.string("desc", "name") ?: raw.string("name", "desc") ?: "Site",
            hostId = raw.string("hostId", "host_id"),
            timezone = meta.string("timezone") ?: raw.string("timezone"),
            gatewayMac = meta.string("gatewayMac", "gateway_mac") ?: raw.string("gatewayMac", "gateway_mac"),
            deviceCount = counts.int("devices", "device", "totalDevices", "totalDevice") ?: statistics.int("deviceCount", "devices") ?: 0,
            clientCount = counts.int("clients", "client", "totalClients", "totalClient")
                ?: listOfNotNull(counts.int("wifiClient"), counts.int("wiredClient"), counts.int("guestClient")).takeIf { it.isNotEmpty() }?.sum()
                ?: statistics.int("clientCount", "clients")
                ?: 0,
            health = statistics.string("health", "status") ?: raw.string("health", "status")
        )
    }

    private fun parseHost(raw: JsonObject): UnifiHost? {
        val reported = raw.obj("reportedState")
        val userData = raw.obj("userData")
        val id = raw.string("id", "hostId", "_id") ?: return null
        return UnifiHost(
            id = id,
            name = userData.string("name") ?: reported.string("hostname", "name") ?: raw.string("name") ?: "UniFi Host",
            model = raw.string("hardwareId", "model") ?: reported.string("hardwareId", "model"),
            ipAddress = raw.string("ipAddress", "ip") ?: reported.string("ipAddress", "ip"),
            version = reported.string("version", "firmwareVersion") ?: raw.string("version"),
            status = raw.string("status", "state") ?: reported.string("state", "status")
        )
    }

    private fun parseDevice(raw: JsonObject, fallbackSiteId: String? = null): UnifiDevice? {
        val uidb = raw.obj("uidb")
        val id = raw.string("id", "_id", "deviceId", "mac", "macAddress", "mac_address") ?: return null
        val type = (raw.string("type", "deviceType") ?: uidb.string("type", "deviceType") ?: raw.string("model") ?: "device").uppercase()
        return UnifiDevice(
            id = id,
            siteId = raw.string("siteId", "site_id") ?: uidb.string("siteId", "site_id") ?: fallbackSiteId,
            name = raw.string("name", "displayName", "hostname") ?: uidb.string("name", "displayName") ?: raw.string("model") ?: "UniFi Device",
            model = raw.string("model", "modelKey") ?: uidb.string("model", "modelKey"),
            type = type,
            macAddress = raw.string("macAddress", "mac_address", "mac") ?: uidb.string("macAddress", "mac_address", "mac"),
            ipAddress = raw.string("ipAddress", "ip_address", "ip") ?: uidb.string("ipAddress", "ip_address", "ip"),
            firmware = raw.string("firmwareVersion", "version", "firmware") ?: uidb.string("firmwareVersion", "version", "firmware"),
            serial = raw.string("serial", "serialNumber") ?: uidb.string("serial", "serialNumber"),
            online = raw.bool("online", "isOnline") ?: raw.string("state", "status")?.isOnlineStatus() ?: true,
            upgradeable = raw.bool("upgradeable", "hasUpdate", "updateAvailable") ?: uidb.bool("upgradeable", "hasUpdate") ?: false,
            rxBps = raw.double("rx_bps", "rxRateBps", "rx_rate_bps", "rx_bytes-r") ?: uidb.double("rx_bps", "rxRateBps", "rx_rate_bps", "rx_bytes-r"),
            txBps = raw.double("tx_bps", "txRateBps", "tx_rate_bps", "tx_bytes-r") ?: uidb.double("tx_bps", "txRateBps", "tx_rate_bps", "tx_bytes-r"),
            cpuPercent = raw.double("cpu", "cpuPercent", "cpu_percent") ?: uidb.double("cpu", "cpuPercent", "cpu_percent"),
            memoryPercent = raw.double("mem", "memory", "memoryPercent", "memory_percent") ?: uidb.double("mem", "memory", "memoryPercent", "memory_percent"),
            temperatureCelsius = raw.double("temperature", "temp", "general_temperature") ?: uidb.double("temperature", "temp", "general_temperature"),
            connectedClients = raw.int("num_sta", "clientCount", "clients") ?: uidb.int("num_sta", "clientCount", "clients"),
            ports = (uidb.array("port_table", "ports") + raw.array("port_table", "ports")).mapNotNull(::parsePort).distinctBy { it.number },
            radios = (uidb.array("radio_table", "radios") + raw.array("radio_table", "radios")).mapNotNull(::parseRadio).distinctBy { it.name },
            uplinkName = raw.string("uplinkDeviceName", "uplink_device_name") ?: uidb.string("uplinkDeviceName", "uplink_device_name")
        )
    }

    private fun parsePort(raw: JsonObject): UnifiPort? {
        val number = raw.int("port", "port_idx", "portId", "port_id", "idx") ?: return null
        return UnifiPort(
            number = number,
            name = raw.string("name", "label") ?: "Port $number",
            speedMbps = raw.int("speed", "speedMbps", "speed_mbps"),
            online = raw.bool("up", "online", "enabled") ?: raw.string("status", "state")?.isOnlineStatus() ?: false,
            poe = raw.bool("poe", "poe_enable", "poeEnabled") ?: raw.double("poe_power", "poePowerWatts")?.let { it > 0.0 } ?: false,
            poePowerWatts = raw.double("poe_power", "poePowerWatts", "poe_power_watts"),
            rxBps = raw.double("rx_bps", "rxRateBps", "rx_bytes-r"),
            txBps = raw.double("tx_bps", "txRateBps", "tx_bytes-r"),
            profileName = raw.string("portconf_name", "profileName", "profile"),
            uplink = raw.bool("uplink", "isUplink") ?: raw.string("name", "label")?.contains("uplink", ignoreCase = true) ?: false
        )
    }

    private fun parseRadio(raw: JsonObject): UnifiRadio? {
        val name = raw.string("name", "radio", "radioName", "band") ?: return null
        return UnifiRadio(
            name = name,
            channel = raw.int("channel", "channelNumber"),
            band = raw.string("band", "radio"),
            width = raw.string("ht", "width", "channelWidth"),
            txPower = raw.string("tx_power_mode", "txPowerMode", "txPower"),
            utilizationPercent = raw.double("cu_total", "utilization", "utilizationPercent")
        )
    }

    private fun parseClient(raw: JsonObject, fallbackSiteId: String? = null): UnifiClient? {
        val id = raw.string("id", "_id", "clientId", "macAddress", "mac_address", "mac") ?: return null
        val access = raw.obj("access")
        return UnifiClient(
            id = id,
            siteId = raw.string("siteId", "site_id") ?: fallbackSiteId,
            name = raw.string("name", "hostname", "displayName") ?: raw.string("macAddress", "mac_address", "mac") ?: "Client",
            macAddress = raw.string("macAddress", "mac_address", "mac"),
            ipAddress = raw.string("ipAddress", "ip_address", "ip"),
            type = raw.string("type", "deviceType"),
            networkName = raw.string("networkName", "network_name", "ssid", "essid"),
            accessPointName = raw.string("apName", "ap_name", "uplinkDeviceName", "uplink_device_name"),
            signalDbm = raw.int("signal", "rssi"),
            experiencePercent = raw.double("wifiExperience", "experience", "satisfaction"),
            rxBytes = raw.double("rxBytes", "rx_bytes"),
            txBytes = raw.double("txBytes", "tx_bytes"),
            rxBps = raw.double("rx_bps", "rxRateBps", "rx_rate_bps", "rx_bytes-r"),
            txBps = raw.double("tx_bps", "txRateBps", "tx_rate_bps", "tx_bytes-r"),
            connectedSeconds = raw.long("uptime", "connectedSeconds", "connected_seconds"),
            authorized = access.bool("authorized") ?: raw.bool("authorized")
        )
    }

    private fun parseNetwork(raw: JsonObject, fallbackSiteId: String? = null): UnifiNetwork? {
        val id = raw.string("id", "_id", "networkId") ?: return null
        return UnifiNetwork(
            id = id,
            siteId = raw.string("siteId", "site_id") ?: fallbackSiteId,
            name = raw.string("name", "displayName") ?: "Network",
            purpose = raw.string("purpose", "type"),
            subnet = raw.string("subnet", "ipSubnet", "ip_subnet"),
            vlanId = raw.int("vlanId", "vlan", "vlan_id")
        )
    }

    private fun parseIspMetric(raw: JsonObject): UnifiIspMetric? {
        if (raw.isEmpty()) return null
        val downloadKbps = raw.double("download_kbps", "downloadKbps", "downloadKilobitsPerSecond")
        val uploadKbps = raw.double("upload_kbps", "uploadKbps", "uploadKilobitsPerSecond")
        return UnifiIspMetric(
            siteId = raw.string("siteId", "site_id"),
            hostId = raw.string("hostId", "host_id"),
            timestamp = raw.string("metricTime", "timestamp", "time", "datetime"),
            latencyMs = raw.double("avgLatency", "latency", "latencyMs", "wanLatencyMs"),
            packetLossPercent = raw.double("packetLoss", "packet_loss", "packetLossPercent"),
            downloadMbps = downloadKbps?.div(1000.0) ?: raw.double("downloadMbps", "download_mbps", "download"),
            uploadMbps = uploadKbps?.div(1000.0) ?: raw.double("uploadMbps", "upload_mbps", "upload"),
            uptimePercent = raw.double("uptime", "uptimePercent")
        )
    }

    private fun parseIspMetricSeries(raw: JsonObject): List<UnifiIspMetric> {
        val periods = raw.array("periods")
        if (periods.isEmpty()) return listOfNotNull(parseIspMetric(raw))

        val siteId = raw.string("siteId", "site_id")
        val hostId = raw.string("hostId", "host_id")
        return periods.mapNotNull { period ->
            val wan = period.obj("data").obj("wan")
            val source = if (wan.isEmpty()) period else wan
            val metric = parseIspMetric(source) ?: return@mapNotNull null
            metric.copy(
                siteId = metric.siteId ?: period.string("siteId", "site_id") ?: siteId,
                hostId = metric.hostId ?: period.string("hostId", "host_id") ?: hostId,
                timestamp = metric.timestamp ?: period.string("metricTime", "timestamp", "time", "datetime")
            )
        }
    }

    private fun authModeFor(url: String): UnifiAuthMode {
        val normalized = url.lowercase()
        return if (normalized.contains("api.ui.com") || normalized.contains("unifi.ui.com")) {
            UnifiAuthMode.SITE_MANAGER
        } else {
            UnifiAuthMode.LOCAL_NETWORK
        }
    }

    private fun cleanUrl(raw: String): String {
        var clean = raw.trim()
        if (!clean.startsWith("http://") && !clean.startsWith("https://")) clean = "https://$clean"
        return clean.replace(Regex("/+$"), "")
    }

    private fun cleanOptionalUrl(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        return cleanUrl(raw)
    }

    private fun absoluteUrl(baseUrl: String, path: String): String {
        return cleanUrl(baseUrl) + "/" + path.trimStart('/')
    }

    private fun String.encodePath(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name()).replace("+", "%20")
    private fun String.encodeQuery(): String = java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun demoDevice(
        id: String,
        siteId: String,
        name: String,
        model: String,
        type: String,
        ip: String,
        online: Boolean,
        upgradeable: Boolean,
        rx: Double,
        tx: Double,
        cpu: Double? = null,
        memory: Double? = null,
        temperature: Double? = null,
        clients: Int? = null,
        ports: List<UnifiPort> = emptyList(),
        radios: List<UnifiRadio> = emptyList()
    ) = UnifiDevice(
        id = id,
        siteId = siteId,
        name = name,
        model = model,
        type = type,
        macAddress = "78:45:58:aa:bb:${id.removePrefix("d").padStart(2, '0')}",
        ipAddress = ip,
        firmware = if (type == "UAP") "7.0.10" else "6.6.61",
        serial = "UNIFI-${id.uppercase()}",
        online = online,
        upgradeable = upgradeable,
        rxBps = rx,
        txBps = tx,
        cpuPercent = cpu,
        memoryPercent = memory,
        temperatureCelsius = temperature,
        connectedClients = clients,
        ports = ports,
        radios = radios,
        uplinkName = ports.firstOrNull { it.uplink }?.name
    )

    private fun demoClient(
        id: String,
        siteId: String,
        name: String,
        ip: String,
        type: String,
        network: String,
        ap: String?,
        signal: Int?,
        experience: Double?,
        rxBytes: Double,
        txBytes: Double,
        rxBps: Double?,
        txBps: Double?,
        authorized: Boolean
    ) = UnifiClient(
        id = id,
        siteId = siteId,
        name = name,
        macAddress = "a4:83:e7:${id.removePrefix("c").padStart(2, '0')}:10:20",
        ipAddress = ip,
        type = type,
        networkName = network,
        accessPointName = ap,
        signalDbm = signal,
        experiencePercent = experience,
        rxBytes = rxBytes,
        txBytes = txBytes,
        rxBps = rxBps,
        txBps = txBps,
        connectedSeconds = 3_600,
        authorized = authorized
    )

    private fun demoPorts(name: String, speed: Int, rx: Double, tx: Double): List<UnifiPort> = listOf(
        UnifiPort(1, name, speed, true, false, null, rx, tx, null, true),
        UnifiPort(2, "LAN", 1000, true, false, null, rx / 2, tx / 2, null, false)
    )

    private fun demoPoePort(number: Int, name: String, watts: Double): UnifiPort =
        UnifiPort(number, name, 1000, true, true, watts, 196_608.0, 98_304.0, null, false)

    private fun demoRadios(): List<UnifiRadio> = listOf(
        UnifiRadio("5 GHz", 44, "5G", "80 MHz", "Auto", 36.0),
        UnifiRadio("2.4 GHz", 1, "2G", "20 MHz", "Auto", 24.0)
    )

    private fun demoIspMetrics(siteId: String, hostId: String, downloadBase: Double, uploadBase: Double): List<UnifiIspMetric> =
        (0 until 96).map { index ->
            val spike = index % 23 == 0
            UnifiIspMetric(
                siteId = siteId,
                hostId = hostId,
                timestamp = Instant.now().minusSeconds(((96 - index) * 15 * 60).toLong()).toString(),
                latencyMs = if (spike) 52.0 else 10.0 + (index % 9),
                packetLossPercent = if (spike) 1.2 else 0.0,
                downloadMbps = downloadBase + ((index * 7) % 42),
                uploadMbps = uploadBase + ((index * 5) % 24),
                uptimePercent = if (siteId == "casa") 99.97 else 100.0
            )
        }

    private companion object {
        const val SITE_MANAGER_BASE = "https://api.ui.com"
        const val MAX_LOCAL_SITE_FETCH_CONCURRENCY = 4
    }
}

private fun JsonElement.unwrapData(): JsonElement {
    return (this as? JsonObject)?.get("data") ?: this
}

private fun JsonElement.objectArray(): List<JsonObject> {
    val unwrapped = unwrapData()
    return when (unwrapped) {
        is JsonArray -> unwrapped.mapNotNull { it as? JsonObject }
        is JsonObject -> {
            val nested = listOf("items", "results", "hosts", "sites", "devices", "clients", "networks")
                .firstNotNullOfOrNull { key -> unwrapped[key] as? JsonArray }
            nested?.mapNotNull { it as? JsonObject } ?: listOf(unwrapped)
        }
        else -> emptyList()
    }
}

private fun JsonObject.obj(vararg keys: String): JsonObject {
    keys.forEach { key ->
        val value = this[key]
        if (value is JsonObject) return value
    }
    return JsonObject(emptyMap())
}

private fun JsonObject.array(vararg keys: String): List<JsonObject> {
    keys.forEach { key ->
        val value = this[key]
        if (value is JsonArray) return value.mapNotNull { it as? JsonObject }
    }
    return emptyList()
}

private fun JsonObject.string(vararg keys: String): String? {
    keys.forEach { key ->
        val value = this[key]
        when (value) {
            is JsonPrimitive -> value.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
            is JsonObject -> value.string("name", "id", "value")?.let { return it }
            is JsonArray -> value.firstOrNull()?.let { first ->
                when (first) {
                    is JsonPrimitive -> first.contentOrNull?.trim()?.takeIf { it.isNotBlank() }?.let { return it }
                    is JsonObject -> first.string("name", "id", "value")?.let { return it }
                    else -> Unit
                }
            }
            JsonNull, null -> Unit
        }
    }
    return null
}

private fun JsonObject.int(vararg keys: String): Int? {
    keys.forEach { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@forEach
        primitive.intOrNull?.let { return it }
        primitive.contentOrNull?.toIntOrNull()?.let { return it }
        primitive.doubleOrNull?.toInt()?.let { return it }
    }
    return null
}

private fun JsonObject.long(vararg keys: String): Long? {
    keys.forEach { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@forEach
        primitive.contentOrNull?.toLongOrNull()?.let { return it }
        primitive.doubleOrNull?.toLong()?.let { return it }
    }
    return null
}

private fun JsonObject.double(vararg keys: String): Double? {
    keys.forEach { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@forEach
        primitive.doubleOrNull?.let { return it }
        primitive.contentOrNull?.toDoubleOrNull()?.let { return it }
    }
    return null
}

private fun JsonObject.bool(vararg keys: String): Boolean? {
    keys.forEach { key ->
        val primitive = this[key] as? JsonPrimitive ?: return@forEach
        primitive.booleanOrNull?.let { return it }
        when (primitive.contentOrNull?.lowercase()) {
            "true", "yes", "online", "connected", "ok" -> return true
            "false", "no", "offline", "disconnected", "error" -> return false
        }
    }
    return null
}

private fun String.isOnlineStatus(): Boolean {
    val normalized = lowercase()
    return normalized == "online" || normalized == "connected" || normalized == "ok" || normalized == "1"
}
