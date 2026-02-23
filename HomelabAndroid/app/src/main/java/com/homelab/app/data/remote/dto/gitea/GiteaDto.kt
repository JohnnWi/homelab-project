package com.homelab.app.data.remote.dto.gitea

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.ByteString.Companion.decodeBase64

@Serializable
data class GiteaUser(
    val id: Int,
    val login: String,
    val full_name: String = "",
    val email: String = "",
    val avatar_url: String = "",
    val created: String = ""
)

@Serializable
data class GiteaRepo(
    val id: Int,
    val name: String,
    val full_name: String = "",
    val description: String = "",
    val owner: GiteaRepoOwner,
    @SerialName("private") val isPrivate: Boolean = false,
    val fork: Boolean = false,
    val stars_count: Int = 0,
    val forks_count: Int = 0,
    val open_issues_count: Int = 0,
    val open_pr_counter: Int = 0,
    val language: String? = null,
    val size: Int = 0,
    val updated_at: String = "",
    val created_at: String = "",
    val html_url: String = "",
    val default_branch: String = ""
)

@Serializable
data class GiteaRepoOwner(
    val login: String,
    val avatar_url: String = ""
)

@Serializable
data class GiteaOrg(
    val id: Int,
    val username: String,
    val full_name: String = "",
    val avatar_url: String = "",
    val description: String = ""
)

@Serializable
data class GiteaNotification(
    val id: Int,
    val subject: GiteaNotificationSubject,
    val repository: GiteaNotificationRepo,
    val unread: Boolean = false,
    val updated_at: String = ""
)

@Serializable
data class GiteaNotificationSubject(
    val title: String,
    val type: String = "",
    val url: String = ""
)

@Serializable
data class GiteaNotificationRepo(
    val full_name: String
)

@Serializable
data class GiteaFileContent(
    val name: String,
    val path: String,
    val sha: String,
    val type: String, // file, dir, symlink, submodule
    val size: Int = 0,
    val content: String? = null,
    val encoding: String? = null,
    val url: String = "",
    val html_url: String = "",
    val download_url: String? = null
) {
    val id: String get() = sha + path

    val isDirectory: Boolean get() = type == "dir"
    val isFile: Boolean get() = type == "file"

    val decodedContent: String? get() {
        if (content != null && encoding == "base64") {
            val cleaned = content.replace("\n", "").replace("\r", "")
            return cleaned.decodeBase64()?.utf8()
        }
        return content
    }

    val fileExtension: String get() {
        return name.substringAfterLast('.', "").lowercase()
    }

    val isImage: Boolean get() {
        return listOf("png", "jpg", "jpeg", "gif", "webp", "svg", "ico", "bmp").contains(fileExtension)
    }

    val isMarkdown: Boolean get() {
        return listOf("md", "markdown").contains(fileExtension)
    }
}

@Serializable
data class GiteaCommit(
    val sha: String,
    val url: String = "",
    val html_url: String = "",
    val commit: GiteaCommitData,
    val author: GiteaCommitAuthorUser? = null
) {
    val id: String get() = sha
}

@Serializable
data class GiteaCommitData(
    val message: String,
    val author: GiteaCommitPersonInfo? = null,
    val committer: GiteaCommitPersonInfo? = null
)

@Serializable
data class GiteaCommitPersonInfo(
    val name: String,
    val email: String = "",
    val date: String = ""
)

@Serializable
data class GiteaCommitAuthorUser(
    val login: String,
    val avatar_url: String = ""
)

@Serializable
data class GiteaIssue(
    val id: Int,
    val number: Int,
    val title: String,
    val body: String = "",
    val state: String = "",
    val user: GiteaIssueUser? = null,
    val labels: List<GiteaLabel> = emptyList(),
    val comments: Int = 0,
    val created_at: String = "",
    val updated_at: String = "",
    val closed_at: String? = null,
    val pull_request: GiteaPullRequest? = null
) {
    val isOpen: Boolean get() = state == "open"
    val isPR: Boolean get() = pull_request != null
}

@Serializable
data class GiteaIssueUser(
    val login: String,
    val avatar_url: String = ""
)

@Serializable
data class GiteaLabel(
    val id: Int,
    val name: String,
    val color: String = ""
)

@Serializable
data class GiteaPullRequest(
    val merged: Boolean? = null,
    val merged_at: String? = null
)

@Serializable
data class GiteaBranch(
    val name: String,
    val commit: GiteaBranchCommit,
    val protected: Boolean = false
) {
    val id: String get() = name
}

@Serializable
data class GiteaBranchCommit(
    val id: String,
    val message: String = ""
)

@Serializable
data class GiteaHeatmapItem(
    val timestamp: Long,
    val contributions: Int
) {
    val id: Long get() = timestamp
}

@Serializable
data class GiteaTokenRequest(
    val name: String,
    val scopes: List<String>
)

@Serializable
data class GiteaTokenResponse(
    val id: Int,
    val name: String,
    val sha1: String
)

@Serializable
data class GiteaServerVersion(
    val version: String
)
