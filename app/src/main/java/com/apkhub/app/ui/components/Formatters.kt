package com.apkhub.app.ui.components

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0L) return "—"
        val kb = 1024.0
        val mb = kb * 1024.0
        val gb = mb * 1024.0
        return when {
            bytes >= gb -> String.format(Locale.US, "%.2f ГБ", bytes / gb)
            bytes >= mb -> String.format(Locale.US, "%.1f МБ", bytes / mb)
            bytes >= kb -> String.format(Locale.US, "%.0f КБ", bytes / kb)
            else -> "$bytes Б"
        }
    }

    fun formatSpeed(bytesPerSec: Long): String {
        if (bytesPerSec <= 0L) return ""
        return "${formatBytes(bytesPerSec)}/с"
    }

    fun formatTimestamp(epochMs: Long): String {
        if (epochMs <= 0L) return "Никогда"
        val fmt = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
        return fmt.format(Date(epochMs))
    }

    fun formatIsoDate(iso: String?): String {
        if (iso.isNullOrBlank()) return "—"
        return iso.take(10)
    }

    fun formatEpochSeconds(epochSeconds: Long): String {
        if (epochSeconds <= 0L) return "—"
        val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
        return fmt.format(Date(epochSeconds * 1000L))
    }
}
