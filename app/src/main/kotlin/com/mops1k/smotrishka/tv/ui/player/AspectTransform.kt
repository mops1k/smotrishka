package com.mops1k.smotrishka.tv.ui.player

import com.mops1k.smotrishka.tv.core.data.AspectMode

/**
 * Масштаб кадра для вывода видео через `TextureView`.
 *
 * `PlayerView` умел это сам, но на ТВ-приставке его `SurfaceView` приходил нулевого размера
 * (Media3 писал `surfaceSize 0x0` и отключал видео), поэтому видео выводится через
 * `TextureView`, а масштаб считаем сами — чистым расчётом, который проверяется тестом.
 */
object AspectTransform {

    /**
     * Множители масштаба по осям.
     *
     * @param mode режим кадра из настроек;
     * @param videoWidth ширина кадра, пиксели;
     * @param videoHeight высота кадра, пиксели;
     * @param viewWidth ширина поверхности, пиксели;
     * @param viewHeight высота поверхности, пиксели;
     * @return пара (масштаб по X, масштаб по Y); при неизвестных размерах — (1, 1).
     */
    fun scale(
        mode: AspectMode,
        videoWidth: Int,
        videoHeight: Int,
        viewWidth: Int,
        viewHeight: Int,
    ): Pair<Float, Float> {
        if (videoWidth <= 0 || videoHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return 1f to 1f
        }
        // `TextureView` сам растягивает буфер кадра на свои границы, поэтому единичный
        // масштаб означает «на весь экран». Режимы считаем от соотношений сторон, иначе
        // кадр 16:9 в поверхности 16:9 ужимался в центр (коэффициент 720/1080 = 0.667).
        val videoAspect = videoWidth.toFloat() / videoHeight
        val viewAspect = viewWidth.toFloat() / viewHeight
        // Доля, на которую надо ужать ширину кадра, чтобы он вписался целиком.
        val fitX = minOf(1f, videoAspect / viewAspect)
        val fitY = minOf(1f, viewAspect / videoAspect)
        // Доля, на которую надо увеличить, чтобы заполнить поверхность с обрезкой краёв.
        val cropX = maxOf(1f, videoAspect / viewAspect)
        val cropY = maxOf(1f, viewAspect / videoAspect)
        return when (mode) {
            // Вписать: кадр целиком, с полосами.
            AspectMode.FIT -> fitX to fitY
            // Растянуть: буфер и так заполняет поверхность, дополнительный масштаб не нужен.
            AspectMode.STRETCH -> 1f to 1f
            // Обрезать: заполнить с обрезкой краёв, пропорции сохраняем.
            AspectMode.CROP -> cropX to cropY
            // 1:1: пиксель кадра в пиксель экрана.
            AspectMode.ORIGINAL -> videoWidth.toFloat() / viewWidth to videoHeight.toFloat() / viewHeight
        }
    }
}
