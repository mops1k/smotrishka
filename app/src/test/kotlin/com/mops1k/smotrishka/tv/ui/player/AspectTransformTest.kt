package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.AspectMode
import org.junit.Test

/**
 * TDD-спецификация масштаба кадра для `TextureView`.
 *
 * Появилось вместе с переходом с `SurfaceView` на `TextureView`: на ТВ-приставке
 * поверхность `PlayerView` приходила нулевого размера, Media3 отключал видео, и картинки
 * не было вообще. Масштаб теперь считаем сами — значит его надо покрыть тестом, чтобы
 * FIT/CROP/STRETCH не перепутались.
 */
class AspectTransformTest {

    @Test
    fun `вписать сохраняет пропорции и вписывает по узкой стороне`() {
        // Кадр 1920x1080 в поверхность 1280x720: вписывается ровно по ширине.
        val (sx, sy) = AspectTransform.scale(AspectMode.FIT, 1920, 1080, 1280, 720)

        assertThat(sx).isWithin(0.001f).of(0.6667f)
        assertThat(sy).isWithin(0.001f).of(0.6667f)
    }

    @Test
    fun `обрезать заполняет поверхность по большей стороне`() {
        // Кадр 1080x1920 (вертикальный) в горизонтальную поверхность: обрезаем по высоте.
        val (sx, sy) = AspectTransform.scale(AspectMode.CROP, 1080, 1920, 1280, 720)

        assertThat(sx).isWithin(0.001f).of(1.1852f)
        assertThat(sy).isWithin(0.001f).of(1.1852f)
    }

    @Test
    fun `растянуть заполняет обе оси независимо`() {
        val (sx, sy) = AspectTransform.scale(AspectMode.STRETCH, 1920, 1080, 1280, 720)

        assertThat(sx).isWithin(0.001f).of(0.6667f)
        assertThat(sy).isWithin(0.001f).of(0.6667f)
    }

    @Test
    fun `растянуть не сохраняет пропорции у несовпадающих сторон`() {
        val (sx, sy) = AspectTransform.scale(AspectMode.STRETCH, 1000, 1000, 1280, 720)

        assertThat(sx).isWithin(0.001f).of(1.28f)
        assertThat(sy).isWithin(0.001f).of(0.72f)
    }

    @Test
    fun `один к одному не масштабирует`() {
        val (sx, sy) = AspectTransform.scale(AspectMode.ORIGINAL, 1920, 1080, 1280, 720)

        assertThat(sx).isEqualTo(1f)
        assertThat(sy).isEqualTo(1f)
    }

    @Test
    fun `неизвестные размеры дают единичный масштаб`() {
        assertThat(AspectTransform.scale(AspectMode.FIT, 0, 0, 1280, 720)).isEqualTo(1f to 1f)
        assertThat(AspectTransform.scale(AspectMode.CROP, 1920, 1080, 0, 0)).isEqualTo(1f to 1f)
    }
}
