package com.apkhub.app.data.local

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.apkhub.app.BuildConfig
import com.apkhub.app.domain.model.AppThemeMode
import com.apkhub.app.domain.model.RateLimitInfo
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.domain.model.UpdateCheckInterval
import com.apkhub.app.domain.model.UserSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.apkHubDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "apkhub_settings"
)

class SettingsDataStore(private val context: Context) {

    private object Keys {
        val DEFAULT_CHANNEL = stringPreferencesKey("default_channel")
        val CHECK_INTERVAL_HOURS = longPreferencesKey("check_interval_hours")
        val WIFI_ONLY = booleanPreferencesKey("wifi_only_updates")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val GITHUB_TOKEN = stringPreferencesKey("github_token")
        val SELF_UPDATE_REPO = stringPreferencesKey("self_update_repo")
        val AUTO_DELETE_APK = booleanPreferencesKey("auto_delete_apk")
        val RATE_LIMIT_TOTAL = intPreferencesKey("rate_limit_total")
        val RATE_LIMIT_REMAINING = intPreferencesKey("rate_limit_remaining")
        val RATE_LIMIT_RESET = longPreferencesKey("rate_limit_reset")
        val LAST_GLOBAL_CHECK = longPreferencesKey("last_global_check")
    }

    val settingsFlow: Flow<UserSettings> = context.apkHubDataStore.data.map { prefs ->
        UserSettings(
            defaultChannel = ReleaseChannel.fromNameOrDefault(prefs[Keys.DEFAULT_CHANNEL]),
            checkInterval = UpdateCheckInterval.fromHours(prefs[Keys.CHECK_INTERVAL_HOURS] ?: 12L),
            wifiOnlyUpdates = prefs[Keys.WIFI_ONLY] ?: true,
            themeMode = AppThemeMode.fromNameOrDefault(prefs[Keys.THEME_MODE]),
            githubToken = prefs[Keys.GITHUB_TOKEN] ?: "",
            selfUpdateRepo = prefs[Keys.SELF_UPDATE_REPO]?.takeIf { it.isNotBlank() }
                ?: BuildConfig.SELF_UPDATE_REPO,
            autoDeleteApkAfterInstall = prefs[Keys.AUTO_DELETE_APK] ?: true
        )
    }

    val rateLimitFlow: Flow<RateLimitInfo> = context.apkHubDataStore.data.map { prefs ->
        val limit = prefs[Keys.RATE_LIMIT_TOTAL] ?: 60
        val remaining = prefs[Keys.RATE_LIMIT_REMAINING] ?: limit
        val reset = prefs[Keys.RATE_LIMIT_RESET] ?: 0L
        val nowSec = System.currentTimeMillis() / 1000L
        val isLimited = remaining <= 0 && reset > nowSec
        RateLimitInfo(
            limit = limit,
            remaining = if (reset in 1..nowSec) limit else remaining,
            resetEpochSeconds = reset,
            isRateLimited = isLimited
        )
    }

    val lastGlobalCheckFlow: Flow<Long> = context.apkHubDataStore.data.map { prefs ->
        prefs[Keys.LAST_GLOBAL_CHECK] ?: 0L
    }

    suspend fun getSettingsSnapshot(): UserSettings = settingsFlow.first()

    suspend fun setDefaultChannel(channel: ReleaseChannel) {
        context.apkHubDataStore.edit { it[Keys.DEFAULT_CHANNEL] = channel.name }
    }

    suspend fun setCheckInterval(interval: UpdateCheckInterval) {
        context.apkHubDataStore.edit { it[Keys.CHECK_INTERVAL_HOURS] = interval.hours }
    }

    suspend fun setWifiOnly(enabled: Boolean) {
        context.apkHubDataStore.edit { it[Keys.WIFI_ONLY] = enabled }
    }

    suspend fun setThemeMode(mode: AppThemeMode) {
        context.apkHubDataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    suspend fun setGithubToken(token: String) {
        context.apkHubDataStore.edit { it[Keys.GITHUB_TOKEN] = token.trim() }
    }

    suspend fun setSelfUpdateRepo(repo: String) {
        context.apkHubDataStore.edit { it[Keys.SELF_UPDATE_REPO] = repo.trim() }
    }

    suspend fun setAutoDeleteApk(enabled: Boolean) {
        context.apkHubDataStore.edit { it[Keys.AUTO_DELETE_APK] = enabled }
    }

    suspend fun updateRateLimit(limit: Int, remaining: Int, resetEpochSeconds: Long) {
        context.apkHubDataStore.edit {
            it[Keys.RATE_LIMIT_TOTAL] = limit
            it[Keys.RATE_LIMIT_REMAINING] = remaining
            it[Keys.RATE_LIMIT_RESET] = resetEpochSeconds
        }
    }

    suspend fun updateLastGlobalCheck(timestampMs: Long) {
        context.apkHubDataStore.edit { it[Keys.LAST_GLOBAL_CHECK] = timestampMs }
    }

    suspend fun applyImportedSettings(
        defaultChannel: ReleaseChannel,
        checkInterval: UpdateCheckInterval,
        wifiOnlyUpdates: Boolean,
        themeMode: AppThemeMode,
        selfUpdateRepo: String?,
        autoDeleteApkAfterInstall: Boolean
    ) {
        context.apkHubDataStore.edit { prefs ->
            prefs[Keys.DEFAULT_CHANNEL] = defaultChannel.name
            prefs[Keys.CHECK_INTERVAL_HOURS] = checkInterval.hours
            prefs[Keys.WIFI_ONLY] = wifiOnlyUpdates
            prefs[Keys.THEME_MODE] = themeMode.name
            if (!selfUpdateRepo.isNullOrBlank()) {
                prefs[Keys.SELF_UPDATE_REPO] = selfUpdateRepo.trim()
            }
            prefs[Keys.AUTO_DELETE_APK] = autoDeleteApkAfterInstall
        }
    }
}
