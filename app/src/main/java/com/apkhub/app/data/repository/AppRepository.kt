package com.apkhub.app.data.repository

import com.apkhub.app.BuildConfig
import com.apkhub.app.data.local.ActivityHistoryDao
import com.apkhub.app.data.local.ActivityHistoryEntity
import com.apkhub.app.data.local.CachedReleaseDao
import com.apkhub.app.data.local.CachedReleaseEntity
import com.apkhub.app.data.local.DownloadTaskDao
import com.apkhub.app.data.local.DownloadTaskEntity
import com.apkhub.app.data.local.SettingsDataStore
import com.apkhub.app.data.local.TrackedAppDao
import com.apkhub.app.data.local.TrackedAppEntity
import com.apkhub.app.data.remote.ApiErrorKind
import com.apkhub.app.data.remote.GitHubApiClient
import com.apkhub.app.data.remote.GitHubResult
import com.apkhub.app.data.system.ApkDownloadManager
import com.apkhub.app.data.system.ApkMetadataVerifier
import com.apkhub.app.data.system.DeviceInfoProvider
import com.apkhub.app.data.system.DownloadOutcome
import com.apkhub.app.data.system.InstallResultEvent
import com.apkhub.app.data.system.InstallTriggerResult
import com.apkhub.app.data.system.InstalledAppsInspector
import com.apkhub.app.data.system.PackageInstallerController
import com.apkhub.app.domain.logic.ApkAssetSelector
import com.apkhub.app.domain.logic.ChecksumExtractor
import com.apkhub.app.domain.logic.GitHubUrlParser
import com.apkhub.app.domain.logic.VersionComparator
import com.apkhub.app.domain.model.DownloadStatus
import com.apkhub.app.domain.model.HistoryEventType
import com.apkhub.app.domain.model.RateLimitInfo
import com.apkhub.app.domain.model.ReleaseAsset
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.domain.model.ReleaseInfo
import com.apkhub.app.domain.model.SelfUpdateState
import com.apkhub.app.domain.model.TrackedAppUiModel
import com.apkhub.app.domain.model.VerificationOutcome
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

data class RepoPreview(
    val owner: String,
    val repo: String,
    val displayName: String,
    val description: String,
    val avatarUrl: String?,
    val releaseCount: Int,
    val latestTag: String?,
    val recommendedApkName: String?,
    val recommendedApkSize: Long,
    val selectionNote: String
)

data class UpdateSummary(
    val checkedCount: Int,
    val updatesFound: List<TrackedAppUiModel>,
    val errorsCount: Int,
    val rateLimited: Boolean
)

class AppRepository(
    private val trackedAppDao: TrackedAppDao,
    private val cachedReleaseDao: CachedReleaseDao,
    private val downloadTaskDao: DownloadTaskDao,
    private val activityHistoryDao: ActivityHistoryDao,
    private val settingsDataStore: SettingsDataStore,
    private val gitHubApiClient: GitHubApiClient,
    private val deviceInfoProvider: DeviceInfoProvider,
    private val installedAppsInspector: InstalledAppsInspector,
    private val apkDownloadManager: ApkDownloadManager,
    private val apkMetadataVerifier: ApkMetadataVerifier,
    private val packageInstallerController: PackageInstallerController
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    val trackedAppsFlow: Flow<List<TrackedAppUiModel>> = trackedAppDao.observeAll().map { list ->
        list.map { it.toUiModel() }
    }

    val downloadTasksFlow: Flow<List<DownloadTaskEntity>> = downloadTaskDao.observeAll()

    val activityHistoryFlow: Flow<List<ActivityHistoryEntity>> = activityHistoryDao.observeRecent()

    fun observeReleasesForApp(appId: Long): Flow<List<ReleaseInfo>> {
        return cachedReleaseDao.observeByAppId(appId).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    fun observeHistoryForApp(appId: Long): Flow<List<ActivityHistoryEntity>> {
        return activityHistoryDao.observeForApp(appId)
    }

    suspend fun previewRepository(rawUrl: String, channel: ReleaseChannel): Result<RepoPreview> {
        val ref = GitHubUrlParser.parse(rawUrl)
            ?: return Result.failure(
                IllegalArgumentException("Некорректная ссылка GitHub. Укажите формат owner/repo или https://github.com/owner/repo.")
            )

        val token = settingsDataStore.getSettingsSnapshot().githubToken.takeIf { it.isNotBlank() }

        val repoResult = gitHubApiClient.fetchRepository(ref.owner, ref.repo, token)
        val repoDto = when (repoResult) {
            is GitHubResult.Success -> {
                repoResult.rateLimit?.let { persistRateLimit(it) }
                repoResult.data
            }
            is GitHubResult.NotModified -> {
                return Result.failure(IllegalStateException("Неожиданный статус 304 при предпросмотре."))
            }
            is GitHubResult.Error -> {
                repoResult.rateLimit?.let { persistRateLimit(it) }
                return Result.failure(IllegalStateException(repoResult.message))
            }
        }

        val releasesResult = gitHubApiClient.fetchReleases(ref.owner, ref.repo, token)
        val releases = when (releasesResult) {
            is GitHubResult.Success -> {
                releasesResult.rateLimit?.let { persistRateLimit(it) }
                releasesResult.data
            }
            is GitHubResult.NotModified -> emptyList()
            is GitHubResult.Error -> {
                releasesResult.rateLimit?.let { persistRateLimit(it) }
                return Result.failure(IllegalStateException(releasesResult.message))
            }
        }

        val bestRelease = pickLatestReleaseForChannel(releases, channel)
        val device = deviceInfoProvider.getDeviceProfile()
        val selection = bestRelease?.let { ApkAssetSelector.selectBestApk(it.assets, device) }

        return Result.success(
            RepoPreview(
                owner = repoDto.owner.login.ifBlank { ref.owner },
                repo = repoDto.name.ifBlank { ref.repo },
                displayName = repoDto.name.ifBlank { ref.repo },
                description = repoDto.description.orEmpty(),
                avatarUrl = repoDto.owner.avatarUrl,
                releaseCount = releases.size,
                latestTag = bestRelease?.tagName,
                recommendedApkName = selection?.recommended?.name,
                recommendedApkSize = selection?.recommended?.sizeBytes ?: 0L,
                selectionNote = selection?.reason
                    ?: if (releases.isEmpty()) "В репозитории пока нет опубликованных релизов."
                    else "Нет релизов в канале ${channel.label}."
            )
        )
    }

    suspend fun addRepository(
        rawUrl: String,
        channel: ReleaseChannel,
        customDisplayName: String? = null,
        manualPackageName: String? = null
    ): Result<TrackedAppUiModel> {
        val ref = GitHubUrlParser.parse(rawUrl)
            ?: return Result.failure(
                IllegalArgumentException("Некорректная ссылка GitHub. Используйте owner/repo или https://github.com/owner/repo.")
            )

        val existing = trackedAppDao.findByRepo(ref.owner, ref.repo)
        if (existing != null) {
            return Result.failure(
                IllegalStateException("Репозиторий ${existing.owner}/${existing.repo} уже добавлен.")
            )
        }

        val token = settingsDataStore.getSettingsSnapshot().githubToken.takeIf { it.isNotBlank() }
        val repoResult = gitHubApiClient.fetchRepository(ref.owner, ref.repo, token)
        val (owner, repo, desc, avatarUrl) = when (repoResult) {
            is GitHubResult.Success -> {
                repoResult.rateLimit?.let { persistRateLimit(it) }
                Quad(
                    repoResult.data.owner.login.ifBlank { ref.owner },
                    repoResult.data.name.ifBlank { ref.repo },
                    repoResult.data.description.orEmpty(),
                    repoResult.data.owner.avatarUrl
                )
            }
            is GitHubResult.NotModified -> Quad(ref.owner, ref.repo, "", null)
            is GitHubResult.Error -> {
                repoResult.rateLimit?.let { persistRateLimit(it) }
                return Result.failure(IllegalStateException(repoResult.message))
            }
        }

        val normalizedPkg = manualPackageName?.trim()?.takeIf { it.isNotBlank() }
        val installed = normalizedPkg?.let { installedAppsInspector.getInstalledPackageInfo(it) }

        val newEntity = TrackedAppEntity(
            owner = owner,
            repo = repo,
            displayName = customDisplayName?.trim()?.takeIf { it.isNotBlank() } ?: repo,
            description = desc,
            avatarUrl = avatarUrl,
            packageName = normalizedPkg,
            channel = channel.name,
            installedVersionName = installed?.versionName,
            installedVersionCode = installed?.versionCode
        )

        val id = trackedAppDao.insert(newEntity)
        val refreshed = checkSingleAppUpdate(id, forceIgnoreEtag = true)
        return Result.success(refreshed ?: newEntity.copy(id = id).toUiModel())
    }

    suspend fun removeApp(appId: Long) {
        trackedAppDao.deleteById(appId)
    }

    suspend fun updateAppChannel(appId: Long, channel: ReleaseChannel) {
        val entity = trackedAppDao.getById(appId) ?: return
        val updated = entity.copy(channel = channel.name)
        trackedAppDao.update(updated)
        recomputeSelectionFromCache(updated)
    }

    suspend fun bindPackageName(appId: Long, packageName: String) {
        val entity = trackedAppDao.getById(appId) ?: return
        val cleanPkg = packageName.trim().takeIf { it.isNotBlank() }
        val installed = cleanPkg?.let { installedAppsInspector.getInstalledPackageInfo(it) }
        val updated = entity.copy(
            packageName = cleanPkg,
            installedVersionName = installed?.versionName,
            installedVersionCode = installed?.versionCode
        )
        trackedAppDao.update(updated)
        recomputeSelectionFromCache(updated)
    }

    suspend fun syncAllInstalledPackages() {
        val apps = trackedAppDao.getAllOnce()
        for (app in apps) {
            syncInstalledStateForEntity(app)
        }
    }

    suspend fun syncInstalledPackageByName(packageName: String) {
        val app = trackedAppDao.findByPackageName(packageName) ?: return
        syncInstalledStateForEntity(app)
    }

    private suspend fun syncInstalledStateForEntity(app: TrackedAppEntity) {
        val pkg = app.packageName ?: return
        val installed = installedAppsInspector.getInstalledPackageInfo(pkg)
        val newHasUpdate = computeHasUpdate(
            installedVersionName = installed?.versionName,
            latestTag = app.latestTag,
            hasCompatibleAsset = !app.latestAssetUrl.isNullOrBlank()
        )
        if (installed?.versionName != app.installedVersionName ||
            installed?.versionCode != app.installedVersionCode ||
            newHasUpdate != app.hasUpdate
        ) {
            trackedAppDao.update(
                app.copy(
                    installedVersionName = installed?.versionName,
                    installedVersionCode = installed?.versionCode,
                    hasUpdate = newHasUpdate
                )
            )
        }
    }

    suspend fun checkSingleAppUpdate(
        appId: Long,
        forceIgnoreEtag: Boolean = false
    ): TrackedAppUiModel? {
        val entity = trackedAppDao.getById(appId) ?: return null
        val token = settingsDataStore.getSettingsSnapshot().githubToken.takeIf { it.isNotBlank() }
        val etagToUse = if (forceIgnoreEtag) null else entity.etag

        val result = gitHubApiClient.fetchReleases(
            owner = entity.owner,
            repo = entity.repo,
            token = token,
            etag = etagToUse
        )

        val now = System.currentTimeMillis()

        return when (result) {
            is GitHubResult.Success -> {
                result.rateLimit?.let { persistRateLimit(it) }
                val releases = result.data
                val cachedEntities = releases.map { it.toCachedEntity(entity.id) }
                cachedReleaseDao.replaceForApp(entity.id, cachedEntities)

                val updated = applyReleasesToEntity(
                    entity = entity.copy(
                        etag = result.etag ?: entity.etag,
                        lastCheckedAt = now,
                        lastError = null
                    ),
                    releases = releases
                )
                trackedAppDao.update(updated)

                if (updated.hasUpdate && updated.latestTag != entity.latestTag) {
                    logHistory(
                        appId = updated.id,
                        appName = updated.displayName,
                        repoFullName = "${updated.owner}/${updated.repo}",
                        packageName = updated.packageName,
                        versionTag = updated.latestTag,
                        type = HistoryEventType.UPDATE_FOUND,
                        message = "Доступен релиз ${updated.latestTag} (${updated.latestAssetName ?: "APK"})"
                    )
                }
                updated.toUiModel()
            }

            is GitHubResult.NotModified -> {
                result.rateLimit?.let { persistRateLimit(it) }
                val cached = cachedReleaseDao.getByAppId(entity.id).map { it.toDomain() }
                val updated = applyReleasesToEntity(
                    entity = entity.copy(
                        etag = result.etag ?: entity.etag,
                        lastCheckedAt = now,
                        lastError = null
                    ),
                    releases = cached
                )
                trackedAppDao.update(updated)
                updated.toUiModel()
            }

            is GitHubResult.Error -> {
                result.rateLimit?.let { persistRateLimit(it) }
                val updated = entity.copy(
                    lastCheckedAt = now,
                    lastError = result.message
                )
                trackedAppDao.update(updated)
                updated.toUiModel()
            }
        }
    }

    suspend fun checkAllUpdates(): UpdateSummary {
        syncAllInstalledPackages()
        val apps = trackedAppDao.getAllOnce()
        val updatesFound = mutableListOf<TrackedAppUiModel>()
        var errors = 0
        var rateLimited = false

        for (app in apps) {
            if (rateLimited) {
                errors++
                continue
            }
            val result = checkSingleAppUpdate(app.id)
            if (result != null) {
                if (result.hasUpdate) updatesFound.add(result)
                if (!result.lastError.isNullOrBlank()) {
                    errors++
                    if (result.lastError.contains("лимит", ignoreCase = true)) {
                        rateLimited = true
                    }
                }
            }
        }

        settingsDataStore.updateLastGlobalCheck(System.currentTimeMillis())
        return UpdateSummary(
            checkedCount = apps.size,
            updatesFound = updatesFound,
            errorsCount = errors,
            rateLimited = rateLimited
        )
    }

    suspend fun checkSelfUpdate(): SelfUpdateState {
        val settings = settingsDataStore.getSettingsSnapshot()
        val repoStr = settings.selfUpdateRepo.ifBlank { BuildConfig.SELF_UPDATE_REPO }
        val baseState = SelfUpdateState(
            repoFullName = repoStr,
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        )

        val ref = GitHubUrlParser.parse(repoStr)
            ?: return baseState.copy(
                lastError = "Неверный формат репозитория для самообновления ($repoStr)."
            )

        val token = settings.githubToken.takeIf { it.isNotBlank() }
        val res = gitHubApiClient.fetchReleases(ref.owner, ref.repo, token)
        return when (res) {
            is GitHubResult.Success -> {
                res.rateLimit?.let { persistRateLimit(it) }
                val bestRelease = pickLatestReleaseForChannel(res.data, settings.defaultChannel)
                if (bestRelease == null) {
                    baseState.copy(lastError = "Подходящие релизы APK HUB не найдены.")
                } else {
                    val device = deviceInfoProvider.getDeviceProfile()
                    val selection = ApkAssetSelector.selectBestApk(bestRelease.assets, device)
                    val newer = VersionComparator.isNewer(
                        candidateVersion = bestRelease.tagName,
                        currentVersion = BuildConfig.VERSION_NAME
                    )
                    baseState.copy(
                        latestTag = bestRelease.tagName,
                        latestChangelog = bestRelease.body,
                        asset = selection.recommended,
                        hasUpdate = newer && selection.recommended != null,
                        lastError = if (selection.recommended == null) selection.reason else null
                    )
                }
            }
            is GitHubResult.NotModified -> baseState
            is GitHubResult.Error -> {
                res.rateLimit?.let { persistRateLimit(it) }
                baseState.copy(lastError = res.message)
            }
        }
    }

    suspend fun downloadAndInstallApp(
        appId: Long,
        specificRelease: ReleaseInfo? = null,
        specificAsset: ReleaseAsset? = null
    ): Result<Long> {
        val entity = trackedAppDao.getById(appId)
            ?: return Result.failure(IllegalStateException("Приложение не найдено."))

        val release = specificRelease ?: run {
            val cached = cachedReleaseDao.getByAppId(appId).map { it.toDomain() }
            pickLatestReleaseForChannel(cached, ReleaseChannel.fromNameOrDefault(entity.channel))
        }

        val asset = specificAsset ?: run {
            if (release != null) {
                ApkAssetSelector.selectBestApk(release.assets, deviceInfoProvider.getDeviceProfile()).recommended
            } else if (!entity.latestAssetUrl.isNullOrBlank() && !entity.latestAssetName.isNullOrBlank()) {
                ReleaseAsset(
                    id = 0L,
                    name = entity.latestAssetName,
                    downloadUrl = entity.latestAssetUrl,
                    sizeBytes = entity.latestAssetSizeBytes
                )
            } else {
                null
            }
        }

        if (asset == null) {
            return Result.failure(
                IllegalStateException(
                    entity.unsupportedFormatHint
                        ?: "Нет совместимого APK для архитектуры вашего устройства."
                )
            )
        }

        val token = settingsDataStore.getSettingsSnapshot().githubToken.takeIf { it.isNotBlank() }
        val expectedSha256 = resolveExpectedSha256(release, asset, token)

        val taskId = downloadTaskDao.insert(
            DownloadTaskEntity(
                appId = entity.id,
                appName = entity.displayName,
                repoFullName = "${entity.owner}/${entity.repo}",
                versionTag = release?.tagName ?: entity.latestTag.orEmpty(),
                assetName = asset.name,
                downloadUrl = asset.downloadUrl,
                expectedSizeBytes = asset.sizeBytes,
                expectedSha256 = expectedSha256,
                status = DownloadStatus.DOWNLOADING.name
            )
        )

        executeDownloadVerifyAndInstall(
            taskId = taskId,
            appId = entity.id,
            appName = entity.displayName,
            repoFullName = "${entity.owner}/${entity.repo}",
            expectedPackageName = entity.packageName,
            asset = asset,
            expectedSha256 = expectedSha256,
            token = token
        )

        return Result.success(taskId)
    }

    suspend fun downloadAndInstallSelfUpdate(state: SelfUpdateState): Result<Long> {
        val asset = state.asset
            ?: return Result.failure(IllegalStateException("Нет совместимого APK для обновления APK HUB."))
        val token = settingsDataStore.getSettingsSnapshot().githubToken.takeIf { it.isNotBlank() }
        val expectedSha256 = ChecksumExtractor.extractExpectedSha256(state.latestChangelog, asset.name)
        val selfPkg = installedAppsInspector.getSelfPackageInfo()?.packageName

        val taskId = downloadTaskDao.insert(
            DownloadTaskEntity(
                appId = SELF_UPDATE_APP_ID,
                appName = "APK HUB",
                repoFullName = state.repoFullName,
                versionTag = state.latestTag.orEmpty(),
                assetName = asset.name,
                downloadUrl = asset.downloadUrl,
                expectedSizeBytes = asset.sizeBytes,
                expectedSha256 = expectedSha256,
                status = DownloadStatus.DOWNLOADING.name
            )
        )

        executeDownloadVerifyAndInstall(
            taskId = taskId,
            appId = SELF_UPDATE_APP_ID,
            appName = "APK HUB",
            repoFullName = state.repoFullName,
            expectedPackageName = selfPkg,
            asset = asset,
            expectedSha256 = expectedSha256,
            token = token
        )
        return Result.success(taskId)
    }

    suspend fun triggerInstallForReadyTask(taskId: Long): InstallTriggerResult {
        val task = downloadTaskDao.getById(taskId)
            ?: return InstallTriggerResult.Failed("Задача загрузки не найдена.")
        val path = task.localFilePath
            ?: return InstallTriggerResult.Failed("Локальный файл APK отсутствует.")
        val file = File(path)

        val expectedPkg = if (task.appId > 0L) {
            trackedAppDao.getById(task.appId)?.packageName ?: task.verifiedPackageName
        } else {
            task.verifiedPackageName
        }

        // Re-verify before installing to guarantee integrity even if file changed on disk
        val verification = apkMetadataVerifier.verify(
            apkFile = file,
            expectedSizeBytes = task.expectedSizeBytes,
            expectedSha256 = task.expectedSha256,
            expectedPackageName = expectedPkg
        )

        when (verification) {
            is VerificationOutcome.Blocked -> {
                downloadTaskDao.update(
                    task.copy(
                        status = DownloadStatus.FAILED.name,
                        errorMessage = verification.reason,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                logHistory(
                    appId = task.appId.takeIf { it > 0L },
                    appName = task.appName,
                    repoFullName = task.repoFullName,
                    packageName = verification.inspection?.packageName ?: expectedPkg,
                    versionTag = task.versionTag,
                    type = HistoryEventType.VERIFICATION_BLOCKED,
                    message = verification.reason,
                    sha256 = verification.inspection?.sha256
                )
                return InstallTriggerResult.Failed(verification.reason)
            }
            is VerificationOutcome.Verified -> {
                downloadTaskDao.update(
                    task.copy(
                        status = DownloadStatus.INSTALLING.name,
                        verifiedPackageName = verification.inspection.packageName,
                        verifiedVersionName = verification.inspection.versionName,
                        verifiedVersionCode = verification.inspection.versionCode,
                        errorMessage = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                val trigger = packageInstallerController.installApk(
                    apkFile = file,
                    taskId = task.id,
                    appId = task.appId,
                    expectedPackageName = verification.inspection.packageName
                )
                when (trigger) {
                    is InstallTriggerResult.PermissionRequired -> {
                        downloadTaskDao.update(
                            task.copy(
                                status = DownloadStatus.READY_TO_INSTALL.name,
                                errorMessage = "Требуется разрешение на установку из этого источника.",
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    is InstallTriggerResult.Failed -> {
                        downloadTaskDao.update(
                            task.copy(
                                status = DownloadStatus.FAILED.name,
                                errorMessage = trigger.message,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                    }
                    is InstallTriggerResult.SessionCommitted -> Unit
                }
                return trigger
            }
        }
    }

    suspend fun cancelDownload(taskId: Long) {
        apkDownloadManager.cancelDownload(taskId)
        val task = downloadTaskDao.getById(taskId) ?: return
        downloadTaskDao.update(
            task.copy(
                status = DownloadStatus.CANCELLED.name,
                errorMessage = "Отменено пользователем",
                updatedAt = System.currentTimeMillis()
            )
        )
    }

    suspend fun retryDownload(taskId: Long): Result<Long> {
        val task = downloadTaskDao.getById(taskId)
            ?: return Result.failure(IllegalStateException("Задача не найдена."))
        val token = settingsDataStore.getSettingsSnapshot().githubToken.takeIf { it.isNotBlank() }
        val expectedPkg = if (task.appId > 0L) {
            trackedAppDao.getById(task.appId)?.packageName
        } else {
            installedAppsInspector.getSelfPackageInfo()?.packageName
        }

        downloadTaskDao.update(
            task.copy(
                status = DownloadStatus.DOWNLOADING.name,
                downloadedBytes = 0L,
                errorMessage = null,
                updatedAt = System.currentTimeMillis()
            )
        )

        executeDownloadVerifyAndInstall(
            taskId = task.id,
            appId = task.appId,
            appName = task.appName,
            repoFullName = task.repoFullName,
            expectedPackageName = expectedPkg,
            asset = ReleaseAsset(
                id = 0L,
                name = task.assetName,
                downloadUrl = task.downloadUrl,
                sizeBytes = task.expectedSizeBytes
            ),
            expectedSha256 = task.expectedSha256,
            token = token
        )
        return Result.success(task.id)
    }

    suspend fun handleInstallResultEvent(event: InstallResultEvent) {
        val task = downloadTaskDao.getById(event.taskId)
        val now = System.currentTimeMillis()

        if (task != null) {
            val newStatus = when {
                event.isSuccess -> DownloadStatus.INSTALLED
                event.isCancelled -> DownloadStatus.READY_TO_INSTALL
                else -> DownloadStatus.FAILED
            }
            downloadTaskDao.update(
                task.copy(
                    status = newStatus.name,
                    errorMessage = if (event.isSuccess) null else event.statusMessage,
                    updatedAt = now
                )
            )

            val historyType = when {
                event.isSuccess -> HistoryEventType.INSTALL_SUCCESS
                event.isCancelled -> HistoryEventType.INSTALL_CANCELLED
                else -> HistoryEventType.INSTALL_FAILED
            }

            logHistory(
                appId = task.appId.takeIf { it > 0L },
                appName = task.appName,
                repoFullName = task.repoFullName,
                packageName = event.packageName ?: task.verifiedPackageName,
                versionTag = task.versionTag,
                type = historyType,
                message = event.statusMessage,
                sha256 = task.computedSha256
            )

            if (event.isSuccess) {
                val settings = settingsDataStore.getSettingsSnapshot()
                if (settings.autoDeleteApkAfterInstall) {
                    apkDownloadManager.deleteFileIfExists(task.localFilePath)
                }

                // Bind detected packageName automatically to tracked app and refresh installed state
                if (task.appId > 0L) {
                    val app = trackedAppDao.getById(task.appId)
                    val pkg = event.packageName ?: task.verifiedPackageName
                    if (app != null && !pkg.isNullOrBlank()) {
                        val installed = installedAppsInspector.getInstalledPackageInfo(pkg)
                        val updatedApp = app.copy(
                            packageName = pkg,
                            installedVersionName = installed?.versionName ?: task.verifiedVersionName,
                            installedVersionCode = installed?.versionCode ?: task.verifiedVersionCode,
                            hasUpdate = computeHasUpdate(
                                installedVersionName = installed?.versionName ?: task.verifiedVersionName,
                                latestTag = app.latestTag,
                                hasCompatibleAsset = !app.latestAssetUrl.isNullOrBlank()
                            )
                        )
                        trackedAppDao.update(updatedApp)
                    }
                }
            }
        }
    }

    suspend fun clearFinishedDownloads() {
        downloadTaskDao.clearFinished()
    }

    suspend fun clearHistory() {
        activityHistoryDao.clearAll()
    }

    fun getApkCacheSizeBytes(): Long = apkDownloadManager.getCacheSizeBytes()

    fun clearApkCache(): Long = apkDownloadManager.clearAllApkFiles()

    private suspend fun executeDownloadVerifyAndInstall(
        taskId: Long,
        appId: Long,
        appName: String,
        repoFullName: String,
        expectedPackageName: String?,
        asset: ReleaseAsset,
        expectedSha256: String?,
        token: String?
    ) {
        val outcome = apkDownloadManager.downloadApk(
            taskId = taskId,
            url = asset.downloadUrl,
            targetFileName = asset.name,
            expectedSizeBytes = asset.sizeBytes,
            token = token
        )

        val currentTask = downloadTaskDao.getById(taskId) ?: return
        val now = System.currentTimeMillis()

        when (outcome) {
            is DownloadOutcome.Cancelled -> {
                downloadTaskDao.update(
                    currentTask.copy(
                        status = DownloadStatus.CANCELLED.name,
                        errorMessage = "Загрузка отменена.",
                        updatedAt = now
                    )
                )
            }
            is DownloadOutcome.Failure -> {
                downloadTaskDao.update(
                    currentTask.copy(
                        status = DownloadStatus.FAILED.name,
                        errorMessage = outcome.message,
                        updatedAt = now
                    )
                )
                logHistory(
                    appId = appId.takeIf { it > 0L },
                    appName = appName,
                    repoFullName = repoFullName,
                    packageName = expectedPackageName,
                    versionTag = currentTask.versionTag,
                    type = HistoryEventType.DOWNLOAD_FAILED,
                    message = outcome.message
                )
            }
            is DownloadOutcome.Success -> {
                downloadTaskDao.update(
                    currentTask.copy(
                        status = DownloadStatus.VERIFYING.name,
                        downloadedBytes = outcome.sizeBytes,
                        localFilePath = outcome.file.absolutePath,
                        computedSha256 = outcome.sha256,
                        updatedAt = now
                    )
                )

                val verification = apkMetadataVerifier.verify(
                    apkFile = outcome.file,
                    expectedSizeBytes = asset.sizeBytes,
                    expectedSha256 = expectedSha256,
                    expectedPackageName = expectedPackageName,
                    precomputedSha256 = outcome.sha256
                )

                when (verification) {
                    is VerificationOutcome.Blocked -> {
                        // Remove compromised/corrupted APK file immediately
                        apkDownloadManager.deleteFileIfExists(outcome.file.absolutePath)
                        downloadTaskDao.update(
                            currentTask.copy(
                                status = DownloadStatus.FAILED.name,
                                downloadedBytes = outcome.sizeBytes,
                                localFilePath = null,
                                computedSha256 = outcome.sha256,
                                errorMessage = verification.reason,
                                updatedAt = System.currentTimeMillis()
                            )
                        )
                        logHistory(
                            appId = appId.takeIf { it > 0L },
                            appName = appName,
                            repoFullName = repoFullName,
                            packageName = verification.inspection?.packageName ?: expectedPackageName,
                            versionTag = currentTask.versionTag,
                            type = HistoryEventType.VERIFICATION_BLOCKED,
                            message = verification.reason,
                            sha256 = outcome.sha256
                        )
                    }
                    is VerificationOutcome.Verified -> {
                        val info = verification.inspection
                        val noteParts = mutableListOf<String>()
                        noteParts.add("${info.packageName} • v${info.versionName} (${info.versionCode})")
                        if (verification.checksumVerified) {
                            noteParts.add("SHA-256 подтверждён")
                        }
                        if (info.nativeAbis.isNotEmpty()) {
                            noteParts.add("ABI: ${info.nativeAbis.joinToString()}")
                        }
                        noteParts.addAll(verification.warnings)

                        val readyTask = currentTask.copy(
                            status = DownloadStatus.READY_TO_INSTALL.name,
                            downloadedBytes = outcome.sizeBytes,
                            localFilePath = outcome.file.absolutePath,
                            computedSha256 = outcome.sha256,
                            verifiedPackageName = info.packageName,
                            verifiedVersionName = info.versionName,
                            verifiedVersionCode = info.versionCode,
                            verificationNote = noteParts.joinToString(" • "),
                            errorMessage = null,
                            updatedAt = System.currentTimeMillis()
                        )
                        downloadTaskDao.update(readyTask)

                        logHistory(
                            appId = appId.takeIf { it > 0L },
                            appName = appName,
                            repoFullName = repoFullName,
                            packageName = info.packageName,
                            versionTag = currentTask.versionTag,
                            type = HistoryEventType.DOWNLOAD_COMPLETED,
                            message = "APK проверен: ${info.packageName} v${info.versionName}",
                            sha256 = outcome.sha256
                        )

                        // Trigger Android PackageInstaller automatically if permission is granted
                        if (packageInstallerController.canRequestPackageInstalls()) {
                            triggerInstallForReadyTask(taskId)
                        }
                    }
                }
            }
        }
    }

    private suspend fun resolveExpectedSha256(
        release: ReleaseInfo?,
        apkAsset: ReleaseAsset,
        token: String?
    ): String? {
        if (release == null) return null

        // 1. Check release body first
        ChecksumExtractor.extractExpectedSha256(release.body, apkAsset.name)?.let { return it }

        // 2. Check sibling checksum files (.sha256, sha256sums.txt, etc.)
        val checksumAsset = release.assets.firstOrNull { candidate ->
            ChecksumExtractor.isChecksumAssetFor(candidate.name, apkAsset.name)
        } ?: return null

        val text = gitHubApiClient.fetchTextAsset(checksumAsset.downloadUrl, token)
        return ChecksumExtractor.extractExpectedSha256(text, apkAsset.name)
    }

    private suspend fun recomputeSelectionFromCache(entity: TrackedAppEntity) {
        val cached = cachedReleaseDao.getByAppId(entity.id).map { it.toDomain() }
        val updated = applyReleasesToEntity(entity, cached)
        trackedAppDao.update(updated)
    }

    private fun applyReleasesToEntity(
        entity: TrackedAppEntity,
        releases: List<ReleaseInfo>
    ): TrackedAppEntity {
        val channel = ReleaseChannel.fromNameOrDefault(entity.channel)
        val latest = pickLatestReleaseForChannel(releases, channel)
        val installed = entity.packageName?.let { installedAppsInspector.getInstalledPackageInfo(it) }

        if (latest == null) {
            return entity.copy(
                installedVersionName = installed?.versionName ?: entity.installedVersionName,
                installedVersionCode = installed?.versionCode ?: entity.installedVersionCode,
                latestReleaseId = null,
                latestTag = null,
                latestReleaseName = null,
                latestPublishedAt = null,
                latestChangelog = null,
                latestAssetName = null,
                latestAssetUrl = null,
                latestAssetSizeBytes = 0L,
                latestAssetAbiNote = null,
                unsupportedFormatHint = if (releases.isEmpty()) {
                    "В репозитории нет опубликованных релизов."
                } else {
                    "Нет релизов для канала ${channel.label}."
                },
                hasUpdate = false
            )
        }

        val device = deviceInfoProvider.getDeviceProfile()
        val selection = ApkAssetSelector.selectBestApk(latest.assets, device)
        val installedVerName = installed?.versionName
        val hasUpdate = computeHasUpdate(
            installedVersionName = installedVerName,
            latestTag = latest.tagName,
            hasCompatibleAsset = selection.recommended != null
        )

        return entity.copy(
            installedVersionName = installed?.versionName,
            installedVersionCode = installed?.versionCode,
            latestReleaseId = latest.id,
            latestTag = latest.tagName,
            latestReleaseName = latest.name,
            latestPublishedAt = latest.publishedAt,
            latestChangelog = latest.body,
            latestAssetName = selection.recommended?.name,
            latestAssetUrl = selection.recommended?.downloadUrl,
            latestAssetSizeBytes = selection.recommended?.sizeBytes ?: 0L,
            latestAssetAbiNote = if (selection.recommended != null) selection.reason else null,
            unsupportedFormatHint = if (selection.recommended == null) selection.reason else null,
            hasUpdate = hasUpdate
        )
    }

    private fun pickLatestReleaseForChannel(
        releases: List<ReleaseInfo>,
        channel: ReleaseChannel
    ): ReleaseInfo? {
        val eligible = releases.filter { !it.isDraft && channel.includes(it.channel) }
        if (eligible.isEmpty()) return null
        return eligible.maxWithOrNull(
            compareBy<ReleaseInfo> { VersionComparator.parse(it.tagName) }
                .thenBy { it.publishedAt }
        )
    }

    private fun computeHasUpdate(
        installedVersionName: String?,
        latestTag: String?,
        hasCompatibleAsset: Boolean
    ): Boolean {
        if (!hasCompatibleAsset || latestTag.isNullOrBlank()) return false
        if (installedVersionName.isNullOrBlank()) return false
        return VersionComparator.isNewer(latestTag, installedVersionName)
    }

    private suspend fun persistRateLimit(info: RateLimitInfo) {
        settingsDataStore.updateRateLimit(info.limit, info.remaining, info.resetEpochSeconds)
    }

    private suspend fun logHistory(
        appId: Long?,
        appName: String,
        repoFullName: String,
        packageName: String?,
        versionTag: String?,
        type: HistoryEventType,
        message: String,
        sha256: String? = null
    ) {
        activityHistoryDao.insert(
            ActivityHistoryEntity(
                appId = appId,
                appName = appName,
                repoFullName = repoFullName,
                packageName = packageName,
                versionTag = versionTag,
                eventType = type.name,
                message = message,
                sha256 = sha256
            )
        )
    }

    private fun ReleaseInfo.toCachedEntity(appId: Long): CachedReleaseEntity {
        return CachedReleaseEntity(
            appId = appId,
            releaseId = id,
            tagName = tagName,
            name = name,
            body = body,
            htmlUrl = htmlUrl,
            publishedAt = publishedAt,
            isPrerelease = isPrerelease,
            channel = channel.name,
            assetsJson = json.encodeToString(assets)
        )
    }

    private fun CachedReleaseEntity.toDomain(): ReleaseInfo {
        val decodedAssets = try {
            json.decodeFromString<List<ReleaseAsset>>(assetsJson)
        } catch (_: Exception) {
            emptyList()
        }
        return ReleaseInfo(
            id = releaseId,
            tagName = tagName,
            name = name,
            body = body,
            htmlUrl = htmlUrl,
            publishedAt = publishedAt,
            isPrerelease = isPrerelease,
            isDraft = false,
            channel = ReleaseChannel.fromNameOrDefault(channel),
            assets = decodedAssets
        )
    }

    private fun TrackedAppEntity.toUiModel(): TrackedAppUiModel {
        return TrackedAppUiModel(
            id = id,
            owner = owner,
            repo = repo,
            displayName = displayName,
            description = description,
            avatarUrl = avatarUrl,
            packageName = packageName,
            channel = ReleaseChannel.fromNameOrDefault(channel),
            installedVersionName = installedVersionName,
            installedVersionCode = installedVersionCode,
            latestTag = latestTag,
            latestReleaseName = latestReleaseName,
            latestPublishedAt = latestPublishedAt,
            latestChangelog = latestChangelog,
            latestAssetName = latestAssetName,
            latestAssetUrl = latestAssetUrl,
            latestAssetSizeBytes = latestAssetSizeBytes,
            latestAssetAbiNote = latestAssetAbiNote,
            unsupportedFormatHint = unsupportedFormatHint,
            hasUpdate = hasUpdate,
            lastCheckedAt = lastCheckedAt,
            lastError = lastError,
            isSelfApp = isSelfApp
        )
    }

    private data class Quad<A, B, C, D>(val a: A, val b: B, val c: C, val d: D)

    companion object {
        const val SELF_UPDATE_APP_ID = -100L
    }
}
