package com.apkhub.app

import android.app.Application
import com.apkhub.app.data.system.InstallEventBus
import com.apkhub.app.data.system.PackageChangeReceiver
import com.apkhub.app.di.AppContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class ApkHubApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var packageChangeReceiver: PackageChangeReceiver? = null

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.notificationHelper.ensureChannels()

        // Observe system installer results globally so state updates even if UI is paused
        appScope.launch {
            InstallEventBus.events.collect { event ->
                container.appRepository.handleInstallResultEvent(event)
            }
        }

        // Sync WorkManager schedule with saved user settings
        appScope.launch {
            val settings = container.settingsDataStore.settingsFlow.first()
            container.updateWorkScheduler.schedule(
                interval = settings.checkInterval,
                wifiOnly = settings.wifiOnlyUpdates
            )
            container.appRepository.syncAllInstalledPackages()
        }

        packageChangeReceiver = PackageChangeReceiver.register(this) { pkgName ->
            appScope.launch {
                container.appRepository.syncInstalledPackageByName(pkgName)
            }
        }
    }
}
