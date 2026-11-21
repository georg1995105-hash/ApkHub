package com.apkhub.app.data.repository

import com.apkhub.app.data.local.SettingsDataStore
import com.apkhub.app.data.local.TrackedAppDao
import com.apkhub.app.data.local.TrackedAppEntity
import com.apkhub.app.domain.model.AppThemeMode
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.domain.model.UpdateCheckInterval
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

@Serializable
data class BackupPayload(
    val schemaVersion: Int = CURRENT_SCHEMA_VERSION,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val apps: List<BackupAppItem> = emptyList(),
    val settings: BackupSettingsItem? = null
) {
    companion object {
        const val CURRENT_SCHEMA_VERSION = 1
    }
}

@Serializable
data class BackupAppItem(
    val owner: String,
    val repo: String,
    val displayName: String,
    val packageName: String? = null,
    val channel: String = ReleaseChannel.STABLE.name
)

@Serializable
data class BackupSettingsItem(
    val defaultChannel: String = ReleaseChannel.STABLE.name,
    val checkIntervalHours: Long = UpdateCheckInterval.HOURS_12.hours,
    val wifiOnlyUpdates: Boolean = true,
    val themeMode: String = AppThemeMode.GRAPHITE.name,
    val selfUpdateRepo: String? = null,
    val autoDeleteApkAfterInstall: Boolean = true
)

data class ImportSummary(
    val addedApps: Int,
    val skippedExisting: Int,
    val settingsApplied: Boolean
)

class BackupManager(
    private val trackedAppDao: TrackedAppDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportToJson(): String {
        val apps = trackedAppDao.getAllOnce().map { entity ->
            BackupAppItem(
                owner = entity.owner,
                repo = entity.repo,
                displayName = entity.displayName,
                packageName = entity.packageName,
                channel = entity.channel
            )
        }
        val settings = settingsDataStore.getSettingsSnapshot()
        val payload = BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = System.currentTimeMillis(),
            apps = apps,
            settings = BackupSettingsItem(
                defaultChannel = settings.defaultChannel.name,
                checkIntervalHours = settings.checkInterval.hours,
                wifiOnlyUpdates = settings.wifiOnlyUpdates,
                themeMode = settings.themeMode.name,
                selfUpdateRepo = settings.selfUpdateRepo,
                autoDeleteApkAfterInstall = settings.autoDeleteApkAfterInstall
            )
        )
        return json.encodeToString(payload)
    }

    fun parseBackupJson(rawJson: String): Result<BackupPayload> {
        return try {
            val payload = json.decodeFromString<BackupPayload>(rawJson)
            if (payload.schemaVersion <= 0 || payload.schemaVersion > BackupPayload.CURRENT_SCHEMA_VERSION) {
                Result.failure(
                    IllegalArgumentException("Неподдерживаемая версия файла резервной копии: ${payload.schemaVersion}")
                )
            } else {
                Result.success(payload)
            }
        } catch (e: Exception) {
            Result.failure(
                IllegalArgumentException("Файл повреждён или не является экспортом APK HUB: ${e.message}")
            )
        }
    }

    suspend fun importFromJson(rawJson: String): Result<ImportSummary> {
        val parsed = parseBackupJson(rawJson).getOrElse { return Result.failure(it) }
        var added = 0
        var skipped = 0

        for (item in parsed.apps) {
            if (item.owner.isBlank() || item.repo.isBlank()) continue
            val existing = trackedAppDao.findByRepo(item.owner.trim(), item.repo.trim())
            if (existing != null) {
                skipped++
            } else {
                val channel = ReleaseChannel.fromNameOrDefault(item.channel)
                trackedAppDao.insert(
                    TrackedAppEntity(
                        owner = item.owner.trim(),
                        repo = item.repo.trim(),
                        displayName = item.displayName.ifBlank { item.repo.trim() },
                        packageName = item.packageName?.takeIf { it.isNotBlank() },
                        channel = channel.name
                    )
                )
                added++
            }
        }

        var settingsApplied = false
        val s = parsed.settings
        if (s != null) {
            settingsDataStore.applyImportedSettings(
                defaultChannel = ReleaseChannel.fromNameOrDefault(s.defaultChannel),
                checkInterval = UpdateCheckInterval.fromHours(s.checkIntervalHours),
                wifiOnlyUpdates = s.wifiOnlyUpdates,
                themeMode = AppThemeMode.fromNameOrDefault(s.themeMode),
                selfUpdateRepo = s.selfUpdateRepo,
                autoDeleteApkAfterInstall = s.autoDeleteApkAfterInstall
            )
            settingsApplied = true
        }

        return Result.success(
            ImportSummary(
                addedApps = added,
                skippedExisting = skipped,
                settingsApplied = settingsApplied
            )
        )
    }
}
