package com.mops1k.smotrishka.tv.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density

/**
 * Текущий размер шрифта интерфейса (решение Александра 09.11.2026).
 *
 * Значение задаётся в настройках и раздаётся от корня приложения, а применяется экранами
 * через [WithAppFontScale]: так меню плеера можно оставить обычного размера.
 */
val LocalFontScale = compositionLocalOf { 1f }

/**
 * Масштабирует все тексты внутри на выбранный размер шрифта.
 *
 * @param content содержимое, тексты которого нужно масштабировать.
 */
@Composable
fun WithAppFontScale(content: @Composable () -> Unit) {
    val scale = LocalFontScale.current
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, scale),
        content = content,
    )
}

/**
 * Возвращает обычный размер текста внутри: так меню плеера не зависит от настройки шрифта.
 *
 * @param content содержимое, которое не масштабируется.
 */
@Composable
fun WithoutFontScale(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    CompositionLocalProvider(
        LocalDensity provides Density(density.density, 1f),
        content = content,
    )
}
