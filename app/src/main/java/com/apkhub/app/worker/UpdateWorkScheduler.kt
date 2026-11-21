package com.apkhub.app.worker

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.apkhub.app.domain.model.UpdateCheckInterval
import java.util.concurrent.TimeUnit

class UpdateWorkScheduler(private val context: Context) {

    fun schedule(interval: UpdateCheckInterval, wifiOnly: Boolean) {
        val workManager = WorkManager.getInstance(context)
        if (interval == UpdateCheckInterval.OFF || interval.hours <= 0L) {
            workManager.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val constraints = Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(
            interval.hours,
            TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.MINUTES)
            .build()

        workManager.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "apkhub_periodic_update_check"
    }
}
