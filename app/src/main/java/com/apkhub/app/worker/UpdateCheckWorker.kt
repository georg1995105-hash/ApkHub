package com.apkhub.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.apkhub.app.ApkHubApplication

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as? ApkHubApplication ?: return Result.failure()
        val container = app.container

        if (!container.deviceInfoProvider.isOnline()) {
            return Result.retry()
        }

        val summary = container.appRepository.checkAllUpdates()
        if (summary.updatesFound.isNotEmpty()) {
            container.notificationHelper.showUpdatesAvailableNotification(summary.updatesFound)
        }

        return if (summary.rateLimited) {
            Result.retry()
        } else {
            Result.success()
        }
    }
}
