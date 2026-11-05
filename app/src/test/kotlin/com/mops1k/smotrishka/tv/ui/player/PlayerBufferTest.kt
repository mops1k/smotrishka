package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация расчёта буфера плеера из настройки.
 */
class PlayerBufferTest {

    @Test
    fun `пять секунд дают пять тысяч миллисекунд буфера`() {
        val buffer = PlayerBuffer(seconds = 5)

        assertThat(buffer.minBufferMs).isEqualTo(5_000)
        assertThat(buffer.maxBufferMs).isEqualTo(35_000)
    }

    @Test
    fun `начало показа ждёт не больше двух секунд`() {
        val buffer = PlayerBuffer(seconds = 10)

        assertThat(buffer.forPlaybackMs).isEqualTo(2_000)
    }

    @Test
    fun `возобновление после обрыва ждёт не больше трёх секунд`() {
        val buffer = PlayerBuffer(seconds = 20)

        assertThat(buffer.forPlaybackAfterRebufferMs).isEqualTo(3_000)
    }

    @Test
    fun `маленький буфер не делает ожидание больше самого буфера`() {
        val buffer = PlayerBuffer(seconds = 1)

        assertThat(buffer.forPlaybackMs).isEqualTo(1_000)
        assertThat(buffer.forPlaybackAfterRebufferMs).isEqualTo(1_000)
    }

    @Test
    fun `слишком большое значение ограничивается`() {
        val buffer = PlayerBuffer(seconds = 600)

        assertThat(buffer.minBufferMs).isEqualTo(60_000)
    }

    @Test
    fun `время буфера не превышает минуту даже с запасом`() {
        val buffer = PlayerBuffer(seconds = 60)

        assertThat(buffer.maxBufferMs).isEqualTo(60_000)
    }

    @Test
    fun `нулевое значение не допускается`() {
        val buffer = PlayerBuffer(seconds = 0)

        assertThat(buffer.minBufferMs).isEqualTo(1_000)
    }
}
