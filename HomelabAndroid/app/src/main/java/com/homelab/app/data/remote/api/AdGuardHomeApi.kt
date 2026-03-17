package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.adguard.AdGuardBlockedServicesAll
import com.homelab.app.data.remote.dto.adguard.AdGuardBlockedServicesSchedule
import com.homelab.app.data.remote.dto.adguard.AdGuardFilterAddRequest
import com.homelab.app.data.remote.dto.adguard.AdGuardFilterRemoveRequest
import com.homelab.app.data.remote.dto.adguard.AdGuardFilterSetUrlRequest
import com.homelab.app.data.remote.dto.adguard.AdGuardFilteringStatus
import com.homelab.app.data.remote.dto.adguard.AdGuardProtectionRequest
import com.homelab.app.data.remote.dto.adguard.AdGuardQueryLogResponse
import com.homelab.app.data.remote.dto.adguard.AdGuardRewriteEntry
import com.homelab.app.data.remote.dto.adguard.AdGuardRewriteSettings
import com.homelab.app.data.remote.dto.adguard.AdGuardRewriteUpdate
import com.homelab.app.data.remote.dto.adguard.AdGuardSetRulesRequest
import com.homelab.app.data.remote.dto.adguard.AdGuardStats
import com.homelab.app.data.remote.dto.adguard.AdGuardStatus
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Query
import retrofit2.http.Url

interface AdGuardHomeApi {
    @GET
    suspend fun authenticate(
        @Url url: String,
        @Header("Authorization") authorization: String,
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Bypass") bypass: String = "true"
    ): AdGuardStatus

    @GET("control/status")
    suspend fun getStatus(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): AdGuardStatus

    @GET("control/stats")
    suspend fun getStats(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("interval") intervalMs: Long? = null
    ): AdGuardStats

    @POST("control/protection")
    suspend fun setProtection(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardProtectionRequest
    )

    @GET("control/querylog")
    suspend fun getQueryLog(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("limit") limit: Int = 200,
        @Query("search") search: String? = null,
        @Query("response_status") responseStatus: String? = null,
        @Query("offset") offset: Int? = null
    ): AdGuardQueryLogResponse

    @GET("control/filtering/status")
    suspend fun getFilteringStatus(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): AdGuardFilteringStatus

    @POST("control/filtering/set_rules")
    suspend fun setUserRules(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardSetRulesRequest
    )

    @POST("control/filtering/add_url")
    suspend fun addFilter(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardFilterAddRequest
    )

    @POST("control/filtering/remove_url")
    suspend fun removeFilter(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardFilterRemoveRequest
    )

    @POST("control/filtering/set_url")
    suspend fun setFilter(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardFilterSetUrlRequest
    )

    @GET("control/blocked_services/all")
    suspend fun getBlockedServicesAll(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): AdGuardBlockedServicesAll

    @GET("control/blocked_services/get")
    suspend fun getBlockedServicesSchedule(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): AdGuardBlockedServicesSchedule

    @PUT("control/blocked_services/update")
    suspend fun updateBlockedServices(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardBlockedServicesSchedule
    )

    @GET("control/rewrite/list")
    suspend fun getRewrites(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<AdGuardRewriteEntry>

    @POST("control/rewrite/add")
    suspend fun addRewrite(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardRewriteEntry
    )

    @POST("control/rewrite/delete")
    suspend fun deleteRewrite(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardRewriteEntry
    )

    @POST("control/rewrite/update")
    suspend fun updateRewrite(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardRewriteUpdate
    )

    @GET("control/rewrite/settings")
    suspend fun getRewriteSettings(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): AdGuardRewriteSettings

    @POST("control/rewrite/settings/update")
    suspend fun updateRewriteSettings(
        @Header("X-Homelab-Service") service: String = "AdGuardHome",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: AdGuardRewriteSettings
    )
}
