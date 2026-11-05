package com.mops1k.smotrishka.tv.ui.player

/**
 * Границы локального буфера эфира (таймшифт).
 *
 * Время в буфере задаётся настенных часами: запись начинается в [startedAtMillis] и растёт,
 * пока канал открыт. Позиция в файле — это разница между моментом передачи и началом записи,
 * поэтому пауза в прямом эфире не теряет данные: файл продолжает писаться.
 *
 * @param startedAtMillis момент начала записи;
 * @param maxMinutes глубина буфера в минутах.
 */
data class TimeshiftWindow(
    val startedAtMillis: Long,
    val maxMinutes: Int,
) {

    /** Момент, раньше которого буфер уже обрезан. */
    fun availableFromMillis(nowMillis: Long): Long =
        maxOf(startedAtMillis, nowMillis - maxMinutes * MILLIS_PER_MINUTE)

    /**
     * Позиция в буфере для момента передачи.
     *
     * @param wallMillis момент передачи;
     * @param nowMillis текущий момент;
     * @return смещение от начала записи либо `null`, если момент уже вне буфера.
     */
    fun offsetMillis(wallMillis: Long, nowMillis: Long): Long? {
        if (wallMillis < availableFromMillis(nowMillis)) return null
        if (wallMillis > nowMillis) return null
        return wallMillis - startedAtMillis
    }

    /**
     * Доступна ли перемотка к моменту.
     *
     * @param wallMillis момент передачи;
     * @param nowMillis текущий момент.
     */
    fun canSeekTo(wallMillis: Long, nowMillis: Long): Boolean =
        offsetMillis(wallMillis, nowMillis) != null

    private companion object {
        const val MILLIS_PER_MINUTE = 60_000L
    }
}
