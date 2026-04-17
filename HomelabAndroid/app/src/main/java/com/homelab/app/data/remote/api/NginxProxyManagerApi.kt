package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.nginxpm.NpmAccessList
import com.homelab.app.data.remote.dto.nginxpm.NpmAccessListRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmAuditLog
import com.homelab.app.data.remote.dto.nginxpm.NpmCertificate
import com.homelab.app.data.remote.dto.nginxpm.NpmCertificateRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmDeadHost
import com.homelab.app.data.remote.dto.nginxpm.NpmDeadHostRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmHealthResponse
import com.homelab.app.data.remote.dto.nginxpm.NpmHostReport
import com.homelab.app.data.remote.dto.nginxpm.NpmProxyHost
import com.homelab.app.data.remote.dto.nginxpm.NpmProxyHostRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmRedirectionHost
import com.homelab.app.data.remote.dto.nginxpm.NpmRedirectionHostRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmSetting
import com.homelab.app.data.remote.dto.nginxpm.NpmStream
import com.homelab.app.data.remote.dto.nginxpm.NpmStreamRequest
import com.homelab.app.data.remote.dto.nginxpm.NpmTokenResult
import com.homelab.app.data.remote.dto.nginxpm.NpmUser
import com.homelab.app.data.remote.dto.nginxpm.NpmUserRequest
import retrofit2.http.*

interface NginxProxyManagerApi {

    @POST
    suspend fun authenticate(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false",
        @Body credentials: Map<String, String>
    ): retrofit2.Response<NpmTokenResult>

    @GET("api/")
    suspend fun health(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): NpmHealthResponse

    @GET("api/reports/hosts")
    suspend fun getHostReport(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): NpmHostReport

    @GET("api/nginx/proxy-hosts?expand=certificate")
    suspend fun getProxyHosts(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmProxyHost>

    @POST("api/nginx/proxy-hosts")
    suspend fun createProxyHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: NpmProxyHostRequest
    ): NpmProxyHost

    @PUT("api/nginx/proxy-hosts/{hostId}")
    suspend fun updateProxyHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("hostId") hostId: Int,
        @Body request: NpmProxyHostRequest
    ): NpmProxyHost

    @DELETE("api/nginx/proxy-hosts/{hostId}")
    suspend fun deleteProxyHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("hostId") hostId: Int
    )

    @POST("api/nginx/proxy-hosts/{hostId}/enable")
    suspend fun enableProxyHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("hostId") hostId: Int
    )

    @POST("api/nginx/proxy-hosts/{hostId}/disable")
    suspend fun disableProxyHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("hostId") hostId: Int
    )

    @GET("api/nginx/redirection-hosts?expand=certificate")
    suspend fun getRedirectionHosts(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmRedirectionHost>

    @POST("api/nginx/redirection-hosts")
    suspend fun createRedirectionHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: NpmRedirectionHostRequest
    ): NpmRedirectionHost

    @PUT("api/nginx/redirection-hosts/{hostId}")
    suspend fun updateRedirectionHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("hostId") hostId: Int,
        @Body request: NpmRedirectionHostRequest
    ): NpmRedirectionHost

    @DELETE("api/nginx/redirection-hosts/{hostId}")
    suspend fun deleteRedirectionHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("hostId") hostId: Int
    )

    @GET("api/nginx/streams")
    suspend fun getStreams(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmStream>

    @POST("api/nginx/streams")
    suspend fun createStream(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: NpmStreamRequest
    ): NpmStream

    @PUT("api/nginx/streams/{streamId}")
    suspend fun updateStream(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("streamId") streamId: Int,
        @Body request: NpmStreamRequest
    ): NpmStream

    @DELETE("api/nginx/streams/{streamId}")
    suspend fun deleteStream(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("streamId") streamId: Int
    )

    @GET("api/nginx/dead-hosts")
    suspend fun getDeadHosts(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmDeadHost>

    @POST("api/nginx/dead-hosts")
    suspend fun createDeadHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: NpmDeadHostRequest
    ): NpmDeadHost

    @PUT("api/nginx/dead-hosts/{hostId}")
    suspend fun updateDeadHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("hostId") hostId: Int,
        @Body request: NpmDeadHostRequest
    ): NpmDeadHost

    @DELETE("api/nginx/dead-hosts/{hostId}")
    suspend fun deleteDeadHost(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("hostId") hostId: Int
    )

    @GET("api/nginx/certificates")
    suspend fun getCertificates(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmCertificate>

    @POST("api/nginx/certificates")
    suspend fun createCertificate(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: NpmCertificateRequest
    ): NpmCertificate

    @DELETE("api/nginx/certificates/{certId}")
    suspend fun deleteCertificate(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("certId") certId: Int
    )

    @POST("api/nginx/certificates/{certId}/renew")
    suspend fun renewCertificate(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("certId") certId: Int
    ): NpmCertificate

    @GET("api/nginx/access-lists?expand=items,clients")
    suspend fun getAccessLists(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmAccessList>

    @POST("api/nginx/access-lists")
    suspend fun createAccessList(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: NpmAccessListRequest
    ): NpmAccessList

    @PUT("api/nginx/access-lists/{listId}")
    suspend fun updateAccessList(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("listId") listId: Int,
        @Body request: NpmAccessListRequest
    ): NpmAccessList

    @DELETE("api/nginx/access-lists/{listId}")
    suspend fun deleteAccessList(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("listId") listId: Int
    )

    @GET("api/users")
    suspend fun getUsers(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmUser>

    @POST("api/users")
    suspend fun createUser(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Body request: NpmUserRequest
    ): NpmUser

    @PUT("api/users/{userId}")
    suspend fun updateUser(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("userId") userId: Int,
        @Body request: NpmUserRequest
    ): NpmUser

    @DELETE("api/users/{userId}")
    suspend fun deleteUser(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("userId") userId: Int
    )

    @GET("api/audit-log")
    suspend fun getAuditLogs(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmAuditLog>

    @GET("api/settings")
    suspend fun getSettings(
        @Header("X-Homelab-Service") service: String = "NginxProxyManager",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<NpmSetting>
}
