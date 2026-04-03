package com.homelab.app.data.repository

import com.homelab.app.data.remote.api.PangolinApi
import com.homelab.app.data.remote.dto.pangolin.PangolinClient
import com.homelab.app.data.remote.dto.pangolin.PangolinDomain
import com.homelab.app.data.remote.dto.pangolin.PangolinOrg
import com.homelab.app.data.remote.dto.pangolin.PangolinResource
import com.homelab.app.data.remote.dto.pangolin.PangolinSite
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResourceClient
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResource
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResourceRole
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResourceUser
import com.homelab.app.data.remote.dto.pangolin.PangolinTarget
import com.homelab.app.data.remote.dto.pangolin.PangolinUserDevice
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class PangolinSnapshot(
    val orgs: List<PangolinOrg>,
    val sites: List<PangolinSite>,
    val siteResources: List<PangolinSiteResource>,
    val resources: List<PangolinResource>,
    val targetsByResourceId: Map<Int, List<PangolinTarget>>,
    val clients: List<PangolinClient>,
    val userDevices: List<PangolinUserDevice>,
    val domains: List<PangolinDomain>
)

data class PangolinSiteResourceBindings(
    val userIds: List<String>,
    val roleIds: List<Int>,
    val clientIds: List<Int>
)

@Singleton
class PangolinRepository @Inject constructor(
    private val api: PangolinApi,
    private val okHttpClient: OkHttpClient
) {
    private companion object {
        const val DASHBOARD_RESOURCE_LIMIT = 8
    }

    suspend fun authenticate(url: String, apiKey: String, orgId: String? = null) {
        withContext(Dispatchers.IO) {
            val token = cleanToken(apiKey)
            val cleanedOrgId = orgId?.trim().orEmpty()
            val path = if (cleanedOrgId.isNotEmpty()) "v1/org/$cleanedOrgId/sites?pageSize=1&page=1" else "v1/orgs"
            val request = Request.Builder()
                .url("${cleanUrl(url)}/$path")
                .addHeader("Authorization", "Bearer $token")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw IllegalStateException("Pangolin authentication failed")
                }
            }
        }
    }

    suspend fun listOrgs(instanceId: String, scopedOrgId: String? = null): List<PangolinOrg> {
        val trimmed = scopedOrgId?.trim().orEmpty()
        if (trimmed.isNotEmpty()) {
            return listOf(PangolinOrg(orgId = trimmed, name = trimmed, subnet = null, utilitySubnet = null, isBillingOrg = null))
        }
        return api.listOrgs(instanceId = instanceId).data.orgs.sortedBy { it.name.lowercase() }
    }

    suspend fun getSnapshot(
        instanceId: String,
        orgId: String,
        orgs: List<PangolinOrg>? = null
    ): PangolinSnapshot = coroutineScope {
        val sitesDeferred = async { listAllSites(instanceId, orgId) }
        val siteResourcesDeferred = async { listAllSiteResources(instanceId, orgId) }
        val resourcesDeferred = async { listAllResources(instanceId, orgId) }
        val clientsDeferred = async { listAllClients(instanceId, orgId) }
        val userDevicesDeferred = async { listAllUserDevices(instanceId, orgId) }
        val domainsDeferred = async { listAllDomains(instanceId, orgId) }

        val resources = resourcesDeferred.await()
        // The dashboard renders the top 8 public resources only, so avoid fetching
        // targets for the full list on every refresh.
        val targetsByResourceId = listTargetsByResource(instanceId, resources.take(DASHBOARD_RESOURCE_LIMIT))

        PangolinSnapshot(
            orgs = orgs.orEmpty(),
            sites = sitesDeferred.await(),
            siteResources = siteResourcesDeferred.await(),
            resources = resources,
            targetsByResourceId = targetsByResourceId,
            clients = clientsDeferred.await(),
            userDevices = userDevicesDeferred.await(),
            domains = domainsDeferred.await()
        )
    }

    suspend fun getAggregateSummary(instanceId: String, scopedOrgId: String? = null): Triple<Int, Int, Int> {
        val orgs = listOrgs(instanceId, scopedOrgId)
        var totalSites = 0
        var totalResources = 0
        var totalClients = 0

        for (org in orgs) {
            totalSites += listAllSites(instanceId, org.orgId).size
            totalResources += listAllResources(instanceId, org.orgId).size + listAllSiteResources(instanceId, org.orgId).size
            totalClients += listAllClients(instanceId, org.orgId).size
            totalClients += listAllUserDevices(instanceId, org.orgId).size
        }

        return Triple(totalSites, totalResources, totalClients)
    }

    private suspend fun listAllSites(instanceId: String, orgId: String): List<PangolinSite> {
        val collected = mutableListOf<PangolinSite>()
        var page = 1
        val pageSize = 100
        while (true) {
            val response = api.listSites(orgId = orgId, instanceId = instanceId, pageSize = pageSize, page = page)
            val batch = response.data.sites
            if (batch.isEmpty()) break
            collected += batch
            val total = response.pagination?.total ?: 0
            if (total > 0 && collected.size >= total) break
            page += 1
        }
        return collected.sortedWith(
            compareByDescending<PangolinSite> { it.online }
                .thenBy { it.name.lowercase() }
        )
    }

    private suspend fun listAllSiteResources(instanceId: String, orgId: String): List<PangolinSiteResource> {
        val collected = mutableListOf<PangolinSiteResource>()
        var page = 1
        val pageSize = 100
        while (true) {
            val response = api.listSiteResources(orgId = orgId, instanceId = instanceId, pageSize = pageSize, page = page)
            val batch = response.data.siteResources
            if (batch.isEmpty()) break
            collected += batch
            val total = response.pagination?.total ?: 0
            if (total > 0 && collected.size >= total) break
            page += 1
        }
        return collected.sortedWith(
            compareByDescending<PangolinSiteResource> { it.enabled }
                .thenBy { it.siteName.lowercase() }
                .thenBy { it.name.lowercase() }
        )
    }

    private suspend fun listAllResources(instanceId: String, orgId: String): List<PangolinResource> {
        val collected = mutableListOf<PangolinResource>()
        var page = 1
        val pageSize = 100
        while (true) {
            val response = api.listResources(orgId = orgId, instanceId = instanceId, pageSize = pageSize, page = page)
            val batch = response.data.resources
            if (batch.isEmpty()) break
            collected += batch
            val total = response.pagination?.total ?: 0
            if (total > 0 && collected.size >= total) break
            page += 1
        }
        return collected.sortedWith(
            compareByDescending<PangolinResource> { it.enabled }
                .thenBy { it.name.lowercase() }
        )
    }

    private suspend fun listAllClients(instanceId: String, orgId: String): List<PangolinClient> {
        val collected = mutableListOf<PangolinClient>()
        var page = 1
        val pageSize = 100
        while (true) {
            val response = api.listClients(orgId = orgId, instanceId = instanceId, pageSize = pageSize, page = page)
            val batch = response.data.clients
            if (batch.isEmpty()) break
            collected += batch
            val total = response.pagination?.total ?: 0
            if (total > 0 && collected.size >= total) break
            page += 1
        }
        return collected.sortedWith(
            compareByDescending<PangolinClient> { it.online }
                .thenBy { it.blocked }
                .thenBy { it.name.lowercase() }
        )
    }

    private suspend fun listAllDomains(instanceId: String, orgId: String): List<PangolinDomain> {
        val collected = mutableListOf<PangolinDomain>()
        var offset = 0
        val limit = 1000
        while (true) {
            val response = api.listDomains(orgId = orgId, instanceId = instanceId, limit = limit, offset = offset)
            val batch = response.data.domains
            if (batch.isEmpty()) break
            collected += batch
            val total = response.pagination?.total ?: 0
            offset += limit
            if (total > 0 && collected.size >= total) break
        }
        return collected.sortedWith(
            compareByDescending<PangolinDomain> { it.verified }
                .thenBy { it.baseDomain.lowercase() }
        )
    }

    private suspend fun listAllUserDevices(instanceId: String, orgId: String): List<PangolinUserDevice> {
        val collected = mutableListOf<PangolinUserDevice>()
        var page = 1
        val pageSize = 100
        while (true) {
            val response = api.listUserDevices(orgId = orgId, instanceId = instanceId, pageSize = pageSize, page = page)
            val batch = response.data.devices
            if (batch.isEmpty()) break
            collected += batch
            val total = response.pagination?.total ?: 0
            if (total > 0 && collected.size >= total) break
            page += 1
        }
        return collected.sortedWith(
            compareByDescending<PangolinUserDevice> { it.online }
                .thenBy { it.blocked }
                .thenBy { it.name.lowercase() }
        )
    }

    private suspend fun listAllTargets(instanceId: String, resourceId: Int): List<PangolinTarget> {
        val collected = mutableListOf<PangolinTarget>()
        var offset = 0
        val limit = 1000
        while (true) {
            val response = api.listTargets(resourceId = resourceId, instanceId = instanceId, limit = limit, offset = offset)
            val batch = response.data.targets
            if (batch.isEmpty()) break
            collected += batch
            val total = response.pagination?.total ?: 0
            offset += limit
            if (total > 0 && collected.size >= total) break
        }
        return collected.sortedWith(
            compareByDescending<PangolinTarget> { it.enabled }
                .thenBy { it.priority ?: Int.MAX_VALUE }
                .thenBy { it.ip.lowercase() }
                .thenBy { it.port }
        )
    }

    private suspend fun listTargetsByResource(
        instanceId: String,
        resources: List<PangolinResource>
    ): Map<Int, List<PangolinTarget>> = coroutineScope {
        val pairs = mutableListOf<Pair<Int, List<PangolinTarget>>>()
        for (batch in resources.chunked(4)) {
            pairs += batch.map { resource ->
                async {
                    resource.resourceId to listAllTargets(instanceId, resource.resourceId)
                }
            }.awaitAll()
        }
        pairs.toMap()
    }

    suspend fun getSiteResourceBindings(
        instanceId: String,
        siteResourceId: Int
    ): PangolinSiteResourceBindings = coroutineScope {
        val usersDeferred = async { api.listSiteResourceUsers(siteResourceId = siteResourceId, instanceId = instanceId).data.users }
        val rolesDeferred = async { api.listSiteResourceRoles(siteResourceId = siteResourceId, instanceId = instanceId).data.roles }
        val clientsDeferred = async { api.listSiteResourceClients(siteResourceId = siteResourceId, instanceId = instanceId).data.clients }

        PangolinSiteResourceBindings(
            userIds = usersDeferred.await().map(PangolinSiteResourceUser::userId),
            roleIds = rolesDeferred.await().map(PangolinSiteResourceRole::roleId),
            clientIds = clientsDeferred.await().map(PangolinSiteResourceClient::clientId)
        )
    }

    suspend fun updateResource(
        instanceId: String,
        resourceId: Int,
        name: String,
        enabled: Boolean,
        sso: Boolean,
        ssl: Boolean
    ): PangolinResource {
        val body = buildJsonObject {
            put("name", name)
            put("enabled", enabled)
            put("sso", sso)
            put("ssl", ssl)
        }
        return api.updateResource(resourceId = resourceId, instanceId = instanceId, body = body).data
    }

    suspend fun updateTarget(
        instanceId: String,
        targetId: Int,
        siteId: Int,
        ip: String,
        port: Int,
        enabled: Boolean
    ): PangolinTarget {
        val body = buildJsonObject {
            put("siteId", siteId)
            put("ip", ip)
            put("port", port)
            put("enabled", enabled)
        }
        return api.updateTarget(targetId = targetId, instanceId = instanceId, body = body).data
    }

    suspend fun updateSiteResource(
        instanceId: String,
        siteResourceId: Int,
        bindings: PangolinSiteResourceBindings,
        name: String,
        siteId: Int,
        mode: String,
        destination: String,
        enabled: Boolean,
        alias: String,
        tcpPortRangeString: String,
        udpPortRangeString: String,
        disableIcmp: Boolean,
        authDaemonPort: Int?,
        authDaemonMode: String?
    ): PangolinSiteResource {
        val body = buildJsonObject {
            put("name", name)
            put("siteId", siteId)
            put("mode", mode)
            put("destination", destination)
            put("enabled", enabled)
            if (alias.isBlank()) {
                put("alias", JsonNull)
            } else {
                put("alias", alias)
            }
            putStringArray("userIds", bindings.userIds)
            putIntArray("roleIds", bindings.roleIds)
            putIntArray("clientIds", bindings.clientIds)
            if (tcpPortRangeString.isNotBlank()) {
                put("tcpPortRangeString", tcpPortRangeString)
            }
            if (udpPortRangeString.isNotBlank()) {
                put("udpPortRangeString", udpPortRangeString)
            }
            put("disableIcmp", disableIcmp)
            if (authDaemonPort == null) {
                put("authDaemonPort", JsonNull)
            } else {
                put("authDaemonPort", authDaemonPort)
            }
            authDaemonMode?.takeIf { it.isNotBlank() }?.let { put("authDaemonMode", it) }
        }
        return api.updateSiteResource(siteResourceId = siteResourceId, instanceId = instanceId, body = body).data
    }

    suspend fun createResource(
        instanceId: String,
        orgId: String,
        name: String,
        protocol: String,
        enabled: Boolean,
        domainId: String?,
        subdomain: String?,
        proxyPort: Int?
    ): PangolinResource {
        val normalizedProtocol = protocol.trim().lowercase()
        val body = buildJsonObject {
            put("name", name)
            put("enabled", enabled)
            put("http", normalizedProtocol == "http")
            put("protocol", normalizedProtocol)
            when (normalizedProtocol) {
                "http" -> {
                    put("domainId", domainId.orEmpty())
                    if (subdomain.isNullOrBlank()) {
                        put("subdomain", JsonNull)
                    } else {
                        put("subdomain", subdomain)
                    }
                }
                "tcp", "udp" -> put("proxyPort", proxyPort ?: 0)
            }
        }
        return api.createResource(orgId = orgId, instanceId = instanceId, body = body).data
    }

    suspend fun createSiteResource(
        instanceId: String,
        orgId: String,
        name: String,
        siteId: Int,
        mode: String,
        destination: String,
        enabled: Boolean,
        alias: String,
        tcpPortRangeString: String,
        udpPortRangeString: String,
        disableIcmp: Boolean,
        authDaemonPort: Int?,
        authDaemonMode: String?
    ): PangolinSiteResource {
        val body = buildJsonObject {
            put("name", name)
            put("siteId", siteId)
            put("mode", mode)
            put("destination", destination)
            put("enabled", enabled)
            if (alias.isBlank()) {
                put("alias", JsonNull)
            } else {
                put("alias", alias)
            }
            putStringArray("userIds", emptyList())
            putIntArray("roleIds", emptyList())
            putIntArray("clientIds", emptyList())
            put("tcpPortRangeString", tcpPortRangeString.ifBlank { "*" })
            put("udpPortRangeString", udpPortRangeString.ifBlank { "*" })
            put("disableIcmp", disableIcmp)
            if (authDaemonPort == null) {
                put("authDaemonPort", JsonNull)
            } else {
                put("authDaemonPort", authDaemonPort)
            }
            authDaemonMode?.takeIf { it.isNotBlank() }?.let { put("authDaemonMode", it) }
        }
        return api.createSiteResource(orgId = orgId, instanceId = instanceId, body = body).data
    }

    suspend fun createTarget(
        instanceId: String,
        resourceId: Int,
        siteId: Int,
        ip: String,
        port: Int,
        enabled: Boolean,
        method: String?
    ): PangolinTarget {
        val body = buildJsonObject {
            put("siteId", siteId)
            put("ip", ip)
            put("port", port)
            put("enabled", enabled)
            method?.takeIf { it.isNotBlank() }?.let { put("method", it) }
        }
        return api.createTarget(resourceId = resourceId, instanceId = instanceId, body = body).data
    }

    suspend fun deleteResource(instanceId: String, resourceId: Int) {
        api.deleteResource(resourceId = resourceId, instanceId = instanceId)
    }

    private fun cleanUrl(url: String): String = url.trim().removeSuffix("/")

    private fun cleanToken(apiKey: String): String {
        val raw = apiKey.trim()
        return if (raw.startsWith("bearer ", ignoreCase = true)) {
            raw.substring(7).trim()
        } else {
            raw
        }
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putStringArray(
        key: String,
        values: List<String>
    ) {
        put(key, JsonArray(values.map { kotlinx.serialization.json.JsonPrimitive(it) }))
    }

    private fun kotlinx.serialization.json.JsonObjectBuilder.putIntArray(
        key: String,
        values: List<Int>
    ) {
        put(key, JsonArray(values.map { kotlinx.serialization.json.JsonPrimitive(it) }))
    }
}
