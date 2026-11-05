package com.mops1k.smotrishka.tv.ui.player

/**
 * Выравнивание меток времени при склейке сегментов.
 *
 * Провайдер отдаёт сегменты, у которых время прыгает (в логах — `Unexpected audio track timestamp
 * discontinuity`), из-за этого плеер перезапускает декодер и встаёт. Здесь считается сдвиг,
 * который делает таймлайн непрерывным: к меткам следующего сегмента прибавляется разница между
 * ожидаемым и фактическим началом.
 *
 * Метки PTS/DTS в MPEG-TS занимают 33 бита и переполняются по кругу — это учитывается.
 */
object TsTimeline {

    /** Предел 33-битного поля метки времени. */
    const val WRAP: Long = 1L shl 33

    /**
     * Сдвиг для сегмента.
     *
     * @param expectedStart ожидаемое начало сегмента;
     * @param actualStart фактическое начало.
     */
    fun offset(expectedStart: Long, actualStart: Long): Long = expectedStart - actualStart

    /**
     * Применяет сдвиг к метке времени.
     *
     * @param pts метка времени;
     * @param offset сдвиг из [offset].
     */
    fun apply(pts: Long, offset: Long): Long {
        val shifted = pts + offset
        return ((shifted % WRAP) + WRAP) % WRAP
    }

    /**
     * Ожидаемое начало следующего сегмента.
     *
     * @param lastStart начало предыдущего сегмента;
     * @param duration длительность предыдущего сегмента.
     */
    fun expectedNextStart(lastStart: Long, duration: Long): Long = lastStart + duration

    /**
     * Нужен ли сдвиг: расхождение больше одного кадра.
     *
     * @param expectedStart ожидаемое начало;
     * @param actualStart фактическое начало;
     * @param tolerance допустимое расхождение.
     */
    fun needsShift(expectedStart: Long, actualStart: Long, tolerance: Long = 40): Boolean =
        kotlin.math.abs(actualStart - expectedStart) > tolerance
}
