package com.apkhub.app.ui.screens

import android.graphics.drawable.Drawable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkhub.app.data.local.ActivityHistoryEntity
import com.apkhub.app.domain.model.DeviceProfile
import com.apkhub.app.domain.model.HistoryEventType
import com.apkhub.app.domain.model.RateLimitInfo
import com.apkhub.app.domain.model.SelfUpdateState
import com.apkhub.app.domain.model.TrackedAppUiModel
import com.apkhub.app.ui.components.AppAvatarIcon
import com.apkhub.app.ui.components.ChannelBadge
import com.apkhub.app.ui.components.ConnectivityAndRateLimitBanner
import com.apkhub.app.ui.components.EmptyStateCard
import com.apkhub.app.ui.components.Formatters
import com.apkhub.app.ui.components.HubCard
import com.apkhub.app.ui.components.SectionHeader
import com.apkhub.app.ui.components.StatusPill

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(
    apps: List<TrackedAppUiModel>,
    recentHistory: List<ActivityHistoryEntity>,
    deviceProfile: DeviceProfile,
    isOnline: Boolean,
    rateLimit: RateLimitInfo,
    lastGlobalCheck: Long,
    isCheckingAll: Boolean,
    isUpdatingAll: Boolean,
    selfUpdateState: SelfUpdateState,
    loadIcon: (String) -> Drawable?,
    onCheckAll: () -> Unit,
    onUpdateAll: () -> Unit,
    onAddRepo: () -> Unit,
    onOpenAppDetail: (Long) -> Unit,
    onDownloadUpdate: (Long) -> Unit,
    onInstallSelfUpdate: () -> Unit
) {
    val updatesAvailable = remember(apps) { apps.filter { it.hasUpdate } }
    val installedCount = remember(apps) { apps.count { it.isInstalled } }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "APK HUB",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "ABI: ${deviceProfile.supportedAbis.firstOrNull() ?: "—"} • Android API ${deviceProfile.sdkInt}" +
                        if (deviceProfile.isAndroidTv) " • Android TV" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (!isOnline || rateLimit.isRateLimited) {
            item {
                ConnectivityAndRateLimitBanner(
                    isOnline = isOnline,
                    rateLimit = rateLimit
                )
            }
        }

        if (selfUpdateState.hasUpdate) {
            item {
                HubCard(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Доступно обновление APK HUB",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${selfUpdateState.currentVersionName} → ${selfUpdateState.latestTag.orEmpty()}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(onClick = onInstallSelfUpdate) {
                            Icon(
                                imageVector = Icons.Rounded.SystemUpdate,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Обновить")
                        }
                    }
                }
            }
        }

        // Overview status card & Quick Actions
        item {
            HubCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = when {
                                apps.isEmpty() -> "Репозитории не добавлены"
                                updatesAvailable.isNotEmpty() -> "Доступно обновлений: ${updatesAvailable.size}"
                                else -> "Все приложения актуальны"
                            },
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Отслеживается: ${apps.size} • Установлено: $installedCount",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Последняя проверка: ${Formatters.formatTimestamp(lastGlobalCheck)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (updatesAvailable.isNotEmpty()) {
                        StatusPill(
                            text = "${updatesAvailable.size} нов.",
                            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                            contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onCheckAll,
                        enabled = !isCheckingAll && apps.isNotEmpty()
                    ) {
                        if (isCheckingAll) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (isCheckingAll) "Проверка..." else "Проверить все")
                    }

                    if (updatesAvailable.isNotEmpty()) {
                        FilledTonalButton(
                            onClick = onUpdateAll,
                            enabled = !isUpdatingAll
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Download,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isUpdatingAll) "Обновление..." else "Обновить все")
                        }
                    }

                    OutlinedButton(onClick = onAddRepo) {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Добавить GitHub")
                    }
                }
            }
        }

        // Available Updates list
        item {
            SectionHeader(
                title = "Доступные обновления",
                subtitle = if (updatesAvailable.isEmpty()) "Новые релизы появятся здесь" else null
            )
        }

        if (updatesAvailable.isEmpty()) {
            item {
                EmptyStateCard(
                    icon = Icons.Rounded.CheckCircle,
                    title = if (apps.isEmpty()) "Начните с добавления репозитория" else "Обновлений нет",
                    message = if (apps.isEmpty()) {
                        "Добавьте любой публичный GitHub-репозиторий с Android APK — APK HUB подберёт совместимую сборку."
                    } else {
                        "Все отслеживаемые приложения соответствуют последним релизам выбранных каналов."
                    },
                    action = if (apps.isEmpty()) {
                        {
                            Button(onClick = onAddRepo) {
                                Text("Добавить репозиторий")
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        } else {
            items(updatesAvailable, key = { it.id }) { app ->
                val iconDrawable = remember(app.packageName) {
                    app.packageName?.let(loadIcon)
                }
                HubCard(onClick = { onOpenAppDetail(app.id) }) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AppAvatarIcon(
                            displayName = app.displayName,
                            avatarUrl = app.avatarUrl,
                            installedDrawable = iconDrawable
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(
                                    text = app.displayName,
                                    style = MaterialTheme.typography.titleMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f, fill = false)
                                )
                                ChannelBadge(app.channel)
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "${app.installedVersionName ?: "—"} → ${app.latestTag ?: "—"} • ${Formatters.formatBytes(app.latestAssetSizeBytes)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.tertiary
                            )
                            if (!app.latestAssetAbiNote.isNullOrBlank()) {
                                Text(
                                    text = app.latestAssetAbiNote,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        FilledTonalButton(onClick = { onDownloadUpdate(app.id) }) {
                            Text("Обновить")
                        }
                    }
                }
            }
        }

        // Recent Activity preview
        if (recentHistory.isNotEmpty()) {
            item {
                SectionHeader(title = "Недавние события")
            }
            items(recentHistory.take(4), key = { it.id }) { event ->
                val type = HistoryEventType.entries.firstOrNull { it.name == event.eventType }
                HubCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${event.appName} ${event.versionTag.orEmpty()}".trim(),
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = event.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = type?.label ?: event.eventType,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = Formatters.formatTimestamp(event.timestamp),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
