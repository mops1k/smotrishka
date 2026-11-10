package com.mops1k.smotrishka.tv.core.vod.domain.event

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Серия закончилась: плеер может предложить следующую с отменяемым отсчётом.
 *
 * @property titleId сезон (карточка), к которой относится серия;
 * @property episodeId закончившаяся серия;
 * @property episodeNumber номер закончившейся серии;
 * @property nextEpisodeId следующая серия, если она есть;
 * @property occurredAtMillis момент события.
 */
data class VodEpisodeCompleted(
    val titleId: VodTitleId,
    val episodeId: VodTitleId,
    val episodeNumber: VodEpisodeNumber,
    val nextEpisodeId: VodTitleId? = null,
    override val occurredAtMillis: Long,
) : VodEvent
