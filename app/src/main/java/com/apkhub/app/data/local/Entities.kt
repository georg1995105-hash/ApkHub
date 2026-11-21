package com.apkhub.app.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "tracked_apps",
    indices = [
        Index(value = ["owner", "repo"], unique = true),
        Index(value = ["packageName"])
    ]
)
data class TrackedAppEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val owner: String,
    val repo: String,
    val displayName: String,
    val description: String = "",
    val avatarUrl: String? = null,
    val packageName: String? = null,
    val channel: String = "STABLE",
    val etag: String? = null,
    val installedVersionName: String? = null,
    val installedVersionCode: Long? = null,
    val latestReleaseId: Long? = null,
    val latestTag: String? = null,
    val latestReleaseName: String? = null,
    val latestPublishedAt: String? = null,
    val latestChangelog: String? = null,
    val latestAssetName: String? = null,
    val latestAssetUrl: String? = null,
    val latestAssetSizeBytes: Long = 0L,
    val latestAssetAbiNote: String? = null,
    val unsupportedFormatHint: String? = null,
    val hasUpdate: Boolean = false,
    val lastCheckedAt: Long = 0L,
    val lastError: String? = null,
    val isSelfApp: Boolean = false,
    val addedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "cached_releases",
    foreignKeys = [
        ForeignKey(
            entity = TrackedAppEntity::class,
            parentColumns = ["id"],
            childColumns = ["appId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["appId"])]
)
data class CachedReleaseEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0L,
    val appId: Long,
    val releaseId: Long,
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val channel: String,
    val assetsJson: String
)

@Entity(
    tableName = "download_tasks",
    indices = [Index(value = ["appId"]), Index(value = ["createdAt"])]
)
data class DownloadTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val appId: Long,
    val appName: String,
    val repoFullName: String,
    val versionTag: String,
    val assetName: String,
    val downloadUrl: String,
    val expectedSizeBytes: Long,
    val expectedSha256: String? = null,
    val downloadedBytes: Long = 0L,
    val status: String = "QUEUED",
    val localFilePath: String? = null,
    val computedSha256: String? = null,
    val verifiedPackageName: String? = null,
    val verifiedVersionName: String? = null,
    val verifiedVersionCode: Long? = null,
    val verificationNote: String? = null,
    val errorMessage: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

@Entity(
    tableName = "activity_history",
    indices = [Index(value = ["timestamp"]), Index(value = ["appId"])]
)
data class ActivityHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val appId: Long?,
    val appName: String,
    val repoFullName: String,
    val packageName: String?,
    val versionTag: String?,
    val eventType: String,
    val message: String,
    val sha256: String? = null,
    val timestamp: Long = System.currentTimeMillis()
)
