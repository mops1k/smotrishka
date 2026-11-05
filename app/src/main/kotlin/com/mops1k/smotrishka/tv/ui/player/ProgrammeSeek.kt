package com.mops1k.smotrishka.tv.ui.player

import kotlin.time.Duration

/**
 * Пересчёт касания по полосе программы в позицию потока.
 *
 * Полоса показывает передачу от начала до конца. Пользователь тянет её в точку передачи,
 * и плеер перематывается на столько же относительно текущего момента. Вперёд дальше эфира
 * уйти нельзя — вместо этого возвращаемся к текущему моменту (прямой эфир).
 */
object ProgrammeSeek {

    /**
     * Куда перемотать поток.
     *
     * @param currentPositionMs текущая позиция плеера;
     * @param nowOffsetMs сколько прошло от начала передачи до текущего момента;
     * @param targetOffsetMs сколько прошло от начала передачи до выбранной точки;
     * @param isLive идёт ли прямой эфир (вперёд дальше текущего момента нельзя).
     * @return новая позиция в миллисекундах или `null`, если перематывать некуда.
     */
    fun target(
        currentPositionMs: Long,
        nowOffsetMs: Long,
        targetOffsetMs: Long,
        isLive: Boolean,
    ): Long? {
        val delta = targetOffsetMs - nowOffsetMs
        if (delta == 0L) return null

        // Вперёд на прямом эфире — значит «к текущему моменту».
        if (isLive && delta > 0L) return currentPositionMs

        return (currentPositionMs + delta).coerceAtLeast(0L)
    }

    /**
     * Доля полосы для точки передачи.
     *
     * @param currentPositionMs текущая позиция плеера;
     * @param durationMs длительность передачи;
     * @param nowOffsetMs сколько прошло от начала передачи.
     */
    fun currentFraction(nowOffsetMs: Long, durationMs: Long): Float {
        if (durationMs <= 0L) return 0f
        return (nowOffsetMs.toFloat() / durationMs).coerceIn(0f, 1f)
    }

    /** Остаток передачи до конца. */
    fun remaining(durationMs: Long, nowOffsetMs: Long): Duration =
        Duration.parse("PT${(durationMs - nowOffsetMs).coerceAtLeast(0L)}S")
}
