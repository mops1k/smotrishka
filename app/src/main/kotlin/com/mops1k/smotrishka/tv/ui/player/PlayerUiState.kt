package com.mops1k.smotrishka.tv.ui.player

/**
 * Состояние экрана плеера.
 */
sealed interface PlayerUiState {

    /** Ищем канал в каталоге. */
    data object Loading : PlayerUiState

    /**
     * Канал найден, можно играть.
     *
     * @param title название канала для OSD;
     * @param streamUrl адрес потока;
     * @param logoUrl логотип для OSD, если он есть;
     * @param programmeTitle передача, которая идёт сейчас (из EPG), `null` — программы нет;
     * @param archiveAvailable есть ли у канала архив: от этого зависит доступность перемотки.
     */
    data class Content(
        val channelId: com.mops1k.smotrishka.tv.core.model.ChannelId,
        val title: String,
        val streamUrl: String,
        val logoUrl: String?,
        val programmeTitle: String? = null,
        val programmeStart: java.time.Instant? = null,
        val programmeEnd: java.time.Instant? = null,
        val archiveAvailable: Boolean = false,
        /** Играется передача из архива, а не прямой эфир. */
        val isArchive: Boolean = false,
    ) : PlayerUiState

    /** Канал не найден или каталог пуст. */
    data class Error(val reason: String) : PlayerUiState
}
