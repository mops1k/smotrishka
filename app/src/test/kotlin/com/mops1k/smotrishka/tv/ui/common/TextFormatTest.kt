package com.mops1k.smotrishka.tv.ui.common

import com.google.common.truth.Truth.assertThat
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import org.junit.Test

/**
 * Проверки текстов интерфейса: склонения, остатки, интервалы и время.
 *
 * Значения взяты из `docs/design` дословно: макет задаёт точные формулировки, и они не должны
 * «плыть» при переносе.
 */
class TextFormatTest {

    @Test
    fun `счётчик каналов склоняется по числу`() {
        assertThat(TextFormat.channels(1)).isEqualTo("1 канал")
        assertThat(TextFormat.channels(2)).isEqualTo("2 канала")
        assertThat(TextFormat.channels(4)).isEqualTo("4 канала")
        assertThat(TextFormat.channels(5)).isEqualTo("5 каналов")
        assertThat(TextFormat.channels(11)).isEqualTo("11 каналов")
        assertThat(TextFormat.channels(21)).isEqualTo("21 канал")
        assertThat(TextFormat.channels(0)).isEqualTo("0 каналов")
    }

    @Test
    fun `счётчик передач склоняется по числу`() {
        assertThat(TextFormat.programmes(1)).isEqualTo("1 передача")
        assertThat(TextFormat.programmes(3)).isEqualTo("3 передачи")
        assertThat(TextFormat.programmes(7)).isEqualTo("7 передач")
    }

    @Test
    fun `остаток передачи в минутах`() {
        assertThat(TextFormat.remaining(Duration.ofMinutes(26))).isEqualTo("осталось 26 мин")
    }

    @Test
    fun `закончившаяся передача сообщает об окончании`() {
        assertThat(TextFormat.remaining(Duration.ofSeconds(30))).isEqualTo("передача закончилась")
        assertThat(TextFormat.remaining(Duration.ZERO)).isEqualTo("передача закончилась")
    }

    @Test
    fun `позиция воспроизведения в формате m ss`() {
        assertThat(TextFormat.playback(Duration.ofSeconds(754))).isEqualTo("воспроизведение 12:34")
        assertThat(TextFormat.playback(Duration.ofSeconds(5))).isEqualTo("воспроизведение 0:05")
        assertThat(TextFormat.playback(Duration.ofSeconds(65))).isEqualTo("воспроизведение 1:05")
    }

    @Test
    fun `интервал передачи идёт через en dash`() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-06-12T19:20:00Z")
        val end = Instant.parse("2026-06-12T20:05:00Z")

        assertThat(TextFormat.timeRange(start, end, zone)).isEqualTo("19:20–20:05")
    }

    @Test
    fun `часы шапки в формате HH mm`() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-06-12T20:33:00Z")

        assertThat(TextFormat.clock(now, zone)).isEqualTo("20:33")
    }

    @Test
    fun `дата шапки начинается с заглавной буквы`() {
        val zone = ZoneId.of("UTC")
        val now = Instant.parse("2026-06-12T20:33:00Z")

        val date = TextFormat.date(now, zone)

        assertThat(date).contains("12 июня")
        assertThat(date.first().isUpperCase()).isTrue()
    }

    @Test
    fun `доля прогресса передачи зажата в границах`() {
        val start = Instant.parse("2026-06-12T19:20:00Z")
        val end = Instant.parse("2026-06-12T20:05:00Z")

        assertThat(TextFormat.progressFraction(start, end, Instant.parse("2026-06-12T19:42:00Z")))
            .isWithin(0.001f).of(22f / 45f)
        assertThat(TextFormat.progressFraction(start, end, Instant.parse("2026-06-12T19:00:00Z")))
            .isEqualTo(0f)
        assertThat(TextFormat.progressFraction(start, end, Instant.parse("2026-06-12T21:00:00Z")))
            .isEqualTo(1f)
    }

    @Test
    fun `передача нулевой длины считается законченной`() {
        val moment = Instant.parse("2026-06-12T20:00:00Z")

        assertThat(TextFormat.progressFraction(moment, moment, moment)).isEqualTo(1f)
    }

    @Test
    fun `подсказка о начале будущей передачи`() {
        val zone = ZoneId.of("UTC")
        val start = Instant.parse("2026-06-12T22:00:00Z")

        assertThat(TextFormat.startsAt("Новости", start, zone))
            .isEqualTo("«Новости» начнётся в 22:00")
    }
}
