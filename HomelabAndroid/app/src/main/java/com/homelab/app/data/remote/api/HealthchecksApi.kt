package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.healthchecks.*
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface HealthchecksApi {

    @GET("api/v3/checks/")
    suspend fun listChecks(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("slug") slug: String? = null,
        @Query("tag") tag: String? = null
    ): HealthchecksChecksResponse

    @GET("api/v3/checks/{id}")
    suspend fun getCheck(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String
    ): HealthchecksCheck

    @POST("api/v3/checks/")
    suspend fun createCheck(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body payload: HealthchecksCheckPayload
    )

    @POST("api/v3/checks/{id}")
    suspend fun updateCheck(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String,
        @Body payload: HealthchecksCheckPayload
    )

    @POST("api/v3/checks/{id}/pause")
    suspend fun pauseCheck(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String
    )

    @POST("api/v3/checks/{id}/resume")
    suspend fun resumeCheck(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String
    )

    @DELETE("api/v3/checks/{id}")
    suspend fun deleteCheck(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String
    )

    @GET("api/v3/checks/{id}/pings/")
    suspend fun listPings(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String
    ): HealthchecksPingResponse

    @GET("api/v3/checks/{id}/pings/{n}/body")
    suspend fun getPingBody(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String,
        @Path("n") n: Int
    ): String

    @GET("api/v3/checks/{id}/flips/")
    suspend fun listFlips(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("id") id: String
    ): HealthchecksFlipsResponse

    @GET("api/v3/channels/")
    suspend fun listChannels(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): HealthchecksChannelsResponse

    @GET("api/v3/badges/")
    suspend fun listBadges(
        @Header("X-Homelab-Service") service: String = "Healthchecks",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): HealthchecksBadgesResponse
}
