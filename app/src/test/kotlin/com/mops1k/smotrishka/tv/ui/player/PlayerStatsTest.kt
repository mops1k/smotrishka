package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация индикатора качества в OSD.
 */
class PlayerStatsTest {

    @Test
    fun `показывает разрешение, битрейт и буфер`() {
        val text = PlayerStats.describe(height = 1080, bitrate = 2_500_000, bufferMs = 4_300)

        assertThat(text).isEqualTo("1080p · 2.5 Мбит/с · буфер 4 с")
    }

    @Test
    fun `неизвестные значения не выводятся`() {
        val text = PlayerStats.describe(height = null, bitrate = null, bufferMs = 500)

        assertThat(text).isEqualTo("буфер 0 с")
    }

    @Test
    fun `720p показывается как 720p`() {
        val text = PlayerStats.describe(height = 720, bitrate = 1_200_000, bufferMs = 0)

        assertThat(text).startsWith("720p")
    }
}
