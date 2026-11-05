package com.mops1k.smotrishka.tv.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Тёмная палитра приложения: рассчитана на просмотр с дивана при слабом свете.
 * Акцент — зелёный, как в баннере и иконке.
 */
private val TvDarkColors = darkColorScheme(
    primary = Color(0xFF3BA55D),
    onPrimary = Color(0xFF06210F),
    primaryContainer = Color(0xFF1C4F31),
    onPrimaryContainer = Color(0xFFB8F2CC),
    secondary = Color(0xFF7AA2C8),
    onSecondary = Color(0xFF0A1B2A),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6EBF2),
    surface = Color(0xFF151B23),
    onSurface = Color(0xFFE6EBF2),
    surfaceVariant = Color(0xFF243040),
    onSurfaceVariant = Color(0xFFAFBCCB),
    error = Color(0xFFE4695C),
    onError = Color(0xFF2B0A07),
)

/**
 * Тема приложения: единый стиль для экранов онбординга, каналов и плеера.
 */
@Composable
fun SmotrishkaTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvDarkColors,
        content = content,
    )
}
