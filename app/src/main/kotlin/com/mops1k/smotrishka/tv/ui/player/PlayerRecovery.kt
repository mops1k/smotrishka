package com.mops1k.smotrishka.tv.ui.player

import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Правила восстановления потока в плеере.
 *
 * Плеер не сдаётся: пока пользователь не вышел из плеера, попытки повторяются
 * бесконечно, но с растущей паузой — чтобы не забивать канал запросами.
 * Первая пауза короткая (мгновенный сбой сети лечится сам), дальше она удваивается
 * и упирается в потолок из настроек.
 */
object PlayerRecovery {

    /** Пауза перед первой попыткой: секунды хватает, чтобы пережить короткий сбой. */
    val FIRST_DELAY = 1.seconds

    /** Потолок паузы по умолчанию — 10 секунд: канал получает не больше запроса в 10 с. */
    val DEFAULT_MAX_DELAY = 10.seconds

    /** Через сколько буферизации подряд поток считается зависшим. */
    val STALL_TIMEOUT = 15.seconds

    /** Как часто проверяется состояние буферизации. */
    val WATCHDOG_INTERVAL = 5.seconds

    /** Сколько ждать готовности плеера после создания: дольше — пересоздаём. */
    val FIRST_FRAME_TIMEOUT = 25.seconds

    /** Каждую третью попытку плеер пересоздаётся: так сбрасываются декодер и HTTP-стек. */
    const val RECREATE_EVERY = 3

    /** Сколько попыток делает превью канала в списке, прежде чем замолчать. */
    const val MAX_ATTEMPTS = 5

    /**
     * Пауза перед попыткой.
     *
     * Последовательность: 1, 2, 4, 8 с, дальше [maxDelay] (по умолчанию 10 с).
     *
     * @param attempt номер попытки, начиная с 1;
     * @param maxDelay потолок паузы из настроек.
     */
    fun delayBefore(attempt: Int, maxDelay: Duration = DEFAULT_MAX_DELAY): Duration {
        if (attempt <= 0) return minOf(FIRST_DELAY, maxDelay)
        val doublings = (attempt - 1).coerceAtMost(MAX_DOUBLINGS)
        val grown = FIRST_DELAY * (1 shl doublings)
        return minOf(grown, maxDelay)
    }

    /**
     * Пора ли пересоздавать плеер вместо переподготовки.
     *
     * @param attempt номер попытки, начиная с 1.
     */
    fun shouldRecreate(attempt: Int): Boolean =
        attempt > 0 && attempt % RECREATE_EVERY == 0

    /**
     * Пора ли перезапускать поток из-за зависания.
     *
     * @param bufferingFor сколько подряд длится буферизация.
     */
    fun shouldRestart(bufferingFor: Duration): Boolean = bufferingFor >= STALL_TIMEOUT

    /**
     * Не завис ли плеер ещё до первого кадра.
     *
     * @param waitingFor сколько времени прошло с создания плеера.
     */
    fun firstFrameTimedOut(waitingFor: Duration): Boolean = waitingFor >= FIRST_FRAME_TIMEOUT

    /**
     * Доля прошедшей паузы перед попыткой: 0 в начале, 1 к моменту попытки.
     *
     * @param elapsedMillis сколько уже ждём;
     * @param delayMillis длина паузы.
     */
    fun retryProgress(elapsedMillis: Long, delayMillis: Long): Float {
        if (delayMillis <= 0L) return 1f
        return (elapsedMillis.toFloat() / delayMillis).coerceIn(0f, 1f)
    }

    /**
     * Сколько секунд осталось до попытки: для отсчёта на экране.
     *
     * @param elapsedMillis сколько уже ждём;
     * @param delayMillis длина паузы.
     */
    fun secondsLeft(elapsedMillis: Long, delayMillis: Long): Int {
        val left = delayMillis - elapsedMillis
        return if (left <= 0L) 0 else ((left + MILLIS_IN_SECOND - 1) / MILLIS_IN_SECOND).toInt()
    }

    /** Больше этого удваивать нечего: пауза всё равно упирается в потолок. */
    private const val MAX_DOUBLINGS = 30

    /** Миллисекунд в секунде: для округления остатка вверх. */
    private const val MILLIS_IN_SECOND = 1_000L
}
