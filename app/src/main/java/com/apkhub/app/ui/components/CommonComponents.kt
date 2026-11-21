package com.apkhub.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.apkhub.app.domain.model.RateLimitInfo
import com.apkhub.app.domain.model.ReleaseChannel
import com.apkhub.app.ui.theme.StatusWarning

/**
 * TV / D-Pad & Desktop friendly focusable container card with clear focus ring.
 */
@Composable
fun HubCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(16.dp),
    containerColor: Color = MaterialTheme.colorScheme.surface,
    content: @Composable ColumnScope.() -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val borderColor = if (isFocused) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isFocused) 2.dp else 1.dp

    val cardModifier = modifier
        .onFocusChanged { isFocused = it.isFocused }
        .let { base ->
            if (onClick != null) {
                base
                    .clip(shape)
                    .clickable(onClick = onClick)
            } else {
                base
            }
        }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = BorderStroke(borderWidth, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isFocused) 4.dp else 0.dp
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            content = content
        )
    }
}

@Composable
fun StatusPill(
    text: String,
    containerColor: Color,
    contentColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = containerColor
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
fun ChannelBadge(channel: ReleaseChannel, modifier: Modifier = Modifier) {
    val (bg, fg) = when (channel) {
        ReleaseChannel.STABLE ->
            MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
        ReleaseChannel.BETA ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.onPrimaryContainer
        ReleaseChannel.TEST ->
            StatusWarning.copy(alpha = 0.18f) to StatusWarning
    }
    StatusPill(text = channel.label, containerColor = bg, contentColor = fg, modifier = modifier)
}

@Composable
fun ConnectivityAndRateLimitBanner(
    isOnline: Boolean,
    rateLimit: RateLimitInfo,
    modifier: Modifier = Modifier
) {
    if (!isOnline) {
        BannerBox(
            icon = Icons.Rounded.CloudOff,
            title = "Автономный режим",
            message = "Нет подключения к сети. Показаны сохранённые релизы и локальные данные.",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            background = MaterialTheme.colorScheme.surfaceVariant,
            modifier = modifier
        )
    } else if (rateLimit.isRateLimited) {
        val resetText = Formatters.formatEpochSeconds(rateLimit.resetEpochSeconds)
        BannerBox(
            icon = Icons.Rounded.WarningAmber,
            title = "Достигнут лимит GitHub API",
            message = "Сброс лимита примерно в $resetText. Укажите GitHub Token в Настройках для лимита 5000 запросов/час.",
            tint = StatusWarning,
            background = StatusWarning.copy(alpha = 0.14f),
            modifier = modifier
        )
    }
}

@Composable
private fun BannerBox(
    icon: ImageVector,
    title: String,
    message: String,
    tint: Color,
    background: Color,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .border(1.dp, tint.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(22.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SectionHeader(
    title: String,
    subtitle: String? = null,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            trailing()
        }
    }
}

@Composable
fun EmptyStateCard(
    icon: ImageVector,
    title: String,
    message: String,
    action: (@Composable () -> Unit)? = null
) {
    HubCard {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(14.dp))
                action()
            }
        }
    }
}
