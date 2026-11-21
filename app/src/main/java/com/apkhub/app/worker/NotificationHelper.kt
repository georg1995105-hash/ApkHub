package com.apkhub.app.worker

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.apkhub.app.MainActivity
import com.apkhub.app.R
import com.apkhub.app.domain.model.TrackedAppUiModel

class NotificationHelper(private val context: Context) {

    fun ensureChannels() {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return
        val channel = NotificationChannel(
            CHANNEL_UPDATES,
            context.getString(R.string.notification_channel_updates_name),
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = context.getString(R.string.notification_channel_updates_desc)
        }
        manager.createNotificationChannel(channel)
    }

    fun hasNotificationPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun showUpdatesAvailableNotification(updates: List<TrackedAppUiModel>) {
        if (updates.isEmpty() || !hasNotificationPermission()) return
        ensureChannels()

        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            1001,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title = if (updates.size == 1) {
            "Доступно обновление: ${updates.first().displayName}"
        } else {
            "Доступно обновлений: ${updates.size}"
        }

        val body = updates.take(4).joinToString(", ") { app ->
            "${app.displayName} ${app.latestTag.orEmpty()}".trim()
        } + if (updates.size > 4) " и ещё ${updates.size - 4}" else ""

        val notification = NotificationCompat.Builder(context, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        try {
            NotificationManagerCompat.from(context).notify(NOTIFICATION_ID_UPDATES, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        const val CHANNEL_UPDATES = "apkhub_updates_channel"
        const val NOTIFICATION_ID_UPDATES = 2001
    }
}
