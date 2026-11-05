package com.mops1k.smotrishka.tv.ui.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * TDD-спецификация перемотки по полосе программы в плеере.
 */
class ProgrammeSeekTest {

    @Test
    fun `перемотка назад сдвигает позицию на прошедшую разницу`() {
        val target = ProgrammeSeek.target(
            currentPositionMs = 500_000,
            nowOffsetMs = 600_000,
            targetOffsetMs = 300_000,
            isLive = true,
        )

        assertThat(target).isEqualTo(200_000)
    }

    @Test
    fun `на прямом эфире перемотка вперёд возвращает к текущему моменту`() {
        val target = ProgrammeSeek.target(
            currentPositionMs = 42_000,
            nowOffsetMs = 600_000,
            targetOffsetMs = 900_000,
            isLive = true,
        )

        assertThat(target).isEqualTo(42_000)
    }

    @Test
    fun `в архиве перемотка вперёд работает`() {
        val target = ProgrammeSeek.target(
            currentPositionMs = 60_000,
            nowOffsetMs = 600_000,
            targetOffsetMs = 900_000,
            isLive = false,
        )

        assertThat(target).isEqualTo(360_000)
    }

    @Test
    fun `касание в текущей точке ничего не меняет`() {
        val target = ProgrammeSeek.target(
            currentPositionMs = 60_000,
            nowOffsetMs = 600_000,
            targetOffsetMs = 600_000,
            isLive = true,
        )

        assertThat(target).isNull()
    }

    @Test
    fun `позиция не уходит в минус`() {
        val target = ProgrammeSeek.target(
            currentPositionMs = 10_000,
            nowOffsetMs = 600_000,
            targetOffsetMs = 0,
            isLive = true,
        )

        assertThat(target).isEqualTo(0L)
    }

    @Test
    fun `доля полосы считается от длительности`() {
        assertThat(ProgrammeSeek.currentFraction(nowOffsetMs = 1_800_000, durationMs = 3_600_000))
            .isEqualTo(0.5f)
    }
}
