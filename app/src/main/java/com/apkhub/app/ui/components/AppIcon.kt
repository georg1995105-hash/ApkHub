package com.apkhub.app.ui.components

import android.graphics.drawable.Drawable
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import coil.compose.AsyncImage

@Composable
fun AppAvatarIcon(
    displayName: String,
    avatarUrl: String?,
    installedDrawable: Drawable?,
    size: Dp = 46.dp,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(12.dp)
    val installedBitmap = remember(installedDrawable) {
        installedDrawable?.let {
            try {
                it.toBitmap(width = 128, height = 128).asImageBitmap()
            } catch (_: Exception) {
                null
            }
        }
    }

    when {
        installedBitmap != null -> {
            Image(
                bitmap = installedBitmap,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(shape)
            )
        }
        !avatarUrl.isNullOrBlank() -> {
            AsyncImage(
                model = avatarUrl,
                contentDescription = displayName,
                contentScale = ContentScale.Crop,
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            )
        }
        else -> {
            val initials = displayName
                .trim()
                .take(2)
                .uppercase()
                .ifBlank { "AP" }
            Box(
                modifier = modifier
                    .size(size)
                    .clip(shape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = initials,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
    }
}
