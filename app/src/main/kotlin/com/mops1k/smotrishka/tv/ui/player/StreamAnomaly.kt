package com.mops1k.smotrishka.tv.ui.player

/**
 * Распознаёт «плохой» канал по поведению воспроизведения.
 *
 * Канал стартует прямым потоком: так картинка появляется сразу. Только если в течение
 * [WINDOW_MILLIS] набралось [THRESHOLD] признаков (откат позиции назад, остановки при нормальной
 * сети), канал считается проблемным и переводится на собственный буфер.
 */
object StreamAnomaly {

    /** За сколько секунд собираем признаки. */
    const val WINDOW_MILLIS = 60_000L

    /** Сколько признаков считаем проблемой. */
    const val THRESHOLD = 3

    /** Откат позиции, который уже считается признаком проблемы. */
    const val REWIND_MILLIS = 2_000L

    /**
     * Остановка при показе картинки — признак только вместе с другими: кратковременная
     * буферизация бывает и на здоровых каналах.
     */
    const val STALL_MILLIS = 3_000L

    /**
     * Признак «позиция откатилась назад».
     *
     * @param previousMs предыдущая позиция;
     * @param currentMs текущая позиция.
     */
    fun isRewind(previousMs: Long, currentMs: Long): Boolean =
        previousMs - currentMs >= REWIND_MILLIS

    /**
     * Пора ли переключать канал на буфер.
     *
     * @param events моменты признаков (мс эпохи);
     * @param now текущий момент.
     */
    fun shouldSwitch(events: List<Long>, now: Long): Boolean =
        events.count { now - it <= WINDOW_MILLIS } >= THRESHOLD
}
