package com.homelab.app.data.remote.api

import com.homelab.app.data.remote.dto.gitea.*
import retrofit2.http.*

interface GiteaApi {
    @GET("api/v1/version")
    suspend fun getVersion(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): GiteaServerVersion

    @GET("api/v1/user")
    suspend fun getCurrentUser(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): GiteaUser

    @GET
    suspend fun authenticateUser(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false",
        @Header("Authorization") authHeader: String
    ): GiteaUser

    @POST
    suspend fun createToken(
        @Url url: String,
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Bypass") bypass: String = "true",
        @Header("X-Homelab-Allow-Self-Signed") allowSelfSigned: String = "false",
        @Header("Authorization") authHeader: String,
        @Body body: GiteaTokenRequest
    ): GiteaTokenResponse

    @GET("api/v1/user/repos")
    suspend fun getUserRepos(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("sort") sort: String = "updated"
    ): List<GiteaRepo>

    @GET("api/v1/user/orgs")
    suspend fun getOrgs(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String
    ): List<GiteaOrg>

    @GET("api/v1/notifications")
    suspend fun getNotifications(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Query("limit") limit: Int = 20
    ): List<GiteaNotification>

    @GET("api/v1/users/{username}/heatmap")
    suspend fun getUserHeatmap(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("username") username: String
    ): List<GiteaHeatmapItem>

    @GET("api/v1/repos/{owner}/{repo}")
    suspend fun getRepo(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): GiteaRepo



    @GET("api/v1/repos/{owner}/{repo}/contents/{path}")
    suspend fun getRepoContents(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String = "",
        @Query("ref") ref: String? = null
    ): List<GiteaFileContent>

    @GET("api/v1/repos/{owner}/{repo}/contents")
    suspend fun getRepoRootContents(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("ref") ref: String? = null
    ): List<GiteaFileContent>

    @GET("api/v1/repos/{owner}/{repo}/contents/{path}")
    suspend fun getFileContent(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Path(value = "path", encoded = true) path: String,
        @Query("ref") ref: String? = null
    ): GiteaFileContent

    @GET("api/v1/repos/{owner}/{repo}/commits")
    suspend fun getRepoCommits(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20,
        @Query("sha") ref: String? = null
    ): List<GiteaCommit>

    @GET("api/v1/repos/{owner}/{repo}/issues")
    suspend fun getRepoIssues(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String,
        @Query("state") state: String = "open",
        @Query("type") type: String = "issues",
        @Query("page") page: Int = 1,
        @Query("limit") limit: Int = 20
    ): List<GiteaIssue>

    @GET("api/v1/repos/{owner}/{repo}/branches")
    suspend fun getRepoBranches(
        @Header("X-Homelab-Service") service: String = "Gitea",
        @Header("X-Homelab-Instance-Id") instanceId: String,
        @Path("owner") owner: String,
        @Path("repo") repo: String
    ): List<GiteaBranch>
}
