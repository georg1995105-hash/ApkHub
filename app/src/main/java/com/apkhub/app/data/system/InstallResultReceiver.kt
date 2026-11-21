package com.apkhub.app.data.system

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build

class InstallResultReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_INSTALL_RESULT) return

        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L)
        val appId = intent.getLongExtra(EXTRA_APP_ID, -1L)
        val expectedPkg = intent.getStringExtra(EXTRA_EXPECTED_PACKAGE)
        val installedPkg = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME) ?: expectedPkg
        val status = intent.getIntExtra(
            PackageInstaller.EXTRA_STATUS,
            PackageInstaller.STATUS_FAILURE
        )
        val rawMsg = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE).orEmpty()

        when (status) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                val confirmIntent = extractConfirmIntent(intent)
                if (confirmIntent != null) {
                    confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirmIntent)
                    } catch (e: Exception) {
                        InstallEventBus.emit(
                            InstallResultEvent(
                                taskId = taskId,
                                appId = appId,
                                packageName = installedPkg,
                                status = PackageInstaller.STATUS_FAILURE,
                                statusMessage = "Не удалось открыть системное окно подтверждения установки: ${e.localizedMessage}",
                                isSuccess = false,
                                isCancelled = false
                            )
                        )
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> {
                InstallEventBus.emit(
                    InstallResultEvent(
                        taskId = taskId,
                        appId = appId,
                        packageName = installedPkg,
                        status = status,
                        statusMessage = "Приложение успешно установлено.",
                        isSuccess = true,
                        isCancelled = false
                    )
                )
            }
            PackageInstaller.STATUS_FAILURE_ABORTED -> {
                InstallEventBus.emit(
                    InstallResultEvent(
                        taskId = taskId,
                        appId = appId,
                        packageName = installedPkg,
                        status = status,
                        statusMessage = "Установка отменена пользователем.",
                        isSuccess = false,
                        isCancelled = true
                    )
                )
            }
            else -> {
                val readable = mapFailureMessage(status, rawMsg)
                InstallEventBus.emit(
                    InstallResultEvent(
                        taskId = taskId,
                        appId = appId,
                        packageName = installedPkg,
                        status = status,
                        statusMessage = readable,
                        isSuccess = false,
                        isCancelled = false
                    )
                )
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun extractConfirmIntent(intent: Intent): Intent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
        } else {
            intent.getParcelableExtra(Intent.EXTRA_INTENT)
        }
    }

    private fun mapFailureMessage(status: Int, rawMsg: String): String = when (status) {
        PackageInstaller.STATUS_FAILURE_BLOCKED ->
            "Установка заблокирована системой или политикой безопасности."
        PackageInstaller.STATUS_FAILURE_CONFLICT ->
            "Конфликт с установленным приложением (вероятно, несовпадение подписи)."
        PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
            "Приложение несовместимо с этим устройством или версией Android."
        PackageInstaller.STATUS_FAILURE_INVALID ->
            "Пакет APK повреждён или имеет некорректную структуру."
        PackageInstaller.STATUS_FAILURE_STORAGE ->
            "Недостаточно свободного места на устройстве для установки."
        else ->
            if (rawMsg.isNotBlank()) "Ошибка установки: $rawMsg" else "Не удалось установить приложение."
    }

    companion object {
        const val ACTION_INSTALL_RESULT = "com.apkhub.app.ACTION_INSTALL_RESULT"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_APP_ID = "extra_app_id"
        const val EXTRA_EXPECTED_PACKAGE = "extra_expected_package"
    }
}
