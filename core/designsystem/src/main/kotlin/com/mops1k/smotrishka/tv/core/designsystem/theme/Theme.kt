package com.mops1k.smotrishka.tv.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/**
 * Тёмная схема Material3 в палитре редизайна.
 *
 * Акцент — фиолетовый `#a987ff` (был зелёный `#3BA55D`): по `docs/design/tokens.md` зелёный
 * остаётся только точкой «в эфире» и успехом в тосте.
 */
private val TvDarkColors = darkColorScheme(
    primary = TvColors.accent,
    onPrimary = TvColors.categoryActiveText,
    primaryContainer = TvColors.panelSoft,
    onPrimaryContainer = TvColors.text,
    secondary = TvColors.accentStrong,
    onSecondary = TvColors.text,
    tertiary = TvColors.green,
    onTertiary = TvColors.categoryActiveText,
    background = TvColors.bg,
    onBackground = TvColors.text,
    surface = TvColors.panel,
    onSurface = TvColors.text,
    surfaceVariant = TvColors.panelSoft,
    onSurfaceVariant = TvColors.muted,
    outline = TvColors.line,
    outlineVariant = TvColors.line,
    error = TvColors.favorite,
    onError = Color(0xFF2B0A0F),
    errorContainer = Color(0xFF2A1B21),
    onErrorContainer = TvColors.errorText,
    scrim = TvColors.scrim,
)

/**
 * Тема приложения: единый стиль для онбординга, каркаса, списка каналов, настроек и плеера.
 *
 * @param content содержимое приложения.
 */
@Composable
fun SmotrishkaTvTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TvDarkColors,
        typography = TvMaterialTypography,
        content = content,
    )
}
