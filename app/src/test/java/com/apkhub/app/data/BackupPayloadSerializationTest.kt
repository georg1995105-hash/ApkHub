package com.apkhub.app.data

import com.apkhub.app.data.repository.BackupAppItem
import com.apkhub.app.data.repository.BackupPayload
import com.apkhub.app.data.repository.BackupSettingsItem
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupPayloadSerializationTest {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    @Test
    fun roundTripsBackupPayloadCleanly() {
        val original = BackupPayload(
            schemaVersion = BackupPayload.CURRENT_SCHEMA_VERSION,
            exportedAtEpochMs = 1763760000000L,
            apps = listOf(
                BackupAppItem(
                    owner = "ownerA",
                    repo = "appA",
                    displayName = "App A",
                    packageName = "com.example.appa",
                    channel = "BETA"
                )
            ),
            settings = BackupSettingsItem(
                defaultChannel = "STABLE",
                checkIntervalHours = 24L,
                wifiOnlyUpdates = true,
                themeMode = "GRAPHITE",
                selfUpdateRepo = "apkhub-android/apk-hub",
                autoDeleteApkAfterInstall = true
            )
        )

        val encoded = json.encodeToString(original)
        val decoded = json.decodeFromString<BackupPayload>(encoded)

        assertEquals(original.schemaVersion, decoded.schemaVersion)
        assertEquals(1, decoded.apps.size)
        assertEquals("ownerA", decoded.apps.first().owner)
        assertEquals("BETA", decoded.apps.first().channel)
        assertEquals("GRAPHITE", decoded.settings?.themeMode)
        assertEquals(24L, decoded.settings?.checkIntervalHours)
    }
}
