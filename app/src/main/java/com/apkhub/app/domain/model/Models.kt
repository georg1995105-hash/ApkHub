package com.apkhub.app.domain.model

import kotlinx.serialization.Serializable

enum class ReleaseChannel(val label: String, val description: String) {
    STABLE("Stable", "Только стабильные релизы"),
    BETA("Beta", "Стабильные, beta и release candidate"),
    TEST("Test", "Все сборки, включая alpha, nightly и test");

    fun includes(releaseChannel: ReleaseChannel): Boolean = when (this) {
        STABLE -> releaseChannel == STABLE
        BETA -> releaseChannel == STABLE || releaseChannel == BETA
        TEST -> true
    }

    companion object {
        fun fromNameOrDefault(value: String?, default: ReleaseChannel = STABLE): ReleaseChannel {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: default
        }
    }
}

enum class AppThemeMode(val label: String) {
    SYSTEM("Системная"),
    LIGHT("Светлая"),
    DARK("Тёмная"),
    GRAPHITE("Графитовая");

    companion object {
        fun fromNameOrDefault(value: String?): AppThemeMode {
            return entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: GRAPHITE
        }
    }
}

enum class UpdateCheckInterval(val hours: Long, val label: String) {
    OFF(0L, "Отключено"),
    HOURS_6(6L, "Каждые 6 часов"),
    HOURS_12(12L, "Каждые 12 часов"),
    HOURS_24(24L, "Раз в день"),
    HOURS_72(72L, "Раз в 3 дня");

    companion object {
        fun fromHours(hours: Long): UpdateCheckInterval {
            return entries.firstOrNull { it.hours == hours } ?: HOURS_12
        }
    }
}

enum class DownloadStatus(val label: String) {
    QUEUED("В очереди"),
    DOWNLOADING("Загрузка"),
    VERIFYING("Проверка APK"),
    READY_TO_INSTALL("Готов к установке"),
    INSTALLING("Установка"),
    INSTALLED("Установлено"),
    FAILED("Ошибка"),
    CANCELLED("Отменено")
}

enum class HistoryEventType(val label: String) {
    DOWNLOAD_COMPLETED("Загрузка завершена"),
    DOWNLOAD_FAILED("Ошибка загрузки"),
    INSTALL_SUCCESS("Установлено"),
    INSTALL_CANCELLED("Установка отменена"),
    INSTALL_FAILED("Ошибка установки"),
    VERIFICATION_BLOCKED("Блокировка проверки"),
    UPDATE_FOUND("Найдено обновление")
}

data class GitHubRepoRef(
    val owner: String,
    val repo: String
) {
    val fullName: String get() = "$owner/$repo"
    val htmlUrl: String get() = "https://github.com/$owner/$repo"
}

@Serializable
data class ReleaseAsset(
    val id: Long,
    val name: String,
    val downloadUrl: String,
    val sizeBytes: Long,
    val contentType: String = "",
    val updatedAt: String = ""
)

data class ReleaseInfo(
    val id: Long,
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val isDraft: Boolean,
    val channel: ReleaseChannel,
    val assets: List<ReleaseAsset>
)

data class DeviceProfile(
    val supportedAbis: List<String>,
    val sdkInt: Int,
    val isAndroidTv: Boolean
)

enum class UnsupportedPackageFormat(val extension: String, val displayName: String) {
    XAPK(".xapk", "XAPK"),
    APKS(".apks", "APKS"),
    AAB(".aab", "Android App Bundle (AAB)"),
    APKM(".apkm", "APKM");

    companion object {
        fun detect(fileName: String): UnsupportedPackageFormat? {
            val lower = fileName.lowercase()
            return entries.firstOrNull { lower.endsWith(it.extension) }
        }
    }
}

data class AssetSelectionResult(
    val recommended: ReleaseAsset?,
    val compatibleCandidates: List<ScoredAsset>,
    val unsupportedFormatsFound: List<UnsupportedPackageFormat>,
    val reason: String
)

data class ScoredAsset(
    val asset: ReleaseAsset,
    val score: Int,
    val matchedAbi: String?,
    val notes: String
)

data class InstalledPackageInfo(
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val signingCertSha256: List<String>,
    val appLabel: String
)

data class RateLimitInfo(
    val limit: Int = 60,
    val remaining: Int = 60,
    val resetEpochSeconds: Long = 0L,
    val isRateLimited: Boolean = false
)

data class TrackedAppUiModel(
    val id: Long,
    val owner: String,
    val repo: String,
    val displayName: String,
    val description: String,
    val avatarUrl: String?,
    val packageName: String?,
    val channel: ReleaseChannel,
    val installedVersionName: String?,
    val installedVersionCode: Long?,
    val latestTag: String?,
    val latestReleaseName: String?,
    val latestPublishedAt: String?,
    val latestChangelog: String?,
    val latestAssetName: String?,
    val latestAssetUrl: String?,
    val latestAssetSizeBytes: Long,
    val latestAssetAbiNote: String?,
    val unsupportedFormatHint: String?,
    val hasUpdate: Boolean,
    val lastCheckedAt: Long,
    val lastError: String?,
    val isSelfApp: Boolean
) {
    val fullName: String get() = "$owner/$repo"
    val isInstalled: Boolean get() = !packageName.isNullOrBlank() && installedVersionName != null
}

data class SelfUpdateState(
    val repoFullName: String,
    val currentVersionName: String,
    val currentVersionCode: Long,
    val latestTag: String? = null,
    val latestChangelog: String? = null,
    val asset: ReleaseAsset? = null,
    val hasUpdate: Boolean = false,
    val isChecking: Boolean = false,
    val lastError: String? = null
)

data class UserSettings(
    val defaultChannel: ReleaseChannel = ReleaseChannel.STABLE,
    val checkInterval: UpdateCheckInterval = UpdateCheckInterval.HOURS_12,
    val wifiOnlyUpdates: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.GRAPHITE,
    val githubToken: String = "",
    val selfUpdateRepo: String = "",
    val autoDeleteApkAfterInstall: Boolean = true
)
