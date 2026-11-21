package com.apkhub.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.apkhub.app.BuildConfig
import com.apkhub.app.data.local.ActivityHistoryEntity
import com.apkhub.app.data.local.DownloadTaskEntity
import com.apkhub.app.data.repository.RepoPreview
import com.apkhub.app.data.system.ActiveDownloadProgress
import com.apkhub.app.data.system.InstallTriggerResult
import com.apkhub.app.di.AppContainer
import com.apkhub.app.domain.model.AppThemeMode
import com.apkhub.app.domain.model.DeviceProfile
import com.apkhub.app.domain.model.RateLimitInfo
import com.apkhub.app.domain.model.ReleaseAsset
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.domain.model.ReleaseInfo
import com.apkhub.app.domain.model.SelfUpdateState
import com.apkhub.app.domain.model.TrackedAppUiModel
import com.apkhub.app.domain.model.UpdateCheckInterval
import com.apkhub.app.domain.model.UserSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class AppsFilter(val label: String) {
    ALL("Все"),
    UPDATES("Обновления"),
    INSTALLED("Установлены"),
    STABLE("Stable"),
    BETA("Beta"),
    TEST("Test")
}

sealed interface UiEvent {
    data class ShowMessage(val text: String) : UiEvent
    data object RequestInstallPermission : UiEvent
}

data class AddRepoDialogState(
    val isOpen: Boolean = false,
    val urlInput: String = "",
    val selectedChannel: ReleaseChannel = ReleaseChannel.STABLE,
    val customName: String = "",
    val packageName: String = "",
    val isLoadingPreview: Boolean = false,
    val isSubmitting: Boolean = false,
    val preview: RepoPreview? = null,
    val error: String? = null
)

class ApkHubViewModel(
    private val container: AppContainer
) : ViewModel() {

    private val repo = container.appRepository
    private val settingsStore = container.settingsDataStore

    val deviceProfile: DeviceProfile = container.deviceInfoProvider.getDeviceProfile()

    val apps: StateFlow<List<TrackedAppUiModel>> = repo.trackedAppsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val downloadTasks: StateFlow<List<DownloadTaskEntity>> = repo.downloadTasksFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val activeDownloadProgress: StateFlow<Map<Long, ActiveDownloadProgress>> =
        container.apkDownloadManager.progressMap

    val activityHistory: StateFlow<List<ActivityHistoryEntity>> = repo.activityHistoryFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = emptyList()
    )

    val settings: StateFlow<UserSettings> = settingsStore.settingsFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = UserSettings(selfUpdateRepo = BuildConfig.SELF_UPDATE_REPO)
    )

    val rateLimit: StateFlow<RateLimitInfo> = settingsStore.rateLimitFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = RateLimitInfo()
    )

    val lastGlobalCheck: StateFlow<Long> = settingsStore.lastGlobalCheckFlow.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0L
    )

    private val _isCheckingAll = MutableStateFlow(false)
    val isCheckingAll: StateFlow<Boolean> = _isCheckingAll.asStateFlow()

    private val _isUpdatingAll = MutableStateFlow(false)
    val isUpdatingAll: StateFlow<Boolean> = _isUpdatingAll.asStateFlow()

    private val _checkingAppIds = MutableStateFlow<Set<Long>>(emptySet())
    val checkingAppIds: StateFlow<Set<Long>> = _checkingAppIds.asStateFlow()

    private val _isOnline = MutableStateFlow(container.deviceInfoProvider.isOnline())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private val _canInstallPackages = MutableStateFlow(
        container.packageInstallerController.canRequestPackageInstalls()
    )
    val canInstallPackages: StateFlow<Boolean> = _canInstallPackages.asStateFlow()

    private val _hasNotificationPermission = MutableStateFlow(
        container.notificationHelper.hasNotificationPermission()
    )
    val hasNotificationPermission: StateFlow<Boolean> = _hasNotificationPermission.asStateFlow()

    private val _apkCacheSizeBytes = MutableStateFlow(repo.getApkCacheSizeBytes())
    val apkCacheSizeBytes: StateFlow<Long> = _apkCacheSizeBytes.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _selectedFilter = MutableStateFlow(AppsFilter.ALL)
    val selectedFilter: StateFlow<AppsFilter> = _selectedFilter.asStateFlow()

    private val _selectedAppIdForDetail = MutableStateFlow<Long?>(null)
    val selectedAppIdForDetail: StateFlow<Long?> = _selectedAppIdForDetail.asStateFlow()

    private val _addRepoDialog = MutableStateFlow(AddRepoDialogState())
    val addRepoDialog: StateFlow<AddRepoDialogState> = _addRepoDialog.asStateFlow()

    private val _selfUpdateState = MutableStateFlow(
        SelfUpdateState(
            repoFullName = BuildConfig.SELF_UPDATE_REPO,
            currentVersionName = BuildConfig.VERSION_NAME,
            currentVersionCode = BuildConfig.VERSION_CODE.toLong()
        )
    )
    val selfUpdateState: StateFlow<SelfUpdateState> = _selfUpdateState.asStateFlow()

    private val _events = MutableSharedFlow<UiEvent>(extraBufferCapacity = 16)
    val events: SharedFlow<UiEvent> = _events.asSharedFlow()

    fun refreshSystemState() {
        _isOnline.value = container.deviceInfoProvider.isOnline()
        _canInstallPackages.value = container.packageInstallerController.canRequestPackageInstalls()
        _hasNotificationPermission.value = container.notificationHelper.hasNotificationPermission()
        _apkCacheSizeBytes.value = repo.getApkCacheSizeBytes()
        viewModelScope.launch {
            repo.syncAllInstalledPackages()
        }
    }

    fun observeReleases(appId: Long): Flow<List<ReleaseInfo>> = repo.observeReleasesForApp(appId)

    fun observeAppHistory(appId: Long): Flow<List<ActivityHistoryEntity>> = repo.observeHistoryForApp(appId)

    fun loadInstalledAppIcon(packageName: String) =
        container.installedAppsInspector.loadAppIcon(packageName)

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setAppsFilter(filter: AppsFilter) {
        _selectedFilter.value = filter
    }

    fun openAppDetail(appId: Long) {
        _selectedAppIdForDetail.value = appId
    }

    fun closeAppDetail() {
        _selectedAppIdForDetail.value = null
    }

    // Add Repository Dialog Flow
    fun openAddRepoDialog() {
        _addRepoDialog.value = AddRepoDialogState(
            isOpen = true,
            selectedChannel = settings.value.defaultChannel
        )
    }

    fun closeAddRepoDialog() {
        _addRepoDialog.value = AddRepoDialogState(isOpen = false)
    }

    fun updateAddRepoInput(url: String) {
        _addRepoDialog.update { it.copy(urlInput = url, error = null, preview = null) }
    }

    fun updateAddRepoChannel(channel: ReleaseChannel) {
        _addRepoDialog.update { it.copy(selectedChannel = channel, preview = null) }
    }

    fun updateAddRepoCustomName(name: String) {
        _addRepoDialog.update { it.copy(customName = name) }
    }

    fun updateAddRepoPackageName(pkg: String) {
        _addRepoDialog.update { it.copy(packageName = pkg) }
    }

    fun previewRepositoryInput() {
        val state = _addRepoDialog.value
        if (state.urlInput.isBlank()) {
            _addRepoDialog.update { it.copy(error = "Введите ссылку на GitHub или owner/repo.") }
            return
        }
        viewModelScope.launch {
            _addRepoDialog.update { it.copy(isLoadingPreview = true, error = null) }
            val result = repo.previewRepository(state.urlInput, state.selectedChannel)
            result.onSuccess { preview ->
                _addRepoDialog.update {
                    it.copy(
                        isLoadingPreview = false,
                        preview = preview,
                        customName = it.customName.ifBlank { preview.displayName },
                        error = null
                    )
                }
            }.onFailure { err ->
                _addRepoDialog.update {
                    it.copy(
                        isLoadingPreview = false,
                        preview = null,
                        error = err.message ?: "Не удалось проверить репозиторий."
                    )
                }
            }
        }
    }

    fun submitAddRepository() {
        val state = _addRepoDialog.value
        if (state.urlInput.isBlank()) {
            _addRepoDialog.update { it.copy(error = "Введите ссылку на GitHub или owner/repo.") }
            return
        }
        viewModelScope.launch {
            _addRepoDialog.update { it.copy(isSubmitting = true, error = null) }
            val result = repo.addRepository(
                rawUrl = state.urlInput,
                channel = state.selectedChannel,
                customDisplayName = state.customName.takeIf { it.isNotBlank() },
                manualPackageName = state.packageName.takeIf { it.isNotBlank() }
            )
            result.onSuccess { added ->
                _addRepoDialog.value = AddRepoDialogState(isOpen = false)
                _events.emit(UiEvent.ShowMessage("Добавлено: ${added.displayName}"))
            }.onFailure { err ->
                _addRepoDialog.update {
                    it.copy(
                        isSubmitting = false,
                        error = err.message ?: "Не удалось добавить репозиторий."
                    )
                }
            }
        }
    }

    // Update Checks
    fun checkAllUpdates() {
        if (_isCheckingAll.value) return
        viewModelScope.launch {
            _isCheckingAll.value = true
            _isOnline.value = container.deviceInfoProvider.isOnline()
            val summary = repo.checkAllUpdates()
            _isCheckingAll.value = false
            val msg = when {
                summary.checkedCount == 0 -> "Список приложений пуст. Добавьте репозиторий GitHub."
                summary.rateLimited -> "Достигнут лимит GitHub API. Добавьте токен в Настройках."
                summary.updatesFound.isNotEmpty() ->
                    "Найдено обновлений: ${summary.updatesFound.size}"
                summary.errorsCount > 0 ->
                    "Проверка завершена с ошибками (${summary.errorsCount})."
                else -> "Все приложения актуальны."
            }
            _events.emit(UiEvent.ShowMessage(msg))
        }
    }

    fun checkSingleApp(appId: Long) {
        if (appId in _checkingAppIds.value) return
        viewModelScope.launch {
            _checkingAppIds.update { it + appId }
            val updated = repo.checkSingleAppUpdate(appId, forceIgnoreEtag = true)
            _checkingAppIds.update { it - appId }
            val msg = when {
                updated == null -> "Приложение не найдено."
                !updated.lastError.isNullOrBlank() -> updated.lastError
                updated.hasUpdate -> "Доступна версия ${updated.latestTag} для ${updated.displayName}"
                else -> "${updated.displayName}: обновлений нет"
            }
            _events.emit(UiEvent.ShowMessage(msg))
        }
    }

    fun updateAllAvailable() {
        if (_isUpdatingAll.value) return
        val targets = apps.value.filter { it.hasUpdate && !it.latestAssetUrl.isNullOrBlank() }
        if (targets.isEmpty()) {
            viewModelScope.launch {
                _events.emit(UiEvent.ShowMessage("Нет доступных обновлений для установки."))
            }
            return
        }
        if (!container.packageInstallerController.canRequestPackageInstalls()) {
            viewModelScope.launch {
                _events.emit(UiEvent.RequestInstallPermission)
            }
            return
        }
        viewModelScope.launch {
            _isUpdatingAll.value = true
            _events.emit(UiEvent.ShowMessage("Запущено обновление ${targets.size} приложений..."))
            for (app in targets) {
                repo.downloadAndInstallApp(app.id)
            }
            _apkCacheSizeBytes.value = repo.getApkCacheSizeBytes()
            _isUpdatingAll.value = false
        }
    }

    fun downloadAndInstall(
        appId: Long,
        specificRelease: ReleaseInfo? = null,
        specificAsset: ReleaseAsset? = null
    ) {
        viewModelScope.launch {
            if (!container.packageInstallerController.canRequestPackageInstalls()) {
                _events.emit(UiEvent.RequestInstallPermission)
            }
            val result = repo.downloadAndInstallApp(appId, specificRelease, specificAsset)
            result.onFailure { err ->
                _events.emit(UiEvent.ShowMessage(err.message ?: "Ошибка загрузки."))
            }
            _apkCacheSizeBytes.value = repo.getApkCacheSizeBytes()
        }
    }

    fun triggerInstallForTask(taskId: Long) {
        viewModelScope.launch {
            when (val res = repo.triggerInstallForReadyTask(taskId)) {
                is InstallTriggerResult.PermissionRequired -> {
                    _events.emit(UiEvent.RequestInstallPermission)
                }
                is InstallTriggerResult.Failed -> {
                    _events.emit(UiEvent.ShowMessage(res.message))
                }
                is InstallTriggerResult.SessionCommitted -> Unit
            }
        }
    }

    fun cancelDownload(taskId: Long) {
        viewModelScope.launch {
            repo.cancelDownload(taskId)
        }
    }

    fun retryDownload(taskId: Long) {
        viewModelScope.launch {
            val res = repo.retryDownload(taskId)
            res.onFailure {
                _events.emit(UiEvent.ShowMessage(it.message ?: "Не удалось повторить загрузку."))
            }
            _apkCacheSizeBytes.value = repo.getApkCacheSizeBytes()
        }
    }

    fun changeAppChannel(appId: Long, channel: ReleaseChannel) {
        viewModelScope.launch {
            repo.updateAppChannel(appId, channel)
            _events.emit(UiEvent.ShowMessage("Канал изменён на ${channel.label}"))
        }
    }

    fun bindPackageName(appId: Long, packageName: String) {
        viewModelScope.launch {
            repo.bindPackageName(appId, packageName)
            _events.emit(UiEvent.ShowMessage("Package Name сохранён"))
        }
    }

    fun removeApp(appId: Long) {
        viewModelScope.launch {
            repo.removeApp(appId)
            if (_selectedAppIdForDetail.value == appId) {
                _selectedAppIdForDetail.value = null
            }
            _events.emit(UiEvent.ShowMessage("Приложение удалено из списка"))
        }
    }

    fun clearFinishedDownloads() {
        viewModelScope.launch {
            repo.clearFinishedDownloads()
            _events.emit(UiEvent.ShowMessage("Завершённые загрузки очищены"))
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            repo.clearHistory()
            _events.emit(UiEvent.ShowMessage("История очищена"))
        }
    }

    // Self-update
    fun checkSelfUpdate() {
        viewModelScope.launch {
            _selfUpdateState.update { it.copy(isChecking = true, lastError = null) }
            val result = repo.checkSelfUpdate()
            _selfUpdateState.value = result.copy(isChecking = false)
            val msg = when {
                result.hasUpdate -> "Доступна новая версия APK HUB ${result.latestTag}"
                !result.lastError.isNullOrBlank() -> result.lastError
                else -> "Установлена актуальная версия APK HUB (${result.currentVersionName})"
            }
            _events.emit(UiEvent.ShowMessage(msg))
        }
    }

    fun installSelfUpdate() {
        val current = _selfUpdateState.value
        viewModelScope.launch {
            if (!container.packageInstallerController.canRequestPackageInstalls()) {
                _events.emit(UiEvent.RequestInstallPermission)
                return@launch
            }
            val res = repo.downloadAndInstallSelfUpdate(current)
            res.onFailure {
                _events.emit(UiEvent.ShowMessage(it.message ?: "Ошибка обновления APK HUB"))
            }
        }
    }

    // Settings
    fun setThemeMode(mode: AppThemeMode) {
        viewModelScope.launch { settingsStore.setThemeMode(mode) }
    }

    fun setDefaultChannel(channel: ReleaseChannel) {
        viewModelScope.launch { settingsStore.setDefaultChannel(channel) }
    }

    fun setCheckInterval(interval: UpdateCheckInterval) {
        viewModelScope.launch {
            settingsStore.setCheckInterval(interval)
            container.updateWorkScheduler.schedule(interval, settings.value.wifiOnlyUpdates)
        }
    }

    fun setWifiOnlyUpdates(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setWifiOnly(enabled)
            container.updateWorkScheduler.schedule(settings.value.checkInterval, enabled)
        }
    }

    fun setGithubToken(token: String) {
        viewModelScope.launch {
            settingsStore.setGithubToken(token)
            _events.emit(
                UiEvent.ShowMessage(
                    if (token.isBlank()) "GitHub Token удалён" else "GitHub Token сохранён"
                )
            )
        }
    }

    fun setSelfUpdateRepo(repoStr: String) {
        viewModelScope.launch {
            settingsStore.setSelfUpdateRepo(repoStr)
            _selfUpdateState.update { it.copy(repoFullName = repoStr.trim()) }
            _events.emit(UiEvent.ShowMessage("Репозиторий самообновления обновлён"))
        }
    }

    fun setAutoDeleteApk(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setAutoDeleteApk(enabled) }
    }

    fun clearApkCache() {
        viewModelScope.launch {
            val freed = withContext(Dispatchers.IO) { repo.clearApkCache() }
            _apkCacheSizeBytes.value = repo.getApkCacheSizeBytes()
            _events.emit(UiEvent.ShowMessage("Очищено кеша APK: ${com.apkhub.app.ui.components.Formatters.formatBytes(freed)}"))
        }
    }

    // Backup Export / Import
    suspend fun exportBackupJson(): String = container.backupManager.exportToJson()

    fun importBackupJson(rawJson: String) {
        viewModelScope.launch {
            val result = container.backupManager.importFromJson(rawJson)
            result.onSuccess { summary ->
                val cur = settingsStore.getSettingsSnapshot()
                container.updateWorkScheduler.schedule(cur.checkInterval, cur.wifiOnlyUpdates)
                repo.syncAllInstalledPackages()
                _events.emit(
                    UiEvent.ShowMessage(
                        "Импортировано приложений: ${summary.addedApps} (пропущено дубликатов: ${summary.skippedExisting})"
                    )
                )
            }.onFailure { err ->
                _events.emit(UiEvent.ShowMessage(err.message ?: "Ошибка импорта резервной копии."))
            }
        }
    }

    fun getUnknownSourcesIntent() =
        container.packageInstallerController.createUnknownSourcesSettingsIntent()

    class Factory(private val container: AppContainer) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ApkHubViewModel(container) as T
        }
    }
}
