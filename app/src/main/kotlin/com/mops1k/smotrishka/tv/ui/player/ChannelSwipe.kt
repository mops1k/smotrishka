package com.mops1k.smotrishka.tv.ui.player

/**
 * Что делать после вертикального свайпа по видео.
 */
enum class SwipeChannel {

    /** Свайп вниз: переключить на предыдущий канал. */
    Previous,

    /** Свайп вверх: переключить на следующий канал. */
    Next,

    /** Жест короче порога — ничего не делать. */
    None,
}

/**
 * Разбор вертикального свайпа в плеере.
 *
 * Логика вынесена из UI, чтобы проверяться юнит-тестами: направление и порог — единственное,
 * что решает жест, остальное делает Compose.
 */
object ChannelSwipe {

    /**
     * Решает, какой канал включать после жеста.
     *
     * @param totalDyPx суммарный сдвиг пальца по вертикали: минус — вверх, плюс — вниз;
     * @param thresholdPx порог срабатывания в пикселях.
     */
    fun decide(totalDyPx: Float, thresholdPx: Float): SwipeChannel = when {
        totalDyPx <= -thresholdPx -> SwipeChannel.Next
        totalDyPx >= thresholdPx -> SwipeChannel.Previous
        else -> SwipeChannel.None
    }
}
