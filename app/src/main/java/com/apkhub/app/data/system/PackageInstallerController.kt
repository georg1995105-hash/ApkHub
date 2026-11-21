package com.apkhub.app.data.system

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream

data class InstallResultEvent(
    val taskId: Long,
    val appId: Long,
    val packageName: String?,
    val status: Int,
    val statusMessage: String,
    val isSuccess: Boolean,
    val isCancelled: Boolean
)

object InstallEventBus {
    private val _events = MutableSharedFlow<InstallResultEvent>(extraBufferCapacity = 32)
    val events: SharedFlow<InstallResultEvent> = _events.asSharedFlow()

    fun emit(event: InstallResultEvent) {
        _events.tryEmit(event)
    }
}

sealed interface InstallTriggerResult {
    data class SessionCommitted(val sessionId: Int) : InstallTriggerResult
    data object PermissionRequired : InstallTriggerResult
    data class Failed(val message: String) : InstallTriggerResult
}

class PackageInstallerController(private val context: Context) {

    fun canRequestPackageInstalls(): Boolean {
        return context.packageManager.canRequestPackageInstalls()
    }

    fun createUnknownSourcesSettingsIntent(): Intent {
        return Intent(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            Uri.parse("package:${context.packageName}")
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }

    /**
     * Installs an APK via Android's official PackageInstaller.Session API.
     * System installer confirmation is shown natively by Android without bypassing platform security.
     */
    suspend fun installApk(
        apkFile: File,
        taskId: Long,
        appId: Long,
        expectedPackageName: String?
    ): InstallTriggerResult = withContext(Dispatchers.IO) {
        if (!canRequestPackageInstalls()) {
            return@withContext InstallTriggerResult.PermissionRequired
        }
        if (!apkFile.exists() || apkFile.length() <= 0L) {
            return@withContext InstallTriggerResult.Failed("Файл APK не найден на устройстве.")
        }

        val packageInstaller = context.packageManager.packageInstaller
        var session: PackageInstaller.Session? = null
        var sessionId = -1

        try {
            val params = PackageInstaller.SessionParams(
                PackageInstaller.SessionParams.MODE_FULL_INSTALL
            ).apply {
                setSize(apkFile.length())
                if (!expectedPackageName.isNullOrBlank()) {
                    setAppPackageName(expectedPackageName)
                }
            }

            sessionId = packageInstaller.createSession(params)
            session = packageInstaller.openSession(sessionId)

            session.openWrite("base.apk", 0L, apkFile.length()).use { outStream ->
                FileInputStream(apkFile).use { inStream ->
                    val buffer = ByteArray(64 * 1024)
                    var read = inStream.read(buffer)
                    while (read != -1) {
                        outStream.write(buffer, 0, read)
                        read = inStream.read(buffer)
                    }
                }
                session.fsync(outStream)
            }

            val callbackIntent = Intent(context, InstallResultReceiver::class.java).apply {
                action = InstallResultReceiver.ACTION_INSTALL_RESULT
                putExtra(InstallResultReceiver.EXTRA_TASK_ID, taskId)
                putExtra(InstallResultReceiver.EXTRA_APP_ID, appId)
                putExtra(InstallResultReceiver.EXTRA_EXPECTED_PACKAGE, expectedPackageName)
            }

            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                taskId.toInt(),
                callbackIntent,
                flags
            )

            session.commit(pendingIntent.intentSender)
            session.close()
            session = null

            InstallTriggerResult.SessionCommitted(sessionId)
        } catch (e: SecurityException) {
            abandonQuietly(packageInstaller, session, sessionId)
            InstallTriggerResult.PermissionRequired
        } catch (e: Exception) {
            abandonQuietly(packageInstaller, session, sessionId)
            InstallTriggerResult.Failed(
                "Не удалось запустить установщик Android: ${e.localizedMessage ?: "ошибка сессии"}."
            )
        }
    }

    private fun abandonQuietly(
        installer: PackageInstaller,
        session: PackageInstaller.Session?,
        sessionId: Int
    ) {
        try {
            session?.abandon()
            session?.close()
            if (sessionId >= 0) {
                installer.abandonSession(sessionId)
            }
        } catch (_: Exception) {
        }
    }
}
