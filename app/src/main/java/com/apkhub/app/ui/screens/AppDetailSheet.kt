package com.apkhub.app.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkhub.app.domain.logic.ApkAssetSelector
import com.apkhub.app.domain.model.DeviceProfile
import com.apkhub.app.domain.model.ReleaseAsset
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.domain.model.ReleaseInfo
import com.apkhub.app.domain.model.TrackedAppUiModel
import com.apkhub.app.domain.model.UnsupportedPackageFormat
import com.apkhub.app.ui.components.AppAvatarIcon
import com.apkhub.app.ui.components.ChannelBadge
import com.apkhub.app.ui.components.Formatters
import com.apkhub.app.ui.components.StatusPill

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AppDetailSheet(
    app: TrackedAppUiModel,
    releases: List<ReleaseInfo>,
    deviceProfile: DeviceProfile,
    installedDrawable: Drawable?,
    onDismiss: () -> Unit,
    onCheckUpdate: () -> Unit,
    onChangeChannel: (ReleaseChannel) -> Unit,
    onBindPackageName: (String) -> Unit,
    onDownloadAsset: (ReleaseInfo, ReleaseAsset) -> Unit,
    onRemoveApp: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var packageNameInput by remember(app.packageName) { mutableStateOf(app.packageName.orEmpty()) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val channelReleases = remember(releases, app.channel) {
        releases.filter { app.channel.includes(it.channel) }
    }
    var selectedReleaseId by remember(channelReleases) {
        mutableStateOf(channelReleases.firstOrNull()?.id)
    }
    val currentRelease = channelReleases.firstOrNull { it.id == selectedReleaseId }
        ?: channelReleases.firstOrNull()

    val selection = remember(currentRelease, deviceProfile) {
        currentRelease?.let { ApkAssetSelector.selectBestApk(it.assets, deviceProfile) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AppAvatarIcon(
                    displayName = app.displayName,
                    avatarUrl = app.avatarUrl,
                    installedDrawable = installedDrawable,
                    size = 56.dp
                )
                Spacer(modifier = Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = app.displayName,
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = app.fullName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (!app.packageName.isNullOrBlank()) {
                        Text(
                            text = app.packageName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            if (app.description.isNotBlank()) {
                Text(
                    text = app.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Versions & quick action buttons
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Установлено: ${app.installedVersionName ?: "не установлено"}" +
                            (app.installedVersionCode?.let { " (code $it)" } ?: ""),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Последний релиз (${app.channel.label}): ${app.latestTag ?: "—"} (${Formatters.formatIsoDate(app.latestPublishedAt)})",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        text = "Проверено: ${Formatters.formatTimestamp(app.lastCheckedAt)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (currentRelease != null && selection?.recommended != null) {
                    Button(
                        onClick = { onDownloadAsset(currentRelease, selection.recommended) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Download,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (app.isInstalled) "Обновить / Установить" else "Установить APK")
                    }
                }
                OutlinedButton(onClick = onCheckUpdate) {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Проверить")
                }
            }

            HorizontalDivider()

            // Release Channel selector
            Text(
                text = "Канал обновлений",
                style = MaterialTheme.typography.titleSmall
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ReleaseChannel.entries.forEach { channel ->
                    FilterChip(
                        selected = app.channel == channel,
                        onClick = { onChangeChannel(channel) },
                        label = { Text(channel.label) }
                    )
                }
            }

            // Version picker (history of releases)
            if (channelReleases.isNotEmpty()) {
                Text(
                    text = "Версии (${channelReleases.size})",
                    style = MaterialTheme.typography.titleSmall
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    channelReleases.take(10).forEach { rel ->
                        FilterChip(
                            selected = rel.id == currentRelease?.id,
                            onClick = { selectedReleaseId = rel.id },
                            label = { Text(rel.tagName) }
                        )
                    }
                }
            }

            // Selected release assets & compatibility breakdown
            if (currentRelease != null && selection != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "Файлы релиза ${currentRelease.tagName}",
                        style = MaterialTheme.typography.titleSmall
                    )
                    ChannelBadge(currentRelease.channel)
                }

                if (selection.compatibleCandidates.isEmpty()) {
                    Text(
                        text = selection.reason,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                } else {
                    selection.compatibleCandidates.forEach { candidate ->
                        val isRecommended = candidate.asset.id == selection.recommended?.id
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isRecommended) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = candidate.asset.name,
                                        style = MaterialTheme.typography.bodySmall,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${Formatters.formatBytes(candidate.asset.sizeBytes)} • ${candidate.notes}" +
                                            if (isRecommended) " • Рекомендуемый" else "",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Spacer(modifier = Modifier.width(8.dp))
                                FilledTonalButton(
                                    onClick = { onDownloadAsset(currentRelease, candidate.asset) }
                                ) {
                                    Text("Скачать")
                                }
                            }
                        }
                    }
                }

                val unsupportedAssets = currentRelease.assets.filter {
                    UnsupportedPackageFormat.detect(it.name) != null
                }
                if (unsupportedAssets.isNotEmpty()) {
                    Text(
                        text = "Неподдерживаемые форматы (игнорируются в v1): " +
                            unsupportedAssets.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Changelog
                HorizontalDivider()
                Text(
                    text = "Список изменений (${currentRelease.tagName})",
                    style = MaterialTheme.typography.titleSmall
                )
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = currentRelease.body.ifBlank { "Автор не указал описание изменений." },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            HorizontalDivider()

            // Package Name binding
            Text(
                text = "Привязка Package Name",
                style = MaterialTheme.typography.titleSmall
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = packageNameInput,
                    onValueChange = { packageNameInput = it },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                OutlinedButton(
                    onClick = { onBindPackageName(packageNameInput) }
                ) {
                    Text("Сохранить")
                }
            }

            // Delete button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = { showDeleteConfirm = true }) {
                    Icon(
                        imageVector = Icons.Rounded.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Удалить репозиторий",
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить ${app.displayName}?") },
            text = { Text("Репозиторий будет удалён из списка APK HUB. Само установленное приложение на устройстве останется нетронутым.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onRemoveApp()
                    }
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}
