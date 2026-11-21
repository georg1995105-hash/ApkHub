package com.apkhub.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import com.apkhub.app.domain.model.AppThemeMode

private val LightColorScheme = lightColorScheme(
    primary = LightPrimary,
    onPrimary = LightOnPrimary,
    primaryContainer = LightPrimaryContainer,
    onPrimaryContainer = LightOnPrimaryContainer,
    secondary = LightSecondary,
    onSecondary = LightOnSecondary,
    secondaryContainer = LightSecondaryContainer,
    onSecondaryContainer = LightOnSecondaryContainer,
    tertiary = LightTertiary,
    onTertiary = LightOnTertiary,
    tertiaryContainer = LightTertiaryContainer,
    onTertiaryContainer = LightOnTertiaryContainer,
    background = LightBackground,
    onBackground = LightOnBackground,
    surface = LightSurface,
    onSurface = LightOnSurface,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightOnSurfaceVariant,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant,
    error = LightError,
    onError = LightOnError,
    errorContainer = LightErrorContainer,
    onErrorContainer = LightOnErrorContainer
)

private val DarkColorScheme = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = DarkOnPrimary,
    primaryContainer = DarkPrimaryContainer,
    onPrimaryContainer = DarkOnPrimaryContainer,
    secondary = DarkSecondary,
    onSecondary = DarkOnSecondary,
    secondaryContainer = DarkSecondaryContainer,
    onSecondaryContainer = DarkOnSecondaryContainer,
    tertiary = DarkTertiary,
    onTertiary = DarkOnTertiary,
    tertiaryContainer = DarkTertiaryContainer,
    onTertiaryContainer = DarkOnTertiaryContainer,
    background = DarkBackground,
    onBackground = DarkOnBackground,
    surface = DarkSurface,
    onSurface = DarkOnSurface,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkOnSurfaceVariant,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant,
    error = DarkError,
    onError = DarkOnError,
    errorContainer = DarkErrorContainer,
    onErrorContainer = DarkOnErrorContainer
)

private val GraphiteColorScheme = darkColorScheme(
    primary = GraphitePrimary,
    onPrimary = GraphiteOnPrimary,
    primaryContainer = GraphitePrimaryContainer,
    onPrimaryContainer = GraphiteOnPrimaryContainer,
    secondary = GraphiteSecondary,
    onSecondary = GraphiteOnSecondary,
    secondaryContainer = GraphiteSecondaryContainer,
    onSecondaryContainer = GraphiteOnSecondaryContainer,
    tertiary = GraphiteTertiary,
    onTertiary = GraphiteOnTertiary,
    tertiaryContainer = GraphiteTertiaryContainer,
    onTertiaryContainer = GraphiteOnTertiaryContainer,
    background = GraphiteBackground,
    onBackground = GraphiteOnBackground,
    surface = GraphiteSurface,
    onSurface = GraphiteOnSurface,
    surfaceVariant = GraphiteSurfaceVariant,
    onSurfaceVariant = GraphiteOnSurfaceVariant,
    outline = GraphiteOutline,
    outlineVariant = GraphiteOutlineVariant,
    error = GraphiteError,
    onError = GraphiteOnError,
    errorContainer = GraphiteErrorContainer,
    onErrorContainer = GraphiteOnErrorContainer
)

@Composable
fun ApkHubTheme(
    themeMode: AppThemeMode,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val colorScheme = when (themeMode) {
        AppThemeMode.LIGHT -> LightColorScheme
        AppThemeMode.DARK -> DarkColorScheme
        AppThemeMode.GRAPHITE -> GraphiteColorScheme
        AppThemeMode.SYSTEM -> if (systemDark) DarkColorScheme else LightColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ApkHubTypography,
        content = content
    )
}
