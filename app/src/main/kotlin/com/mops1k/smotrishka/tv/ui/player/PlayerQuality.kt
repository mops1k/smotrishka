package com.mops1k.smotrishka.tv.ui.player

import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.mops1k.smotrishka.tv.core.data.VideoQuality

/**
 * Ограничение качества видео в зависимости от настройки.
 *
 * Плеер сам выбирает вариант по скорости сети, но потолок задаёт пользователь: на слабом
 * интернете 480p смотрится без постоянной буферизации, тогда как 1080p только тянет трафик.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlayerQuality {

    /**
     * Применяет потолок качества к выбору дорожек.
     *
     * @param selector выбор дорожек плеера;
     * @param quality выбранное качество.
     */
    fun apply(selector: DefaultTrackSelector, quality: VideoQuality) {
        val size = sizeFor(quality) ?: return

        selector.parameters = selector.buildUponParameters()
            .setMaxVideoSize(size.first, size.second)
            .setMaxVideoBitrate(bitrateFor(quality))
            .build()
    }

    /**
     * Потолок размера кадра для качества.
     *
     * @param quality выбранное качество;
     * @return ширина и высота либо `null` для «Авто».
     */
    fun sizeFor(quality: VideoQuality): Pair<Int, Int>? = when (quality) {
        VideoQuality.AUTO -> null
        VideoQuality.SD_360 -> 640 to 360
        VideoQuality.SD_480 -> 854 to 480
        VideoQuality.HD -> 1280 to 720
        VideoQuality.FULL_HD -> 1920 to 1080
    }

    /** Потолок битрейта для выбранного качества, бит/с. */
    fun bitrateFor(quality: VideoQuality): Int = when (quality) {
        VideoQuality.AUTO -> Int.MAX_VALUE
        VideoQuality.SD_360 -> 900_000
        VideoQuality.SD_480 -> 1_500_000
        VideoQuality.HD -> 3_000_000
        VideoQuality.FULL_HD -> 6_000_000
    }
}
