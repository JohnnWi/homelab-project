package com.homelab.app.data.repository

import android.util.Log
import com.homelab.app.data.remote.api.GiteaApi
import com.homelab.app.data.remote.dto.gitea.*
import okio.ByteString.Companion.encodeUtf8
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class GiteaRepository @Inject constructor(
    private val api: GiteaApi
) {
    suspend fun authenticate(url: String, username: String, password: String, allowSelfSigned: Boolean = false): String {
        val baseUrl = url.trimEnd('/')
        try {
            // 1. Try if 'password' is actually a token
            try {
                api.authenticateUser(
                    url = "$baseUrl/api/v1/user",
                    allowSelfSigned = allowSelfSigned.toString(),
                    authHeader = "token $password"
                )
                return password // It's a token, return as is
            } catch (e: Exception) {
                // Not a token or needs basic auth
            }

            val basicAuthRaw = "$username:$password"
            val basicAuthEncoded = "Basic ${basicAuthRaw.encodeUtf8().base64()}"
            
            // 2. Verify credentials against /user
            val user = api.authenticateUser(
                url = "$baseUrl/api/v1/user",
                allowSelfSigned = allowSelfSigned.toString(),
                authHeader = basicAuthEncoded
            )
            
            // 3. Try to generate a long-lived app token
            try {
                val tokenName = "homelab-${System.currentTimeMillis() / 1000}"
                val request = GiteaTokenRequest(
                    name = tokenName,
                    scopes = listOf("read:repository", "read:user", "read:issue", "read:notification")
                )
                val response = api.createToken(
                    url = "$baseUrl/api/v1/users/${user.login}/tokens",
                    allowSelfSigned = allowSelfSigned.toString(),
                    authHeader = basicAuthEncoded,
                    body = request
                )
                return response.sha1
            } catch (e: Exception) {
                Log.w("GiteaRepository", "Failed to create app token, falling back to basic auth: ${e.message}")
            }
            
            // 4. Fallback: store basic auth
            return "basic:${basicAuthRaw.encodeUtf8().base64()}"
        } catch (e: Exception) {
            Log.e("GiteaRepository", "Authentication failed", e)
            throw Exception("Autenticazione Gitea fallita. Controlla credenziali e URL.", e)
        }
    }

    suspend fun getCurrentUser(instanceId: String): GiteaUser = api.getCurrentUser(instanceId = instanceId)
    suspend fun getUserRepos(instanceId: String, page: Int = 1, limit: Int = 20): List<GiteaRepo> = api.getUserRepos(instanceId = instanceId, page = page, limit = limit)
    suspend fun getOrgs(instanceId: String): List<GiteaOrg> = api.getOrgs(instanceId = instanceId)
    suspend fun getNotifications(instanceId: String, limit: Int = 20): List<GiteaNotification> = api.getNotifications(instanceId = instanceId, limit = limit)
    suspend fun getUserHeatmap(instanceId: String, username: String): List<GiteaHeatmapItem> = api.getUserHeatmap(instanceId = instanceId, username = username)
    suspend fun getRepo(instanceId: String, owner: String, repo: String): GiteaRepo = api.getRepo(instanceId = instanceId, owner = owner, repo = repo)
    

    suspend fun getRepoContents(instanceId: String, owner: String, repo: String, path: String = "", ref: String? = null): List<GiteaFileContent> {
        return if (path.isEmpty()) {
            api.getRepoRootContents(instanceId = instanceId, owner = owner, repo = repo, ref = ref)
        } else {
            api.getRepoContents(instanceId = instanceId, owner = owner, repo = repo, path = path, ref = ref)
        }
    }

    suspend fun getFileContent(instanceId: String, owner: String, repo: String, path: String, ref: String? = null): GiteaFileContent {
        return api.getFileContent(instanceId = instanceId, owner = owner, repo = repo, path = path, ref = ref)
    }

    suspend fun getRepoCommits(instanceId: String, owner: String, repo: String, page: Int = 1, limit: Int = 20, ref: String? = null): List<GiteaCommit> {
        return api.getRepoCommits(instanceId = instanceId, owner = owner, repo = repo, page = page, limit = limit, ref = ref)
    }

    suspend fun getRepoIssues(instanceId: String, owner: String, repo: String, state: String = "open", page: Int = 1, limit: Int = 20): List<GiteaIssue> {
        return api.getRepoIssues(instanceId = instanceId, owner = owner, repo = repo, state = state, page = page, limit = limit)
    }

    suspend fun getRepoBranches(instanceId: String, owner: String, repo: String): List<GiteaBranch> = api.getRepoBranches(instanceId = instanceId, owner = owner, repo = repo)
    
    suspend fun getRepoReadme(instanceId: String, owner: String, repo: String, ref: String? = null): GiteaFileContent {
        return api.getFileContent(instanceId = instanceId, owner = owner, repo = repo, path = "README.md", ref = ref)
    }
}
