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
        val fit = minOf(viewWidth.toFloat() / videoWidth, viewHeight.toFloat() / videoHeight)
        val cover = maxOf(viewWidth.toFloat() / videoWidth, viewHeight.toFloat() / videoHeight)
        return when (mode) {
            // Вписать: кадр целиком, с полосами.
            AspectMode.FIT -> fit to fit
            // Растянуть: заполнить по обеим осям, пропорции не сохраняем.
            AspectMode.STRETCH -> viewWidth.toFloat() / videoWidth to viewHeight.toFloat() / videoHeight
            // Обрезать: заполнить с обрезкой краёв, пропорции сохраняем.
            AspectMode.CROP -> cover to cover
            // 1:1: пиксель в пиксель, без масштаба.
            AspectMode.ORIGINAL -> 1f to 1f
        }
    }
}
