package com.mops1k.smotrishka.tv.ui.player

/**
 * Политика повторов при скачивании сегмента эфира.
 *
 * Лучше короткая пауза, чем пропуск куска: сегмент повторяется, пока не скачается, но не
 * бесконечно — после [MAX_ATTEMPT] попыток загрузчик сообщает об ошибке, чтобы экран показал
 * «Поток недоступен», а не крутил петлю.
 */
object SegmentRetry {

    /** Задержки между попытками, мс. */
    private val DELAYS_MS = longArrayOf(500, 1_000, 2_000, 3_000, 5_000, 8_000, 10_000, 15_000)

    /** Всего попыток на сегмент. */
    const val MAX_ATTEMPT = 8

    /**
     * Пауза перед повтором.
     *
     * @param attempt номер попытки, начиная с 1;
     * @return задержка в миллисекундах.
     */
    fun delayBefore(attempt: Int): Long =
        DELAYS_MS[(attempt - 1).coerceIn(0, DELAYS_MS.lastIndex)]

    /**
     * Есть ли ещё попытки.
     *
     * @param attempt номер уже сделанной попытки.
     */
    fun canRetry(attempt: Int): Boolean = attempt < MAX_ATTEMPT
}
