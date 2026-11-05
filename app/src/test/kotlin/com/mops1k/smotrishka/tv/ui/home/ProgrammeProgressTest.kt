package com.mops1k.smotrishka.tv.ui.home

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * TDD-спецификация прогресса передачи: заполнение полосы и подписи времени.
 */
class ProgrammeProgressTest {

    private val start: Instant = Instant.parse("2026-11-03T16:00:00Z")
    private val end: Instant = Instant.parse("2026-11-03T17:30:00Z")
    private val utc: ZoneId = ZoneId.of("UTC")

    @Test
    fun `в начале передачи полоса пустая`() {
        val progress = ProgrammeProgress(start, end, start)

        assertThat(progress.fraction).isEqualTo(0f)
    }

    @Test
    fun `середина передачи даёт половину полосы`() {
        val middle = start.plus(45, ChronoUnit.MINUTES)

        val progress = ProgrammeProgress(start, end, middle)

        assertThat(progress.fraction).isEqualTo(0.5f)
    }

    @Test
    fun `после конца передачи полоса заполнена целиком`() {
        val progress = ProgrammeProgress(start, end, end.plus(10, ChronoUnit.MINUTES))

        assertThat(progress.fraction).isEqualTo(1f)
    }

    @Test
    fun `остаток считается в минутах`() {
        val progress = ProgrammeProgress(start, end, start.plus(48, ChronoUnit.MINUTES))

        assertThat(progress.remaining.toMinutes()).isEqualTo(42)
        assertThat(progress.remainingText()).isEqualTo("осталось 42 мин")
    }

    @Test
    fun `закончившаяся передача сообщает об этом`() {
        val progress = ProgrammeProgress(start, end, end)

        assertThat(progress.remainingText()).isEqualTo("передача закончилась")
    }

    @Test
    fun `время передачи показывается в заданном поясе`() {
        val progress = ProgrammeProgress(start, end, start)

        assertThat(progress.timeRange(utc)).isEqualTo("16:00–17:30")
    }
}
