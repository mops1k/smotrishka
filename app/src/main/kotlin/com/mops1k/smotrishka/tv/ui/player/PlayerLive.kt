package com.mops1k.smotrishka.tv.ui.player

import androidx.media3.common.MediaItem

/**
 * Настройки живого элемента воспроизведения.
 *
 * После короткого провисания сети плеер перепривязывается к окну эфира и откатывается на 5–10 секунд
 * назад. Живой отступ ставит целевую точку внутри окна: плеер догоняет эфир изменением
 * скорости, а не прыжком к самому старому сегменту.
 */
object PlayerLive {

    /** Отступ от живого края: два-три сегмента провайдера по 10 с. */
    const val TARGET_OFFSET_MS = 8_000L

    /** Минимальная скорость догоняния. */
    const val MIN_PLAYBACK_SPEED = 0.97f

    /** Максимальная скорость догоняния. */
    const val MAX_PLAYBACK_SPEED = 1.05f

    /**
     * Собирает элемент потока с настройками живого края.
     *
     * @param url адрес потока.
     */
    fun mediaItem(url: String): MediaItem = MediaItem.Builder()
        .setUri(url)
        .setLiveConfiguration(
            MediaItem.LiveConfiguration.Builder()
                .setTargetOffsetMs(TARGET_OFFSET_MS)
                .setMinPlaybackSpeed(MIN_PLAYBACK_SPEED)
                .setMaxPlaybackSpeed(MAX_PLAYBACK_SPEED)
                .build(),
        )
        .build()
}
