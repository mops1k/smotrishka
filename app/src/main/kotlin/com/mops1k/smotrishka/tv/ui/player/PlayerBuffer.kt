package com.mops1k.smotrishka.tv.ui.player

/**
 * Параметры буферизации плеера, рассчитанные из настройки «сколько секунд вперёд».
 *
 * Значение задаёт, сколько секунд потока плеер держит впереди себя: этого хватает, чтобы
 * короткий обрыв сети не остановил показ. Потолок буфера ограничен, чтобы переключение
 * каналов не превращалось в ожидание.
 *
 * @param seconds сколько секунд держать впереди (из настроек, 1..60).
 */
data class PlayerBuffer(val seconds: Int) {

    private val clamped: Int = seconds.coerceIn(MIN_SECONDS, MAX_SECONDS)

    /** Целевой размер буфера впереди, мс. */
    val minBufferMs: Int get() = clamped * MILLIS_PER_SECOND

    /** Потолок буфера: целевой размер плюс запас, мс, но не больше [MAX_BUFFER_MS]. */
    val maxBufferMs: Int get() = minOf(minBufferMs + HEADROOM_MS, MAX_BUFFER_MS)

    /** Сколько буфера нужно, чтобы начать показ, мс. */
    val forPlaybackMs: Int get() = minOf(PLAYBACK_BUFFER_MS, minBufferMs)

    /** Сколько буфера нужно, чтобы возобновить показ после обрыва, мс. */
    val forPlaybackAfterRebufferMs: Int get() = minOf(REBUFFER_MS, minBufferMs)

    private companion object {
        const val MILLIS_PER_SECOND = 1000
        const val HEADROOM_MS = 30_000

        /** Жёсткий потолок времени буфера: больше минуты держать в памяти не нужно. */
        const val MAX_BUFFER_MS = 60_000

        const val PLAYBACK_BUFFER_MS = 2_000
        const val REBUFFER_MS = 3_000
        const val MIN_SECONDS = 1
        const val MAX_SECONDS = 60
    }
}
