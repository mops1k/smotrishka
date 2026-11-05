package com.mops1k.smotrishka.tv.ui.common

import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Тексты интерфейса, которые повторяются на нескольких экранах.
 *
 * Все функции чистые и без побочных эффектов: их удобно проверять юнит-тестами, а экраны
 * получают единые формулировки (иначе «осталось 26 мин» и «26 мин осталось» жили бы рядом).
 */
object TextFormat {

    /**
     * Счётчик каналов со склонением: «1 канал», «2 канала», «8 каналов».
     *
     * @param count число каналов.
     */
    fun channels(count: Int): String = "$count ${plural(count, "канал", "канала", "каналов")}"

    /**
     * Счётчик передач со склонением: «1 передача», «7 передач».
     *
     * @param count число передач.
     */
    fun programmes(count: Int): String = "$count ${plural(count, "передача", "передачи", "передач")}"

    /**
     * Остаток текущей передачи: «осталось 26 мин», «передача закончилась».
     *
     * По `docs/design/player.md`: при остатке меньше минуты передача считается закончившейся.
     *
     * @param remaining сколько осталось до конца передачи.
     */
    fun remaining(remaining: Duration): String {
        val minutes = remaining.toMinutes()
        return if (minutes < 1L) "передача закончилась" else "осталось $minutes мин"
    }

    /**
     * Позиция воспроизведения в архиве: «воспроизведение 12:34».
     *
     * @param position сколько прошло с начала записи.
     */
    fun playback(position: Duration): String {
        val total = position.seconds.coerceAtLeast(0L)
        val minutes = total / 60
        val seconds = total % 60
        return "воспроизведение $minutes:${seconds.toString().padStart(2, '0')}"
    }

    /**
     * Интервал передачи: «19:20–20:05» (тире — en dash, как в макете).
     *
     * @param start начало передачи;
     * @param end конец передачи;
     * @param zone часовой пояс пользователя.
     */
    fun timeRange(start: Instant, end: Instant, zone: ZoneId): String =
        "${time(start, zone)}–${time(end, zone)}"

    /**
     * Время в формате `HH:mm`.
     *
     * @param instant момент времени;
     * @param zone часовой пояс пользователя.
     */
    fun time(instant: Instant, zone: ZoneId): String = HOUR_MINUTE.withZone(zone).format(instant)

    /**
     * Часы шапки: «20:33».
     *
     * @param now текущий момент;
     * @param zone часовой пояс пользователя.
     */
    fun clock(now: Instant, zone: ZoneId): String = time(now, zone)

    /**
     * Дата шапки: «Ср, 12 июня».
     *
     * @param now текущий момент;
     * @param zone часовой пояс пользователя.
     */
    fun date(now: Instant, zone: ZoneId): String {
        val text = DAY_AND_DATE.withZone(zone).format(now)
        return text.replaceFirstChar(Char::uppercaseChar)
    }

    /**
     * Доля прошедшего времени передачи, 0…1.
     *
     * @param start начало передачи;
     * @param end конец передачи;
     * @param now текущий момент.
     */
    fun progressFraction(start: Instant, end: Instant, now: Instant): Float {
        val total = Duration.between(start, end).toMillis()
        if (total <= 0L) return 1f
        val passed = Duration.between(start, now).toMillis().toFloat()
        return (passed / total).coerceIn(0f, 1f)
    }

    /**
     * Подсказка для будущей передачи: ««Название» начнётся в 22:00».
     *
     * @param title название передачи;
     * @param start начало передачи;
     * @param zone часовой пояс пользователя.
     */
    fun startsAt(title: String, start: Instant, zone: ZoneId): String =
        "«$title» начнётся в ${time(start, zone)}"

    /**
     * Русское склонение по числу: 1 канал / 2 канала / 5 каналов.
     *
     * @param count число;
     * @param one форма для одного предмета;
     * @param few форма для 2–4 предметов;
     * @param many форма для 5 и более.
     */
    fun plural(count: Int, one: String, few: String, many: String): String {
        val mod100 = count % 100
        if (mod100 in 11..14) return many
        return when (count % 10) {
            1 -> one
            2, 3, 4 -> few
            else -> many
        }
    }

    private val HOUR_MINUTE: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    private val DAY_AND_DATE: DateTimeFormatter =
        DateTimeFormatter.ofPattern("EE, d MMMM", Locale("ru"))
}
