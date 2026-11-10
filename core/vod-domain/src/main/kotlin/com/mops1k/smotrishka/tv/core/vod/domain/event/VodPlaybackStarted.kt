package com.mops1k.smotrishka.tv.core.vod.domain.event

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Воспроизведение началось: фильм или серия открыты в плеере.
 *
 * @property titleId карточка, которую смотрят;
 * @property episodeId серия, если смотрят сериал;
 * @property occurredAtMillis момент события.
 */
data class VodPlaybackStarted(
    val titleId: VodTitleId,
    val episodeId: VodTitleId? = null,
    override val occurredAtMillis: Long,
) : VodEvent
