package com.mops1k.smotrishka.tv.ui.programme

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.Instant
import java.time.temporal.ChronoUnit

/**
 * TDD-спецификация доступности передач в архиве.
 */
class ArchiveAvailabilityTest {

    private val now: Instant = Instant.parse("2026-11-03T20:00:00Z")

    @Test
    fun `передача внутри глубины доступна`() {
        val end = now.minus(47, ChronoUnit.HOURS)

        assertThat(ArchiveAvailability.available(end, now, depthHours = 48)).isTrue()
    }

    @Test
    fun `передача ровно на границе доступна`() {
        val end = now.minus(72, ChronoUnit.HOURS)

        assertThat(ArchiveAvailability.available(end, now, depthHours = 72)).isTrue()
    }

    @Test
    fun `передача старше глубины недоступна`() {
        val end = now.minus(73, ChronoUnit.HOURS)

        assertThat(ArchiveAvailability.available(end, now, depthHours = 72)).isFalse()
    }

    @Test
    fun `без архива ничего не доступно`() {
        val end = now.minus(1, ChronoUnit.HOURS)

        assertThat(ArchiveAvailability.available(end, now, depthHours = 0)).isFalse()
    }
}
