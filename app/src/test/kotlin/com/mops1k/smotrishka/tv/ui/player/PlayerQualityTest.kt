package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.data.VideoQuality
import org.junit.Test

/**
 * TDD-спецификация ограничения качества: потолок битрейта и поведение «Авто».
 */
class PlayerQualityTest {

    @Test
    fun `авто не ограничивает битрейт`() {
        assertThat(PlayerQuality.bitrateFor(VideoQuality.AUTO)).isEqualTo(Int.MAX_VALUE)
    }

    @Test
    fun `360p ограничен девятьюстами килобитами`() {
        assertThat(PlayerQuality.bitrateFor(VideoQuality.SD_360)).isEqualTo(900_000)
    }

    @Test
    fun `480p ограничен полутора мегабитами`() {
        assertThat(PlayerQuality.bitrateFor(VideoQuality.SD_480)).isEqualTo(1_500_000)
    }

    @Test
    fun `480p режет кадр до 854 на 480`() {
        assertThat(PlayerQuality.sizeFor(VideoQuality.SD_480)).isEqualTo(854 to 480)
    }

    @Test
    fun `360p режет кадр до 640 на 360`() {
        assertThat(PlayerQuality.sizeFor(VideoQuality.SD_360)).isEqualTo(640 to 360)
    }

    @Test
    fun `авто не ограничивает размер кадра`() {
        assertThat(PlayerQuality.sizeFor(VideoQuality.AUTO)).isNull()
    }

    @Test
    fun `720p ограничен тремя мегабитами`() {
        assertThat(PlayerQuality.bitrateFor(VideoQuality.HD)).isEqualTo(3_000_000)
    }

    @Test
    fun `1080p ограничен шестью мегабитами`() {
        assertThat(PlayerQuality.bitrateFor(VideoQuality.FULL_HD)).isEqualTo(6_000_000)
    }
}
