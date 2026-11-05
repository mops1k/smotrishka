package com.mops1k.smotrishka.tv.ui.home

import com.google.common.truth.Truth.assertThat
import com.mops1k.smotrishka.tv.core.model.Programme
import java.time.Instant
import org.junit.Test

/**
 * TDD-спецификация строк телепрограммы: статус передачи и доступность архива.
 *
 * Строка — то, что видит пользователь в развороте телепрограммы и в панели плеера, поэтому
 * статус считается только по времени передачи, а архив — по признаку канала.
 */
class ProgrammeRowsTest {

    private val now: Instant = Instant.parse("2026-11-02T20:00:00Z")

    private fun programme(title: String, startMinutes: Long, endMinutes: Long) = Programme(
        channelId = "one",
        title = title,
        start = now.plusSeconds(startMinutes * 60),
        end = now.plusSeconds(endMinutes * 60),
    )

    @Test
    fun `прошедшая текущая и будущая передачи различаются по времени`() {
        val rows = ProgrammeRows.build(
            programmes = listOf(
                programme("Прошлая", -120, -60),
                programme("Идёт", -30, 30),
                programme("Будущая", 30, 90),
            ),
            now = now,
            channelHasArchive = true,
        )

        assertThat(rows.map { it.status }).containsExactly(
            ProgrammeStatus.PAST,
            ProgrammeStatus.NOW,
            ProgrammeStatus.FUTURE,
        ).inOrder()
        assertThat(rows.map { it.programme.title })
            .containsExactly("Прошлая", "Идёт", "Будущая")
            .inOrder()
    }

    @Test
    fun `бейдж архива только у прошедшей передачи канала с архивом`() {
        val rows = ProgrammeRows.build(
            programmes = listOf(
                programme("Прошлая", -120, -60),
                programme("Идёт", -30, 30),
                programme("Будущая", 30, 90),
            ),
            now = now,
            channelHasArchive = true,
        )

        assertThat(rows.map { it.hasArchive }).containsExactly(true, false, false).inOrder()
    }

    @Test
    fun `без архива у канала бейджа нет ни у одной строки`() {
        val rows = ProgrammeRows.build(
            programmes = listOf(
                programme("Прошлая", -120, -60),
                programme("Идёт", -30, 30),
            ),
            now = now,
            channelHasArchive = false,
        )

        assertThat(rows.map { it.hasArchive }).containsExactly(false, false)
    }

    @Test
    fun `границы передачи относятся к текущей`() {
        val rows = ProgrammeRows.build(
            programmes = listOf(
                // Передача началась ровно сейчас.
                programme("Началась", 0, 60),
                // Передача закончилась ровно сейчас — она уже прошедшая.
                programme("Закончилась", -60, 0),
            ),
            now = now,
            channelHasArchive = true,
        )

        assertThat(rows.map { it.status })
            .containsExactly(ProgrammeStatus.NOW, ProgrammeStatus.PAST)
            .inOrder()
    }

    @Test
    fun `пустой список передач даёт пустой разворот`() {
        assertThat(ProgrammeRows.build(emptyList(), now, channelHasArchive = true)).isEmpty()
    }
}
