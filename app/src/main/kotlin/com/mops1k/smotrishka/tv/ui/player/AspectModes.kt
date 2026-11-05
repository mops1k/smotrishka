package com.mops1k.smotrishka.tv.ui.player

import androidx.media3.ui.AspectRatioFrameLayout
import com.mops1k.smotrishka.tv.core.data.AspectMode

/**
 * Режимы кадра: подписи, порядок переключения и связь с плеером.
 *
 * Вынесено из OSD, чтобы кнопка осталась тонкой, а поведение проверялось тестом.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object AspectModes {

    /** Порядок переключения по кругу. */
    val ORDER = listOf(AspectMode.FIT, AspectMode.STRETCH, AspectMode.CROP, AspectMode.ORIGINAL)

    /**
     * Следующий режим после текущего.
     *
     * @param current текущий режим.
     */
    fun next(current: AspectMode): AspectMode {
        val index = ORDER.indexOf(current)
        return ORDER[(index + 1) % ORDER.size]
    }

    /**
     * Подпись режима для OSD.
     *
     * @param mode режим кадра.
     */
    fun label(mode: AspectMode): String = when (mode) {
        AspectMode.FIT -> "Вписать"
        AspectMode.STRETCH -> "Растянуть"
        AspectMode.CROP -> "Обрезать"
        AspectMode.ORIGINAL -> "1:1"
    }

    /**
     * Режим изменения размера у `PlayerView`.
     *
     * @param mode режим кадра.
     */
    fun resizeMode(mode: AspectMode): Int = when (mode) {
        AspectMode.FIT -> AspectRatioFrameLayout.RESIZE_MODE_FIT
        AspectMode.STRETCH -> AspectRatioFrameLayout.RESIZE_MODE_FILL
        AspectMode.CROP -> AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        AspectMode.ORIGINAL -> AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH
    }
}
