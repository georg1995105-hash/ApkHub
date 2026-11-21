package com.apkhub.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.apkhub.app.domain.model.AppThemeMode
import com.apkhub.app.domain.model.RateLimitInfo
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.domain.model.SelfUpdateState
import com.apkhub.app.domain.model.UpdateCheckInterval
import com.apkhub.app.domain.model.UserSettings
import com.apkhub.app.ui.components.Formatters
import com.apkhub.app.ui.components.HubCard
import com.apkhub.app.ui.components.StatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: UserSettings,
    rateLimit: RateLimitInfo,
    selfUpdateState: SelfUpdateState,
    canInstallPackages: Boolean,
    hasNotificationPermission: Boolean,
    apkCacheSizeBytes: Long,
    onThemeChange: (AppThemeMode) -> Unit,
    onDefaultChannelChange: (ReleaseChannel) -> Unit,
    onIntervalChange: (UpdateCheckInterval) -> Unit,
    onWifiOnlyChange: (Boolean) -> Unit,
    onAutoDeleteApkChange: (Boolean) -> Unit,
    onSaveGithubToken: (String) -> Unit,
    onSaveSelfUpdateRepo: (String) -> Unit,
    onCheckSelfUpdate: () -> Unit,
    onInstallSelfUpdate: () -> Unit,
    onExportBackup: () -> Unit,
    onImportBackup: () -> Unit,
    onClearApkCache: () -> Unit,
    onOpenInstallPermission: () -> Unit,
    onRequestNotificationPermission: () -> Unit
) {
    var tokenInput by remember(settings.githubToken) { mutableStateOf(settings.githubToken) }
    var selfRepoInput by remember(settings.selfUpdateRepo) { mutableStateOf(settings.selfUpdateRepo) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column {
                Text(
                    text = "Настройки",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Оформление, каналы релизов, проверки и управление данными",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Appearance
        item {
            HubCard {
                Text(
                    text = "Оформление",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    AppThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode,
                            onClick = { onThemeChange(mode) },
                            label = { Text(mode.label) }
                        )
                    }
                }
            }
        }

        // Channels & Background checks
        item {
            HubCard {
                Text(
                    text = "Канал по умолчанию",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Применяется при добавлении новых репозиториев и проверке обновлений APK HUB.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ReleaseChannel.entries.forEach { channel ->
                        FilterChip(
                            selected = settings.defaultChannel == channel,
                            onClick = { onDefaultChannelChange(channel) },
                            label = { Text(channel.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Фоновая проверка обновлений",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    UpdateCheckInterval.entries.forEach { interval ->
                        FilterChip(
                            selected = settings.checkInterval == interval,
                            onClick = { onIntervalChange(interval) },
                            label = { Text(interval.label) }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Проверять в фоне только по Wi-Fi",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Экономит мобильный трафик при фоновой синхронизации",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.wifiOnlyUpdates,
                        onCheckedChange = onWifiOnlyChange
                    )
                }
            }
        }

        // GitHub API & Rate limit
        item {
            HubCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "GitHub API и лимиты",
                        style = MaterialTheme.typography.titleMedium
                    )
                    StatusPill(
                        text = "${rateLimit.remaining} / ${rateLimit.limit}",
                        containerColor = if (rateLimit.isRateLimited) {
                            MaterialTheme.colorScheme.errorContainer
                        } else {
                            MaterialTheme.colorScheme.secondaryContainer
                        },
                        contentColor = if (rateLimit.isRateLimited) {
                            MaterialTheme.colorScheme.onErrorContainer
                        } else {
                            MaterialTheme.colorScheme.onSecondaryContainer
                        }
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Без токена GitHub ограничивает до 60 запросов/час. Personal Access Token (без дополнительных прав) увеличивает лимит до 5000/час.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = tokenInput,
                    onValueChange = { tokenInput = it },
                    label = { Text("GitHub Personal Access Token (необязательно)") },
                    leadingIcon = { Icon(Icons.Rounded.Key, contentDescription = null) },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { onSaveGithubToken(tokenInput) }) {
                        Text("Сохранить токен")
                    }
                    if (settings.githubToken.isNotBlank()) {
                        OutlinedButton(onClick = {
                            tokenInput = ""
                            onSaveGithubToken("")
                        }) {
                            Text("Очистить")
                        }
                    }
                }
            }
        }

        // Permissions & Security
        item {
            HubCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Rounded.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Системные разрешения",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Установка неизвестных приложений",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (canInstallPackages) "Разрешено" else "Требуется для штатной установки APK",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (canInstallPackages) {
                                MaterialTheme.colorScheme.tertiary
                            } else {
                                MaterialTheme.colorScheme.error
                            }
                        )
                    }
                    OutlinedButton(onClick = onOpenInstallPermission) {
                        Text(if (canInstallPackages) "Настройки" else "Разрешить")
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Уведомления об обновлениях",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = if (hasNotificationPermission) "Разрешено" else "Отключено в системе",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (!hasNotificationPermission) {
                        OutlinedButton(onClick = onRequestNotificationPermission) {
                            Text("Включить")
                        }
                    }
                }
            }
        }

        // Data management: Export / Import / Cache
        item {
            HubCard {
                Text(
                    text = "Данные и резервные копии",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Экспорт сохраняет список репозиториев, каналы и настройки в JSON (GitHub Token не включается в экспорт в целях безопасности).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    FilledTonalButton(onClick = onExportBackup) {
                        Icon(
                            imageVector = Icons.Rounded.Upload,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Экспорт JSON")
                    }
                    OutlinedButton(onClick = onImportBackup) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Импорт JSON")
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Удалять APK после установки",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "Размер кеша APK: ${Formatters.formatBytes(apkCacheSizeBytes)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.autoDeleteApkAfterInstall,
                        onCheckedChange = onAutoDeleteApkChange
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(onClick = onClearApkCache) {
                    Icon(
                        imageVector = Icons.Rounded.CleaningServices,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Очистить скачанные APK")
                }
            }
        }

        // Self-Update APK HUB
        item {
            HubCard {
                Text(
                    text = "Обновление APK HUB",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "Текущая версия: ${selfUpdateState.currentVersionName} (${selfUpdateState.currentVersionCode})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = selfRepoInput,
                    onValueChange = { selfRepoInput = it },
                    label = { Text("GitHub-репозиторий APK HUB (owner/repo)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(onClick = { onSaveSelfUpdateRepo(selfRepoInput) }) {
                        Text("Сохранить репозиторий")
                    }
                    FilledTonalButton(
                        onClick = onCheckSelfUpdate,
                        enabled = !selfUpdateState.isChecking
                    ) {
                        if (selfUpdateState.isChecking) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Проверить обновление")
                    }
                    if (selfUpdateState.hasUpdate) {
                        Button(onClick = onInstallSelfUpdate) {
                            Text("Установить ${selfUpdateState.latestTag.orEmpty()}")
                        }
                    }
                }
                if (!selfUpdateState.lastError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = selfUpdateState.lastError,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
