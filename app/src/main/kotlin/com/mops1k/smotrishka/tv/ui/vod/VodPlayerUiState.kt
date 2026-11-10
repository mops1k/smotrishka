package com.mops1k.smotrishka.tv.ui.vod

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodQuality
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Состояние VOD-плеера медиатеки.
 */
sealed interface VodPlayerUiState {

    /** Карточка загружается: поток ещё не известен. */
    data object Loading : VodPlayerUiState

    /**
     * Поток не удалось получить.
     *
     * @param reason понятный пользователю текст.
     */
    data class Error(val reason: String) : VodPlayerUiState

    /**
     * Поток готов к воспроизведению.
     *
     * @param title название карточки;
     * @param streamUrl адрес HLS-потока;
     * @param episodeTitle название серии, если смотрят сезон;
     * @param episodeId серия;
     * @param episodeNumber номер серии;
     * @param previousEpisodeId предыдущая серия, если есть;
     * @param nextEpisodeId следующая серия, если есть;
     * @param nextEpisodeNumber номер следующей серии;
     * @param startPositionMillis с какого места продолжать;
     * @param countdownSeconds сколько осталось до автоперехода к следующей серии;
     * @param qualities доступные качества записи;
     * @param selectedQuality выбранное качество, `null` — «Авто».
     */
    data class Content(
        val title: String,
        val streamUrl: String,
        val episodeTitle: String? = null,
        val episodeId: VodTitleId? = null,
        val episodeNumber: VodEpisodeNumber? = null,
        val previousEpisodeId: VodTitleId? = null,
        val nextEpisodeId: VodTitleId? = null,
        val nextEpisodeNumber: VodEpisodeNumber? = null,
        val startPositionMillis: Long = 0L,
        val countdownSeconds: Int? = null,
        val qualities: Map<VodQuality, String> = emptyMap(),
        val selectedQuality: VodQuality? = null,
    ) : VodPlayerUiState
}
