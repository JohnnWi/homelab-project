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
    suspend fun authenticate(url: String, username: String, password: String): String {
        val baseUrl = url.trimEnd('/')
        try {
            // 1. Try if 'password' is actually a token
            try {
                api.authenticateUser(url = "$baseUrl/api/v1/user", authHeader = "token $password")
                return password // It's a token, return as is
            } catch (e: Exception) {
                // Not a token or needs basic auth
            }

            val basicAuthRaw = "$username:$password"
            val basicAuthEncoded = "Basic ${basicAuthRaw.encodeUtf8().base64()}"
            
            // 2. Verify credentials against /user
            val user = api.authenticateUser(url = "$baseUrl/api/v1/user", authHeader = basicAuthEncoded)
            
            // 3. Try to generate a long-lived app token
            try {
                val tokenName = "homelab-${System.currentTimeMillis() / 1000}"
                val request = GiteaTokenRequest(
                    name = tokenName,
                    scopes = listOf("read:repository", "read:user", "read:issue", "read:notification")
                )
                val response = api.createToken(
                    url = "$baseUrl/api/v1/users/${user.login}/tokens",
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

    suspend fun getCurrentUser(): GiteaUser = api.getCurrentUser()
    suspend fun getUserRepos(page: Int = 1, limit: Int = 20): List<GiteaRepo> = api.getUserRepos(page = page, limit = limit)
    suspend fun getOrgs(): List<GiteaOrg> = api.getOrgs()
    suspend fun getNotifications(limit: Int = 20): List<GiteaNotification> = api.getNotifications(limit = limit)
    suspend fun getUserHeatmap(username: String): List<GiteaHeatmapItem> = api.getUserHeatmap(username = username)
    suspend fun getRepo(owner: String, repo: String): GiteaRepo = api.getRepo(owner = owner, repo = repo)
    

    suspend fun getRepoContents(owner: String, repo: String, path: String = "", ref: String? = null): List<GiteaFileContent> {
        return if (path.isEmpty()) {
            api.getRepoRootContents(owner = owner, repo = repo, ref = ref)
        } else {
            api.getRepoContents(owner = owner, repo = repo, path = path, ref = ref)
        }
    }

    suspend fun getFileContent(owner: String, repo: String, path: String, ref: String? = null): GiteaFileContent {
        return api.getFileContent(owner = owner, repo = repo, path = path, ref = ref)
    }

    suspend fun getRepoCommits(owner: String, repo: String, page: Int = 1, limit: Int = 20, ref: String? = null): List<GiteaCommit> {
        return api.getRepoCommits(owner = owner, repo = repo, page = page, limit = limit, ref = ref)
    }

    suspend fun getRepoIssues(owner: String, repo: String, state: String = "open", page: Int = 1, limit: Int = 20): List<GiteaIssue> {
        return api.getRepoIssues(owner = owner, repo = repo, state = state, page = page, limit = limit)
    }

    suspend fun getRepoBranches(owner: String, repo: String): List<GiteaBranch> = api.getRepoBranches(owner = owner, repo = repo)
    
    suspend fun getRepoReadme(owner: String, repo: String, ref: String? = null): GiteaFileContent {
        return api.getFileContent(owner = owner, repo = repo, path = "README.md", ref = ref)
    }
}
