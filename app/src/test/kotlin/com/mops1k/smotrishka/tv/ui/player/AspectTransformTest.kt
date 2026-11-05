package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.AspectMode
import org.junit.Test

/**
 * TDD-спецификация масштаба кадра для `TextureView`.
 *
 * Появилось вместе с переходом с `SurfaceView` на `TextureView`: на ТВ-приставке
 * поверхность `PlayerView` приходила нулевого размера, Media3 отключал видео, и картинки
 * не было вообще.
 *
 * `TextureView` сам растягивает буфер кадра на свои границы, поэтому единичный масштаб —
 * это «растянуть на всю поверхность». Режимы считаются от соотношений сторон: у кадра 16:9
 * в поверхность 16:9 масштаб обязан быть (1, 1), иначе эфир ужимается в центр (ошибка
 * 05.11.2026: кадр 853x480 вместо 1280x720 из-за коэффициента 720/1080).
 */
class AspectTransformTest {

    @Test
    fun `вписать при совпадении сторон занимает весь экран`() {
        // Кадр 1920x1080 в поверхность 1280x720: стороны совпадают, ужимать нечего.
        val (sx, sy) = AspectTransform.scale(AspectMode.FIT, 1920, 1080, 1280, 720)

        assertThat(sx).isWithin(0.001f).of(1f)
        assertThat(sy).isWithin(0.001f).of(1f)
    }

    @Test
    fun `вписать узкий кадр оставляет полосы по бокам`() {
        // Кадр 4:3 (1440x1080) в широкую поверхность 16:9: по бокам полосы.
        val (sx, sy) = AspectTransform.scale(AspectMode.FIT, 1440, 1080, 1280, 720)

        assertThat(sx).isWithin(0.001f).of(0.75f)
        assertThat(sy).isWithin(0.001f).of(1f)
    }

    @Test
    fun `обрезать широкий кадр в узкой поверхности режет верх и низ`() {
        // Кадр 16:9 в поверхность 4:3 (1024x768): по бокам заполняем, сверху и снизу обрезаем.
        val (sx, sy) = AspectTransform.scale(AspectMode.CROP, 1920, 1080, 1024, 768)

        assertThat(sx).isWithin(0.001f).of(1.3333f)
        assertThat(sy).isWithin(0.001f).of(1f)
    }

    @Test
    fun `обрезать вертикальный кадр заполняет поверхность по ширине`() {
        // Кадр 1080x1920 (9:16) в горизонтальную поверхность: обрезаем по высоте.
        val (sx, sy) = AspectTransform.scale(AspectMode.CROP, 1080, 1920, 1280, 720)

        assertThat(sx).isWithin(0.001f).of(1f)
        assertThat(sy).isWithin(0.001f).of(3.1605f)
    }

    @Test
    fun `растянуть не масштабирует - TextureView сам заполняет поверхность`() {
        val (sx, sy) = AspectTransform.scale(AspectMode.STRETCH, 1920, 1080, 1280, 720)

        assertThat(sx).isEqualTo(1f)
        assertThat(sy).isEqualTo(1f)
    }

    @Test
    fun `один к одному выводит пиксель в пиксель`() {
        // TextureView сам ужимает буфер 1920x1080 в поверхность 1280x720 (коэффициент 0.667),
        // поэтому для «пиксель в пиксель» нужен обратный множитель 1.5; картинка при этом
        // выходит за границы экрана, и это ожидаемо для режима 1:1.
        val (sx, sy) = AspectTransform.scale(AspectMode.ORIGINAL, 1920, 1080, 1280, 720)

        assertThat(sx).isWithin(0.001f).of(1.5f)
        assertThat(sy).isWithin(0.001f).of(1.5f)
    }

    @Test
    fun `неизвестные размеры дают единичный масштаб`() {
        assertThat(AspectTransform.scale(AspectMode.FIT, 0, 0, 1280, 720)).isEqualTo(1f to 1f)
        assertThat(AspectTransform.scale(AspectMode.CROP, 1920, 1080, 0, 0)).isEqualTo(1f to 1f)
    }
}
