package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация локального буфера эфира.
 */
class TimeshiftWindowTest {

    private val start = 1_000_000_000L

    @Test
    fun `свежий момент даёт смещение от начала записи`() {
        val window = TimeshiftWindow(startedAtMillis = start, maxMinutes = 60)

        val offset = window.offsetMillis(wallMillis = start + 90_000, nowMillis = start + 120_000)

        assertThat(offset).isEqualTo(90_000)
    }

    @Test
    fun `момент внутри глубины буфера доступен`() {
        val window = TimeshiftWindow(startedAtMillis = start, maxMinutes = 60)
        val now = start + 120 * 60_000L

        assertThat(window.canSeekTo(wallMillis = now - 59 * 60_000L, nowMillis = now)).isTrue()
    }

    @Test
    fun `момент старше глубины буфера недоступен`() {
        val window = TimeshiftWindow(startedAtMillis = start, maxMinutes = 30)
        val now = start + 120 * 60_000L

        assertThat(window.canSeekTo(wallMillis = now - 31 * 60_000L, nowMillis = now)).isFalse()
    }

    @Test
    fun `будущий момент недоступен`() {
        val window = TimeshiftWindow(startedAtMillis = start, maxMinutes = 60)

        assertThat(window.canSeekTo(wallMillis = start + 61_000, nowMillis = start + 60_000)).isFalse()
    }

    @Test
    fun `граница буфера считается от текущего момента, а не от начала записи`() {
        val window = TimeshiftWindow(startedAtMillis = start, maxMinutes = 10)

        assertThat(window.availableFromMillis(nowMillis = start + 600_000))
            .isEqualTo(start + 0)
        assertThat(window.availableFromMillis(nowMillis = start + 1_200_000))
            .isEqualTo(start + 600_000)
    }
}
