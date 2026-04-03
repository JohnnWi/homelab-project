package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.pangolin.PangolinDomainsResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinEnvelope
import com.homelab.app.data.remote.dto.pangolin.PangolinOrgsResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinResource
import com.homelab.app.data.remote.dto.pangolin.PangolinResourcesResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResource
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResourcesResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinSitesResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinClientsResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResourceClientsResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResourceRolesResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinSiteResourceUsersResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinTarget
import com.homelab.app.data.remote.dto.pangolin.PangolinTargetsResponse
import com.homelab.app.data.remote.dto.pangolin.PangolinUserDevicesResponse
import kotlinx.serialization.json.JsonObject
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.PUT
import retrofit2.http.Query

interface PangolinApi {

    @GET("v1/orgs")
    suspend fun listOrgs(
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0
    ): PangolinOrgsResponse

    @GET("v1/org/{orgId}/sites")
    suspend fun listSites(
        @Path("orgId") orgId: String,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("page") page: Int = 1
    ): PangolinSitesResponse

    @GET("v1/org/{orgId}/site-resources")
    suspend fun listSiteResources(
        @Path("orgId") orgId: String,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("page") page: Int = 1
    ): PangolinSiteResourcesResponse

    @GET("v1/org/{orgId}/resources")
    suspend fun listResources(
        @Path("orgId") orgId: String,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("page") page: Int = 1
    ): PangolinResourcesResponse

    @GET("v1/org/{orgId}/clients")
    suspend fun listClients(
        @Path("orgId") orgId: String,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("page") page: Int = 1,
        @Query("status") status: String = "active,blocked,archived"
    ): PangolinClientsResponse

    @GET("v1/org/{orgId}/user-devices")
    suspend fun listUserDevices(
        @Path("orgId") orgId: String,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("pageSize") pageSize: Int = 100,
        @Query("page") page: Int = 1,
        @Query("status") status: String = "active,pending,denied,blocked,archived"
    ): PangolinUserDevicesResponse

    @GET("v1/org/{orgId}/domains")
    suspend fun listDomains(
        @Path("orgId") orgId: String,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0
    ): PangolinDomainsResponse

    @GET("v1/resource/{resourceId}/targets")
    suspend fun listTargets(
        @Path("resourceId") resourceId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("limit") limit: Int = 1000,
        @Query("offset") offset: Int = 0
    ): PangolinTargetsResponse

    @GET("v1/site-resource/{siteResourceId}/users")
    suspend fun listSiteResourceUsers(
        @Path("siteResourceId") siteResourceId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): PangolinSiteResourceUsersResponse

    @GET("v1/site-resource/{siteResourceId}/roles")
    suspend fun listSiteResourceRoles(
        @Path("siteResourceId") siteResourceId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): PangolinSiteResourceRolesResponse

    @GET("v1/site-resource/{siteResourceId}/clients")
    suspend fun listSiteResourceClients(
        @Path("siteResourceId") siteResourceId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): PangolinSiteResourceClientsResponse

    @POST("v1/org/{orgId}/site-resources")
    suspend fun createSiteResource(
        @Path("orgId") orgId: String,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: JsonObject
    ): PangolinEnvelope<PangolinSiteResource>

    @POST("v1/resource/{resourceId}")
    suspend fun updateResource(
        @Path("resourceId") resourceId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: JsonObject
    ): PangolinEnvelope<PangolinResource>

    @PUT("v1/org/{orgId}/resource")
    suspend fun createResource(
        @Path("orgId") orgId: String,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: JsonObject
    ): PangolinEnvelope<PangolinResource>

    @POST("v1/target/{targetId}")
    suspend fun updateTarget(
        @Path("targetId") targetId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: JsonObject
    ): PangolinEnvelope<PangolinTarget>

    @PUT("v1/resource/{resourceId}/target")
    suspend fun createTarget(
        @Path("resourceId") resourceId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: JsonObject
    ): PangolinEnvelope<PangolinTarget>

    @POST("v1/site-resource/{siteResourceId}")
    suspend fun updateSiteResource(
        @Path("siteResourceId") siteResourceId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body body: JsonObject
    ): PangolinEnvelope<PangolinSiteResource>

    @DELETE("v1/resource/{resourceId}")
    suspend fun deleteResource(
        @Path("resourceId") resourceId: Int,
        @Header("X-Homelab-Service") service: String = "Pangolin",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): PangolinEnvelope<JsonObject?>
}
