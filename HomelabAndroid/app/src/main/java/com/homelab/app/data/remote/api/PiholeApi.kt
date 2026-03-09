package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.pihole.PiholeAuthResponse
import com.homelab.app.data.remote.dto.pihole.PiholeBlockingRequest
import com.homelab.app.data.remote.dto.pihole.PiholeBlockingStatus
import com.homelab.app.data.remote.dto.pihole.PiholeQueryHistory
import com.homelab.app.data.remote.dto.pihole.PiholeStats
import com.homelab.app.data.remote.dto.pihole.PiholeUpstream
import com.homelab.app.data.remote.dto.pihole.PiholeAddDomainRequest
import kotlinx.serialization.json.JsonElement
import retrofit2.http.*

interface PiholeApi {

    @POST
    suspend fun authenticate(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Body credentials: Map<String, String>
    ): PiholeAuthResponse

    @GET
    suspend fun validateLegacyAuth(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Header("X-Homelab-Bypass") bypass: String = "true"
    ): JsonElement

    @GET("api/stats/summary")
    suspend fun getStats(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null
    ): PiholeStats

    @GET("api/dns/blocking")
    suspend fun getBlockingStatus(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null
    ): PiholeBlockingStatus

    @POST("api/dns/blocking")
    suspend fun setBlocking(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Body request: PiholeBlockingRequest
    )

    // Domains (v6 API)
    @GET("api/domains")
    suspend fun getDomainsRaw(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null
    ): JsonElement

    @GET("admin/api.php")
    suspend fun getDomainsLegacy(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("list") list: String = "all",
        @Query("auth") auth: String? = null
    ): JsonElement

    @POST("api/domains/{list}/exact")
    suspend fun addDomain(
        @Path("list") list: String,
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Body request: PiholeAddDomainRequest
    )

    @GET("admin/api.php")
    suspend fun addDomainLegacy(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("list") list: String,
        @Query("add") domain: String,
        @Query("auth") auth: String? = null
    ): JsonElement

    @DELETE("api/domains/{list}/exact/{domain}")
    suspend fun removeDomain(
        @Path("list") list: String,
        @Path("domain") domain: String,
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null
    )

    @GET("admin/api.php")
    suspend fun removeDomainLegacy(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("list") list: String,
        @Query("sub") domain: String,
        @Query("auth") auth: String? = null
    ): JsonElement

    // Pi-Hole APIs return dynamic formats (arrays vs objects based on version v5/v6)
    // We return JsonElement and parse it manually in Repository exactly as iOS does.
    
    @GET("api/stats/top_domains")
    suspend fun getTopDomains(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Query("count") count: Int = 10
    ): JsonElement

    @GET("api/stats/top_queries")
    suspend fun getTopQueries(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Query("count") count: Int = 10
    ): JsonElement

    @GET("api/stats/top_blocked")
    suspend fun getTopBlocked(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Query("count") count: Int = 10
    ): JsonElement

    @GET("api/stats/top_ads")
    suspend fun getTopAds(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Query("count") count: Int = 10
    ): JsonElement

    @GET("api/stats/top_clients")
    suspend fun getTopClients(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Query("count") count: Int = 10
    ): JsonElement

    @GET("api/stats/top_sources")
    suspend fun getTopSources(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Query("count") count: Int = 10
    ): JsonElement

    @GET("api/history")
    suspend fun getQueryHistory(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null
    ): PiholeQueryHistory

    @GET("api/queries")
    suspend fun getQueries(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null,
        @Query("from") from: Long,
        @Query("until") until: Long
    ): JsonElement

    @GET("admin/api.php")
    suspend fun getQueriesLegacy(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("getAllQueriesRaw") getAllQueriesRaw: Int = 1,
        @Query("from") from: Long,
        @Query("until") until: Long,
        @Query("auth") auth: String? = null
    ): JsonElement

    @GET("api/stats/upstreams")
    suspend fun getUpstreams(
        @Header("X-Homelab-Service") service: String = "Pihole",
        @Query("auth") auth: String? = null
    ): PiholeUpstream
}
