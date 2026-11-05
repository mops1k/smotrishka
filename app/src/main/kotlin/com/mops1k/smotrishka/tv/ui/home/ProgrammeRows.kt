package com.mops1k.smotrishka.tv.ui.home

import com.mops1k.smotrishka.tv.core.model.Programme
import java.time.Instant

/**
 * Статус передачи в телепрограмме.
 *
 * Прошедшая передача без архива остаётся прошедшей: экран по ней показывает подсказку, а не
 * открывает запись (`docs/design/flows.md`).
 */
enum class ProgrammeStatus {

    /** Передача уже закончилась. */
    PAST,

    /** Передача идёт сейчас. */
    NOW,

    /** Передача ещё не началась. */
    FUTURE,
}

/**
 * Строка телепрограммы: передача, её статус и доступность архива.
 *
 * @param programme передача из EPG;
 * @param status статус на момент построения;
 * @param hasArchive можно ли открыть эту передачу в архиве.
 */
data class ProgrammeRowUi(
    val programme: Programme,
    val status: ProgrammeStatus,
    val hasArchive: Boolean,
)

/** Построение строк телепрограммы. */
object ProgrammeRows {

    /**
     * Собирает строки телепрограммы из передач канала.
     *
     * @param programmes передачи канала в порядке времени;
     * @param now текущий момент;
     * @param channelHasArchive доступен ли архив у канала.
     */
    fun build(
        programmes: List<Programme>,
        now: Instant,
        channelHasArchive: Boolean,
    ): List<ProgrammeRowUi> = programmes.map { programme ->
        val status = statusOf(programme, now)
        ProgrammeRowUi(
            programme = programme,
            status = status,
            hasArchive = channelHasArchive && status == ProgrammeStatus.PAST,
        )
    }

    /**
     * Статус одной передачи: начало включительно, конец не включительно.
     *
     * @param programme передача;
     * @param now текущий момент.
     */
    fun statusOf(programme: Programme, now: Instant): ProgrammeStatus = when {
        !programme.start.isAfter(now) && programme.end.isAfter(now) -> ProgrammeStatus.NOW
        !programme.end.isAfter(now) -> ProgrammeStatus.PAST
        else -> ProgrammeStatus.FUTURE
    }
}
