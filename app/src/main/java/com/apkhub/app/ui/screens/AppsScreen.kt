package com.apkhub.app.ui.screens

import android.graphics.drawable.Drawable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Clear
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.domain.model.TrackedAppUiModel
import com.apkhub.app.ui.AppsFilter
import com.apkhub.app.ui.components.AppAvatarIcon
import com.apkhub.app.ui.components.ChannelBadge
import com.apkhub.app.ui.components.EmptyStateCard
import com.apkhub.app.ui.components.Formatters
import com.apkhub.app.ui.components.HubCard
import com.apkhub.app.ui.components.StatusPill

@Composable
fun AppsScreen(
    apps: List<TrackedAppUiModel>,
    searchQuery: String,
    selectedFilter: AppsFilter,
    checkingAppIds: Set<Long>,
    loadIcon: (String) -> Drawable?,
    onSearchChange: (String) -> Unit,
    onFilterChange: (AppsFilter) -> Unit,
    onAddRepo: () -> Unit,
    onOpenDetail: (Long) -> Unit,
    onCheckSingle: (Long) -> Unit,
    onDownloadInstall: (Long) -> Unit
) {
    val filteredApps = remember(apps, searchQuery, selectedFilter) {
        val q = searchQuery.trim().lowercase()
        apps.filter { app ->
            val matchesQuery = q.isEmpty() ||
                app.displayName.lowercase().contains(q) ||
                app.fullName.lowercase().contains(q) ||
                (app.packageName?.lowercase()?.contains(q) == true)

            val matchesFilter = when (selectedFilter) {
                AppsFilter.ALL -> true
                AppsFilter.UPDATES -> app.hasUpdate
                AppsFilter.INSTALLED -> app.isInstalled
                AppsFilter.STABLE -> app.channel == ReleaseChannel.STABLE
                AppsFilter.BETA -> app.channel == ReleaseChannel.BETA
                AppsFilter.TEST -> app.channel == ReleaseChannel.TEST
            }
            matchesQuery && matchesFilter
        }
    }

    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 330.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Приложения",
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = "Каталог GitHub-репозиториев (${apps.size})",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Button(onClick = onAddRepo) {
                    Icon(
                        imageVector = Icons.Rounded.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Добавить")
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchChange,
                placeholder = { Text("Поиск по названию, owner/repo или packageName") },
                leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { onSearchChange("") }) {
                            Icon(Icons.Rounded.Clear, contentDescription = "Очистить")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            )
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(AppsFilter.entries) { filter ->
                    FilterChip(
                        selected = selectedFilter == filter,
                        onClick = { onFilterChange(filter) },
                        label = { Text(filter.label) }
                    )
                }
            }
        }

        if (filteredApps.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                EmptyStateCard(
                    icon = Icons.Rounded.Apps,
                    title = if (apps.isEmpty()) "Каталог пуст" else "Ничего не найдено",
                    message = if (apps.isEmpty()) {
                        "Добавьте ссылку на публичный GitHub-репозиторий с Android-релизами."
                    } else {
                        "Измените фильтр или поисковый запрос."
                    },
                    action = if (apps.isEmpty()) {
                        {
                            Button(onClick = onAddRepo) {
                                Text("Добавить GitHub-ссылку")
                            }
                        }
                    } else {
                        null
                    }
                )
            }
        } else {
            items(filteredApps, key = { it.id }) { app ->
                AppCatalogCard(
                    app = app,
                    isChecking = app.id in checkingAppIds,
                    installedDrawable = remember(app.packageName) { app.packageName?.let(loadIcon) },
                    onClick = { onOpenDetail(app.id) },
                    onRefresh = { onCheckSingle(app.id) },
                    onAction = { onDownloadInstall(app.id) }
                )
            }
        }
    }
}

@Composable
private fun AppCatalogCard(
    app: TrackedAppUiModel,
    isChecking: Boolean,
    installedDrawable: Drawable?,
    onClick: () -> Unit,
    onRefresh: () -> Unit,
    onAction: () -> Unit
) {
    HubCard(onClick = onClick) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppAvatarIcon(
                displayName = app.displayName,
                avatarUrl = app.avatarUrl,
                installedDrawable = installedDrawable
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
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
                Text(
                    text = app.fullName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            IconButton(onClick = onRefresh, enabled = !isChecking) {
                if (isChecking) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Rounded.Refresh,
                        contentDescription = "Проверить обновления"
                    )
                }
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
                    text = "Установлено: ${app.installedVersionName ?: "не установлено"}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Релиз: ${app.latestTag ?: "—"} • ${Formatters.formatBytes(app.latestAssetSizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (app.hasUpdate) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            val canDownload = !app.latestAssetUrl.isNullOrBlank()
            when {
                app.hasUpdate && canDownload -> {
                    Button(onClick = onAction) {
                        Text("Обновить")
                    }
                }
                !app.isInstalled && canDownload -> {
                    FilledTonalButton(onClick = onAction) {
                        Text("Установить")
                    }
                }
                canDownload -> {
                    StatusPill(
                        text = "Актуально",
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                else -> {
                    StatusPill(
                        text = "Нет APK",
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        if (!app.unsupportedFormatHint.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.unsupportedFormatHint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        } else if (!app.lastError.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = app.lastError,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
