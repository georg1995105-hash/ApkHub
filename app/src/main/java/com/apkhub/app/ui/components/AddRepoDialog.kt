package com.apkhub.app.ui.components

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.ui.AddRepoDialogState

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun AddRepoDialog(
    state: AddRepoDialogState,
    onDismiss: () -> Unit,
    onUrlChange: (String) -> Unit,
    onChannelChange: (ReleaseChannel) -> Unit,
    onCustomNameChange: (String) -> Unit,
    onPackageNameChange: (String) -> Unit,
    onPreviewClick: () -> Unit,
    onSubmitClick: () -> Unit
) {
    if (!state.isOpen) return

    AlertDialog(
        onDismissRequest = { if (!state.isSubmitting) onDismiss() },
        shape = RoundedCornerShape(20.dp),
        title = {
            Text(
                text = "Добавить GitHub-репозиторий",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = state.urlInput,
                    onValueChange = onUrlChange,
                    label = { Text("Ссылка GitHub или owner/repo") },
                    placeholder = { Text("https://github.com/owner/app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = "Канал релизов",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    ReleaseChannel.entries.forEach { channel ->
                        FilterChip(
                            selected = state.selectedChannel == channel,
                            onClick = { onChannelChange(channel) },
                            label = { Text(channel.label) }
                        )
                    }
                }

                OutlinedButton(
                    onClick = onPreviewClick,
                    enabled = !state.isLoadingPreview && !state.isSubmitting && state.urlInput.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (state.isLoadingPreview) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Проверка репозитория...")
                    } else {
                        Text("Проверить релизы и APK")
                    }
                }

                if (state.preview != null) {
                    val preview = state.preview
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${preview.owner}/${preview.repo}",
                                style = MaterialTheme.typography.titleSmall
                            )
                            if (preview.description.isNotBlank()) {
                                Text(
                                    text = preview.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2
                                )
                            }
                            Text(
                                text = "Релизов: ${preview.releaseCount} • Последний: ${preview.latestTag ?: "—"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (preview.recommendedApkName != null) {
                                Text(
                                    text = "APK: ${preview.recommendedApkName} (${Formatters.formatBytes(preview.recommendedApkSize)})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Text(
                                text = preview.selectionNote,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = state.customName,
                    onValueChange = onCustomNameChange,
                    label = { Text("Название (необязательно)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = state.packageName,
                    onValueChange = onPackageNameChange,
                    label = { Text("Package Name (определится сам после загрузки)") },
                    placeholder = { Text("com.example.app") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (!state.error.isNullOrBlank()) {
                    Text(
                        text = state.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onSubmitClick,
                enabled = !state.isSubmitting && state.urlInput.isNotBlank()
            ) {
                if (state.isSubmitting) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Добавление...")
                    }
                } else {
                    Text("Добавить")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !state.isSubmitting
            ) {
                Text("Отмена")
            }
        }
    )
}
