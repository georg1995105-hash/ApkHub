package com.apkhub.app.di

import android.content.Context
import com.apkhub.app.data.local.ApkHubDatabase
import com.apkhub.app.data.local.SettingsDataStore
import com.apkhub.app.data.remote.GitHubApiClient
import com.apkhub.app.data.repository.AppRepository
import com.apkhub.app.data.repository.BackupManager
import com.apkhub.app.data.system.ApkDownloadManager
import com.apkhub.app.data.system.ApkMetadataVerifier
import com.apkhub.app.data.system.DeviceInfoProvider
import com.apkhub.app.data.system.InstalledAppsInspector
import com.apkhub.app.data.system.PackageInstallerController
import com.apkhub.app.worker.NotificationHelper
import com.apkhub.app.worker.UpdateWorkScheduler
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val okHttpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .followSslRedirects(true)
        .build()

    val database: ApkHubDatabase = ApkHubDatabase.build(appContext)
    val settingsDataStore: SettingsDataStore = SettingsDataStore(appContext)
    val deviceInfoProvider: DeviceInfoProvider = DeviceInfoProvider(appContext)
    val installedAppsInspector: InstalledAppsInspector = InstalledAppsInspector(appContext)
    val apkDownloadManager: ApkDownloadManager = ApkDownloadManager(appContext, okHttpClient)
    val packageInstallerController: PackageInstallerController = PackageInstallerController(appContext)
    val apkMetadataVerifier: ApkMetadataVerifier = ApkMetadataVerifier(
        context = appContext,
        installedAppsInspector = installedAppsInspector,
        deviceInfoProvider = deviceInfoProvider
    )

    val gitHubApiClient: GitHubApiClient = GitHubApiClient(httpClient = okHttpClient)

    val appRepository: AppRepository = AppRepository(
        trackedAppDao = database.trackedAppDao(),
        cachedReleaseDao = database.cachedReleaseDao(),
        downloadTaskDao = database.downloadTaskDao(),
        activityHistoryDao = database.activityHistoryDao(),
        settingsDataStore = settingsDataStore,
        gitHubApiClient = gitHubApiClient,
        deviceInfoProvider = deviceInfoProvider,
        installedAppsInspector = installedAppsInspector,
        apkDownloadManager = apkDownloadManager,
        apkMetadataVerifier = apkMetadataVerifier,
        packageInstallerController = packageInstallerController
    )

    val backupManager: BackupManager = BackupManager(
        trackedAppDao = database.trackedAppDao(),
        settingsDataStore = settingsDataStore
    )

    val notificationHelper: NotificationHelper = NotificationHelper(appContext)
    val updateWorkScheduler: UpdateWorkScheduler = UpdateWorkScheduler(appContext)
}
