package com.apkhub.app.data.remote

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class GitHubRepoDto(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("full_name") val fullName: String = "",
    val description: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    val owner: GitHubOwnerDto = GitHubOwnerDto(),
    val archived: Boolean = false
)

@Serializable
data class GitHubOwnerDto(
    val login: String = "",
    @SerialName("avatar_url") val avatarUrl: String? = null
)

@Serializable
data class GitHubReleaseDto(
    val id: Long = 0L,
    @SerialName("tag_name") val tagName: String = "",
    val name: String? = null,
    val body: String? = null,
    @SerialName("html_url") val htmlUrl: String = "",
    @SerialName("published_at") val publishedAt: String? = null,
    @SerialName("created_at") val createdAt: String? = null,
    val prerelease: Boolean = false,
    val draft: Boolean = false,
    val assets: List<GitHubAssetDto> = emptyList()
)

@Serializable
data class GitHubAssetDto(
    val id: Long = 0L,
    val name: String = "",
    @SerialName("browser_download_url") val browserDownloadUrl: String = "",
    val size: Long = 0L,
    @SerialName("content_type") val contentType: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)
