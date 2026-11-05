package com.mops1k.smotrishka.tv.ui.home

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Прогресс текущей передачи: сколько прошло, сколько осталось и как это показать.
 *
 * Заполнение полосы считается от начала передачи к концу: середина передачи даёт половину.
 * Значение всегда в пределах 0…1, поэтому полоса не «переполняется» при неточном EPG.
 *
 * @param start начало передачи;
 * @param end конец передачи;
 * @param now текущий момент для расчёта.
 */
class ProgrammeProgress(
    private val start: Instant,
    private val end: Instant,
    private val now: Instant,
) {

    private val totalMillis: Long = Duration.between(start, end).toMillis()

    /** Доля прошедшего времени, 0…1. */
    val fraction: Float
        get() {
            if (totalMillis <= 0L) return 1f
            val passed = Duration.between(start, now).toMillis().toFloat()
            return (passed / totalMillis).coerceIn(0f, 1f)
        }

    /** Сколько осталось до конца передачи (0, если передача уже закончилась). */
    val remaining: Duration
        get() = Duration.between(now, end).let { if (it.isNegative) Duration.ZERO else it }

    /**
     * Время передачи для подписи.
     *
     * @param zone часовой пояс пользователя.
     */
    fun timeRange(zone: ZoneId): String {
        val formatter = DateTimeFormatter.ofPattern("HH:mm").withZone(zone)
        return "${formatter.format(start)}–${formatter.format(end)}"
    }

    /** Подпись остатка: «осталось 42 мин», «осталось меньше минуты», «передача закончилась». */
    fun remainingText(): String {
        val minutes = remaining.toMinutes()
        return when {
            remaining.isZero -> "передача закончилась"
            minutes < 1 -> "осталось меньше минуты"
            else -> "осталось $minutes мин"
        }
    }
}
