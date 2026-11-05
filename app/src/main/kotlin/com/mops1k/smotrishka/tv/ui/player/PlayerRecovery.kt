package com.mops1k.smotrishka.tv.ui.player

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Правила автоматического восстановления потока.
 *
 * Поток на слабом канале может оборваться или зависнуть. Пользователь не должен вручную
 * нажимать «играть»: плеер сам повторяет попытку с паузой, а если картинка слишком долго
 * буферизуется, перескакивает на текущий момент эфира.
 */
object PlayerRecovery {

    /** Сколько попыток делается до показа ошибки пользователю. */
    const val MAX_ATTEMPTS = 5

    /** Через сколько буферизации подряд поток считается зависшим. */
    val STALL_TIMEOUT = 15.seconds

    /** Как часто проверяется состояние буферизации. */
    val WATCHDOG_INTERVAL = 5.seconds

    /**
     * Есть ли ещё попытки восстановления.
     *
     * @param attempt номер уже сделанной попытки;
     * @param maxAttempts сколько попыток разрешено настройкой.
     */
    fun canRetry(attempt: Int, maxAttempts: Int): Boolean = attempt < maxAttempts

    /**
     * Пауза перед следующей попыткой.
     *
     * @param attempt номер попытки, начиная с 1.
     */
    fun delayBefore(attempt: Int): Duration = when (attempt) {
        1 -> 2.seconds
        2 -> 4.seconds
        3 -> 8.seconds
        else -> 15.seconds
    }

    /**
     * Пора ли перезапускать поток из-за зависания.
     *
     * @param bufferingFor сколько подряд длится буферизация.
     */
    fun shouldRestart(bufferingFor: Duration): Boolean = bufferingFor >= STALL_TIMEOUT
}
