package com.apkhub.app.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apkhub.app.domain.model.DownloadStatus
import com.apkhub.app.ui.ApkHubViewModel
import com.apkhub.app.ui.UiEvent
import com.apkhub.app.ui.components.AddRepoDialog
import com.apkhub.app.ui.theme.ApkHubTheme

enum class MainDestination(val label: String, val icon: ImageVector) {
    HOME("Главная", Icons.Rounded.Home),
    APPS("Приложения", Icons.Rounded.Apps),
    DOWNLOADS("Загрузки", Icons.Rounded.Download),
    SETTINGS("Настройки", Icons.Rounded.Settings)
}

@Composable
fun ApkHubRoot(
    viewModel: ApkHubViewModel,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onOpenInstallPermissionSettings: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val apps by viewModel.apps.collectAsStateWithLifecycle()
    val downloadTasks by viewModel.downloadTasks.collectAsStateWithLifecycle()
    val progressMap by viewModel.activeDownloadProgress.collectAsStateWithLifecycle()
    val history by viewModel.activityHistory.collectAsStateWithLifecycle()
    val rateLimit by viewModel.rateLimit.collectAsStateWithLifecycle()
    val lastGlobalCheck by viewModel.lastGlobalCheck.collectAsStateWithLifecycle()
    val isCheckingAll by viewModel.isCheckingAll.collectAsStateWithLifecycle()
    val isUpdatingAll by viewModel.isUpdatingAll.collectAsStateWithLifecycle()
    val checkingAppIds by viewModel.checkingAppIds.collectAsStateWithLifecycle()
    val isOnline by viewModel.isOnline.collectAsStateWithLifecycle()
    val canInstallPackages by viewModel.canInstallPackages.collectAsStateWithLifecycle()
    val hasNotificationPermission by viewModel.hasNotificationPermission.collectAsStateWithLifecycle()
    val apkCacheSizeBytes by viewModel.apkCacheSizeBytes.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val selectedFilter by viewModel.selectedFilter.collectAsStateWithLifecycle()
    val selectedAppId by viewModel.selectedAppIdForDetail.collectAsStateWithLifecycle()
    val addRepoDialog by viewModel.addRepoDialog.collectAsStateWithLifecycle()
    val selfUpdateState by viewModel.selfUpdateState.collectAsStateWithLifecycle()

    var currentDestination by rememberSaveable { mutableStateOf(MainDestination.HOME) }
    val snackbarHostState = remember { SnackbarHostState() }

    val updatesBadgeCount = remember(apps) { apps.count { it.hasUpdate } }
    val activeDownloadsBadgeCount = remember(downloadTasks) {
        downloadTasks.count {
            it.status == DownloadStatus.DOWNLOADING.name ||
                it.status == DownloadStatus.VERIFYING.name ||
                it.status == DownloadStatus.INSTALLING.name
        }
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is UiEvent.ShowMessage -> snackbarHostState.showSnackbar(event.text)
                is UiEvent.RequestInstallPermission -> {
                    snackbarHostState.showSnackbar(
                        "Разрешите установку неизвестных приложений для APK HUB."
                    )
                    onOpenInstallPermissionSettings()
                }
            }
        }
    }

    ApkHubTheme(themeMode = settings.themeMode) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val useNavigationRail = maxWidth >= 600.dp || viewModel.deviceProfile.isAndroidTv

            Scaffold(
                containerColor = MaterialTheme.colorScheme.background,
                snackbarHost = { SnackbarHost(snackbarHostState) },
                bottomBar = {
                    if (!useNavigationRail) {
                        NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                            MainDestination.entries.forEach { dest ->
                                val badgeCount = when (dest) {
                                    MainDestination.HOME -> updatesBadgeCount
                                    MainDestination.DOWNLOADS -> activeDownloadsBadgeCount
                                    else -> 0
                                }
                                NavigationBarItem(
                                    selected = currentDestination == dest,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        DestinationIcon(dest = dest, badgeCount = badgeCount)
                                    },
                                    label = { Text(dest.label) }
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useNavigationRail) {
                        NavigationRail(
                            containerColor = MaterialTheme.colorScheme.surface,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            MainDestination.entries.forEach { dest ->
                                val badgeCount = when (dest) {
                                    MainDestination.HOME -> updatesBadgeCount
                                    MainDestination.DOWNLOADS -> activeDownloadsBadgeCount
                                    else -> 0
                                }
                                NavigationRailItem(
                                    selected = currentDestination == dest,
                                    onClick = { currentDestination = dest },
                                    icon = {
                                        DestinationIcon(dest = dest, badgeCount = badgeCount)
                                    },
                                    label = { Text(dest.label) }
                                )
                            }
                        }
                    }

                    Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                        when (currentDestination) {
                            MainDestination.HOME -> HomeScreen(
                                apps = apps,
                                recentHistory = history,
                                deviceProfile = viewModel.deviceProfile,
                                isOnline = isOnline,
                                rateLimit = rateLimit,
                                lastGlobalCheck = lastGlobalCheck,
                                isCheckingAll = isCheckingAll,
                                isUpdatingAll = isUpdatingAll,
                                selfUpdateState = selfUpdateState,
                                loadIcon = viewModel::loadInstalledAppIcon,
                                onCheckAll = viewModel::checkAllUpdates,
                                onUpdateAll = viewModel::updateAllAvailable,
                                onAddRepo = viewModel::openAddRepoDialog,
                                onOpenAppDetail = viewModel::openAppDetail,
                                onDownloadUpdate = { appId -> viewModel.downloadAndInstall(appId) },
                                onInstallSelfUpdate = viewModel::installSelfUpdate
                            )

                            MainDestination.APPS -> AppsScreen(
                                apps = apps,
                                searchQuery = searchQuery,
                                selectedFilter = selectedFilter,
                                checkingAppIds = checkingAppIds,
                                loadIcon = viewModel::loadInstalledAppIcon,
                                onSearchChange = viewModel::setSearchQuery,
                                onFilterChange = viewModel::setAppsFilter,
                                onAddRepo = viewModel::openAddRepoDialog,
                                onOpenDetail = viewModel::openAppDetail,
                                onCheckSingle = viewModel::checkSingleApp,
                                onDownloadInstall = { appId -> viewModel.downloadAndInstall(appId) }
                            )

                            MainDestination.DOWNLOADS -> DownloadsScreen(
                                tasks = downloadTasks,
                                progressMap = progressMap,
                                history = history,
                                onCancelDownload = viewModel::cancelDownload,
                                onRetryDownload = viewModel::retryDownload,
                                onInstallReady = viewModel::triggerInstallForTask,
                                onClearFinishedDownloads = viewModel::clearFinishedDownloads,
                                onClearHistory = viewModel::clearHistory
                            )

                            MainDestination.SETTINGS -> SettingsScreen(
                                settings = settings,
                                rateLimit = rateLimit,
                                selfUpdateState = selfUpdateState,
                                canInstallPackages = canInstallPackages,
                                hasNotificationPermission = hasNotificationPermission,
                                apkCacheSizeBytes = apkCacheSizeBytes,
                                onThemeChange = viewModel::setThemeMode,
                                onDefaultChannelChange = viewModel::setDefaultChannel,
                                onIntervalChange = viewModel::setCheckInterval,
                                onWifiOnlyChange = viewModel::setWifiOnlyUpdates,
                                onAutoDeleteApkChange = viewModel::setAutoDeleteApk,
                                onSaveGithubToken = viewModel::setGithubToken,
                                onSaveSelfUpdateRepo = viewModel::setSelfUpdateRepo,
                                onCheckSelfUpdate = viewModel::checkSelfUpdate,
                                onInstallSelfUpdate = viewModel::installSelfUpdate,
                                onExportBackup = onExportBackup,
                                onImportBackup = onImportBackup,
                                onClearApkCache = viewModel::clearApkCache,
                                onOpenInstallPermission = onOpenInstallPermissionSettings,
                                onRequestNotificationPermission = onRequestNotificationPermission
                            )
                        }
                    }
                }
            }

            // Add Repository Dialog
            AddRepoDialog(
                state = addRepoDialog,
                onDismiss = viewModel::closeAddRepoDialog,
                onUrlChange = viewModel::updateAddRepoInput,
                onChannelChange = viewModel::updateAddRepoChannel,
                onCustomNameChange = viewModel::updateAddRepoCustomName,
                onPackageNameChange = viewModel::updateAddRepoPackageName,
                onPreviewClick = viewModel::previewRepositoryInput,
                onSubmitClick = viewModel::submitAddRepository
            )

            // App Details Modal Sheet
            val selectedApp = apps.firstOrNull { it.id == selectedAppId }
            if (selectedApp != null) {
                val releases by remember(selectedApp.id) {
                    viewModel.observeReleases(selectedApp.id)
                }.collectAsStateWithLifecycle(initialValue = emptyList())

                val iconDrawable = remember(selectedApp.packageName) {
                    selectedApp.packageName?.let(viewModel::loadInstalledAppIcon)
                }

                AppDetailSheet(
                    app = selectedApp,
                    releases = releases,
                    deviceProfile = viewModel.deviceProfile,
                    installedDrawable = iconDrawable,
                    onDismiss = viewModel::closeAppDetail,
                    onCheckUpdate = { viewModel.checkSingleApp(selectedApp.id) },
                    onChangeChannel = { ch -> viewModel.changeAppChannel(selectedApp.id, ch) },
                    onBindPackageName = { pkg -> viewModel.bindPackageName(selectedApp.id, pkg) },
                    onDownloadAsset = { rel, asset ->
                        viewModel.downloadAndInstall(selectedApp.id, rel, asset)
                    },
                    onRemoveApp = { viewModel.removeApp(selectedApp.id) }
                )
            }
        }
    }
}

@Composable
private fun DestinationIcon(dest: MainDestination, badgeCount: Int) {
    if (badgeCount > 0) {
        BadgedBox(
            badge = {
                Badge {
                    Text(if (badgeCount > 99) "99+" else badgeCount.toString())
                }
            }
        ) {
            Icon(imageVector = dest.icon, contentDescription = dest.label)
        }
    } else {
        Icon(imageVector = dest.icon, contentDescription = dest.label)
    }
}
