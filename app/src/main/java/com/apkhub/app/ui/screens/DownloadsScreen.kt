package com.apkhub.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkhub.app.data.local.ActivityHistoryEntity
import com.apkhub.app.data.local.DownloadTaskEntity
import com.apkhub.app.data.system.ActiveDownloadProgress
import com.apkhub.app.domain.model.DownloadStatus
import com.apkhub.app.domain.model.HistoryEventType
import com.apkhub.app.ui.components.EmptyStateCard
import com.apkhub.app.ui.components.Formatters
import com.apkhub.app.ui.components.HubCard
import com.apkhub.app.ui.components.StatusPill
import com.apkhub.app.ui.theme.StatusWarning

@Composable
fun DownloadsScreen(
    tasks: List<DownloadTaskEntity>,
    progressMap: Map<Long, ActiveDownloadProgress>,
    history: List<ActivityHistoryEntity>,
    onCancelDownload: (Long) -> Unit,
    onRetryDownload: (Long) -> Unit,
    onInstallReady: (Long) -> Unit,
    onClearFinishedDownloads: () -> Unit,
    onClearHistory: () -> Unit
) {
    var activeTab by rememberSaveable { mutableIntStateOf(0) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Загрузки",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Управление загрузками APK и журнал установок",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                TextButton(
                    onClick = if (activeTab == 0) onClearFinishedDownloads else onClearHistory
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DeleteSweep,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Очистить")
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = activeTab == 0,
                    onClick = { activeTab = 0 },
                    label = { Text("Загрузки (${tasks.size})") }
                )
                FilterChip(
                    selected = activeTab == 1,
                    onClick = { activeTab = 1 },
                    label = { Text("История (${history.size})") }
                )
            }
        }

        if (activeTab == 0) {
            if (tasks.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.Download,
                        title = "Загрузок пока нет",
                        message = "Скачанные APK, их прогресс и результаты проверки целостности будут отображаться здесь."
                    )
                }
            } else {
                items(tasks, key = { it.id }) { task ->
                    DownloadTaskCard(
                        task = task,
                        liveProgress = progressMap[task.id],
                        onCancel = { onCancelDownload(task.id) },
                        onRetry = { onRetryDownload(task.id) },
                        onInstall = { onInstallReady(task.id) }
                    )
                }
            }
        } else {
            if (history.isEmpty()) {
                item {
                    EmptyStateCard(
                        icon = Icons.Rounded.History,
                        title = "История пуста",
                        message = "Здесь фиксируются найденные обновления, загрузки, проверки подписи и результаты установки."
                    )
                }
            } else {
                items(history, key = { it.id }) { item ->
                    HistoryEventCard(event = item)
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTaskEntity,
    liveProgress: ActiveDownloadProgress?,
    onCancel: () -> Unit,
    onRetry: () -> Unit,
    onInstall: () -> Unit
) {
    val status = DownloadStatus.entries.firstOrNull { it.name == task.status }
        ?: DownloadStatus.FAILED

    val (pillBg, pillFg) = when (status) {
        DownloadStatus.INSTALLED ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        DownloadStatus.READY_TO_INSTALL, DownloadStatus.DOWNLOADING, DownloadStatus.VERIFYING, DownloadStatus.INSTALLING ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        DownloadStatus.FAILED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        DownloadStatus.CANCELLED, DownloadStatus.QUEUED ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    HubCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${task.appName} ${task.versionTag}".trim(),
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = task.assetName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(modifier = Modifier.width(8.dp))
            StatusPill(text = status.label, containerColor = pillBg, contentColor = pillFg)
        }

        if (status == DownloadStatus.DOWNLOADING) {
            Spacer(modifier = Modifier.height(10.dp))
            val downloaded = liveProgress?.downloadedBytes ?: task.downloadedBytes
            val total = liveProgress?.totalBytes?.takeIf { it > 0L } ?: task.expectedSizeBytes
            if (total > 0L) {
                val fraction = (downloaded.toFloat() / total.toFloat()).coerceIn(0f, 1f)
                LinearProgressIndicator(
                    progress = { fraction },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(4.dp))
                val speed = Formatters.formatSpeed(liveProgress?.speedBytesPerSec ?: 0L)
                Text(
                    text = "${Formatters.formatBytes(downloaded)} / ${Formatters.formatBytes(total)}" +
                        if (speed.isNotEmpty()) " • $speed" else "",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
        } else if (status == DownloadStatus.VERIFYING || status == DownloadStatus.INSTALLING) {
            Spacer(modifier = Modifier.height(10.dp))
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        if (!task.verificationNote.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = task.verificationNote,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }

        if (!task.errorMessage.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = task.errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (status == DownloadStatus.FAILED) {
                    MaterialTheme.colorScheme.error
                } else {
                    StatusWarning
                }
            )
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = Formatters.formatTimestamp(task.updatedAt),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                when (status) {
                    DownloadStatus.DOWNLOADING, DownloadStatus.QUEUED -> {
                        OutlinedButton(onClick = onCancel) {
                            Icon(
                                imageVector = Icons.Rounded.Cancel,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Отмена")
                        }
                    }
                    DownloadStatus.READY_TO_INSTALL -> {
                        Button(onClick = onInstall) {
                            Icon(
                                imageVector = Icons.Rounded.InstallMobile,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Установить")
                        }
                    }
                    DownloadStatus.FAILED, DownloadStatus.CANCELLED -> {
                        OutlinedButton(onClick = onRetry) {
                            Icon(
                                imageVector = Icons.Rounded.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Повторить")
                        }
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun HistoryEventCard(event: ActivityHistoryEntity) {
    val type = HistoryEventType.entries.firstOrNull { it.name == event.eventType }
    val (bg, fg) = when (type) {
        HistoryEventType.INSTALL_SUCCESS, HistoryEventType.DOWNLOAD_COMPLETED ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
        HistoryEventType.INSTALL_FAILED, HistoryEventType.DOWNLOAD_FAILED, HistoryEventType.VERIFICATION_BLOCKED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
        HistoryEventType.UPDATE_FOUND ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        else ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    HubCard {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${event.appName} ${event.versionTag.orEmpty()}".trim(),
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.weight(1f)
            )
            StatusPill(
                text = type?.label ?: event.eventType,
                containerColor = bg,
                contentColor = fg
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = event.message,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = event.packageName ?: event.repoFullName,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = Formatters.formatTimestamp(event.timestamp),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (!event.sha256.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "SHA-256: ${event.sha256.take(16)}…",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
