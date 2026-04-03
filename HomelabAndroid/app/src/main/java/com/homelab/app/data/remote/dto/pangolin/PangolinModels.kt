package com.homelab.app.data.remote.dto.pangolin

import kotlinx.serialization.Serializable

@Serializable
data class PangolinEnvelope<T>(
    val data: T,
    val pagination: PangolinPagination? = null,
    val success: Boolean? = null,
    val error: Boolean? = null,
    val message: String? = null,
    val status: Int? = null
)

@Serializable
data class PangolinPagination(
    val total: Int = 0,
    val limit: Int? = null,
    val offset: Int? = null,
    val pageSize: Int? = null,
    val page: Int? = null
)

@Serializable
data class PangolinOrg(
    val orgId: String,
    val name: String,
    val subnet: String? = null,
    val utilitySubnet: String? = null,
    val suspendOrg: Boolean? = null,
    val suspendAt: Long? = null,
    val isBillingOrg: Boolean? = null
)

@Serializable
data class PangolinOrgsData(
    val orgs: List<PangolinOrg> = emptyList()
)

typealias PangolinOrgsResponse = PangolinEnvelope<PangolinOrgsData>

@Serializable
data class PangolinSite(
    val siteId: Int,
    val niceId: String,
    val name: String,
    val subnet: String? = null,
    val megabytesIn: Double? = null,
    val megabytesOut: Double? = null,
    val type: String? = null,
    val online: Boolean = false,
    val address: String? = null,
    val newtVersion: String? = null,
    val exitNodeName: String? = null,
    val exitNodeEndpoint: String? = null,
    val newtUpdateAvailable: Boolean? = null
)

@Serializable
data class PangolinSitesData(
    val sites: List<PangolinSite> = emptyList()
)

typealias PangolinSitesResponse = PangolinEnvelope<PangolinSitesData>

@Serializable
data class PangolinSiteResource(
    val siteResourceId: Int,
    val siteId: Int,
    val orgId: String,
    val niceId: String,
    val name: String,
    val mode: String? = null,
    val protocol: String? = null,
    val proxyPort: Int? = null,
    val destinationPort: Int? = null,
    val destination: String? = null,
    val enabled: Boolean = false,
    val alias: String? = null,
    val aliasAddress: String? = null,
    val tcpPortRangeString: String? = null,
    val udpPortRangeString: String? = null,
    val disableIcmp: Boolean? = null,
    val authDaemonMode: String? = null,
    val authDaemonPort: Int? = null,
    val siteName: String,
    val siteNiceId: String,
    val siteAddress: String? = null
)

@Serializable
data class PangolinSiteResourcesData(
    val siteResources: List<PangolinSiteResource> = emptyList()
)

typealias PangolinSiteResourcesResponse = PangolinEnvelope<PangolinSiteResourcesData>

@Serializable
data class PangolinTarget(
    val targetId: Int,
    val ip: String,
    val port: Int,
    val enabled: Boolean = false,
    val healthStatus: String? = null,
    val method: String? = null,
    val resourceId: Int? = null,
    val siteId: Int? = null,
    val siteType: String? = null,
    val hcEnabled: Boolean? = null,
    val hcPath: String? = null,
    val hcScheme: String? = null,
    val hcMode: String? = null,
    val hcHostname: String? = null,
    val hcPort: Int? = null,
    val hcInterval: Int? = null,
    val hcUnhealthyInterval: Int? = null,
    val hcTimeout: Int? = null,
    val hcHeaders: List<PangolinHeader>? = null,
    val hcFollowRedirects: Boolean? = null,
    val hcMethod: String? = null,
    val hcStatus: String? = null,
    val hcHealth: String? = null,
    val hcTlsServerName: String? = null,
    val path: String? = null,
    val pathMatchType: String? = null,
    val rewritePath: String? = null,
    val rewritePathType: String? = null,
    val priority: Int? = null
)

@Serializable
data class PangolinHeader(
    val name: String,
    val value: String
)

@Serializable
data class PangolinResource(
    val resourceId: Int,
    val name: String,
    val ssl: Boolean = false,
    val fullDomain: String? = null,
    val sso: Boolean = false,
    val whitelist: Boolean = false,
    val http: Boolean = false,
    val protocol: String? = null,
    val proxyPort: Int? = null,
    val enabled: Boolean = false,
    val domainId: String? = null,
    val niceId: String,
    val targets: List<PangolinTarget> = emptyList()
)

@Serializable
data class PangolinResourcesData(
    val resources: List<PangolinResource> = emptyList()
)

typealias PangolinResourcesResponse = PangolinEnvelope<PangolinResourcesData>

@Serializable
data class PangolinTargetsData(
    val targets: List<PangolinTarget> = emptyList()
)

typealias PangolinTargetsResponse = PangolinEnvelope<PangolinTargetsData>

@Serializable
data class PangolinClientSite(
    val siteId: Int,
    val siteName: String? = null,
    val siteNiceId: String? = null
)

@Serializable
data class PangolinClient(
    val clientId: Int,
    val orgId: String,
    val name: String,
    val subnet: String? = null,
    val megabytesIn: Double? = null,
    val megabytesOut: Double? = null,
    val type: String? = null,
    val online: Boolean = false,
    val olmVersion: String? = null,
    val niceId: String,
    val approvalState: String? = null,
    val archived: Boolean = false,
    val blocked: Boolean = false,
    val sites: List<PangolinClientSite> = emptyList(),
    val olmUpdateAvailable: Boolean? = null
)

@Serializable
data class PangolinClientsData(
    val clients: List<PangolinClient> = emptyList()
)

typealias PangolinClientsResponse = PangolinEnvelope<PangolinClientsData>

@Serializable
data class PangolinUserDevice(
    val clientId: Int,
    val orgId: String,
    val name: String,
    val subnet: String? = null,
    val megabytesIn: Double? = null,
    val megabytesOut: Double? = null,
    val orgName: String? = null,
    val type: String? = null,
    val online: Boolean = false,
    val olmVersion: String? = null,
    val userId: String? = null,
    val username: String? = null,
    val userEmail: String? = null,
    val niceId: String,
    val agent: String? = null,
    val approvalState: String? = null,
    val olmArchived: Boolean = false,
    val archived: Boolean = false,
    val blocked: Boolean = false,
    val deviceModel: String? = null,
    val fingerprintPlatform: String? = null,
    val fingerprintOsVersion: String? = null,
    val fingerprintKernelVersion: String? = null,
    val fingerprintArch: String? = null,
    val fingerprintSerialNumber: String? = null,
    val fingerprintUsername: String? = null,
    val fingerprintHostname: String? = null,
    val olmUpdateAvailable: Boolean? = null
)

@Serializable
data class PangolinUserDevicesData(
    val devices: List<PangolinUserDevice> = emptyList()
)

typealias PangolinUserDevicesResponse = PangolinEnvelope<PangolinUserDevicesData>

@Serializable
data class PangolinSiteResourceUser(
    val userId: String
)

@Serializable
data class PangolinSiteResourceUsersData(
    val users: List<PangolinSiteResourceUser> = emptyList()
)

typealias PangolinSiteResourceUsersResponse = PangolinEnvelope<PangolinSiteResourceUsersData>

@Serializable
data class PangolinSiteResourceRole(
    val roleId: Int
)

@Serializable
data class PangolinSiteResourceRolesData(
    val roles: List<PangolinSiteResourceRole> = emptyList()
)

typealias PangolinSiteResourceRolesResponse = PangolinEnvelope<PangolinSiteResourceRolesData>

@Serializable
data class PangolinSiteResourceClient(
    val clientId: Int
)

@Serializable
data class PangolinSiteResourceClientsData(
    val clients: List<PangolinSiteResourceClient> = emptyList()
)

typealias PangolinSiteResourceClientsResponse = PangolinEnvelope<PangolinSiteResourceClientsData>

@Serializable
data class PangolinDomain(
    val domainId: String,
    val baseDomain: String,
    val verified: Boolean = false,
    val type: String? = null,
    val failed: Boolean = false,
    val tries: Int? = null,
    val configManaged: Boolean? = null,
    val certResolver: String? = null,
    val preferWildcardCert: Boolean? = null,
    val errorMessage: String? = null
)

@Serializable
data class PangolinDomainsData(
    val domains: List<PangolinDomain> = emptyList()
)

typealias PangolinDomainsResponse = PangolinEnvelope<PangolinDomainsData>
