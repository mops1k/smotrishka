package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodPlaybackStarted
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import kotlinx.coroutines.flow.first

/**
 * Отмечает начало просмотра и запоминает серию.
 *
 * Существующая позиция сохраняется: открытие карточки не должно обнулять «Продолжить с …».
 *
 * @param progressPort история просмотра;
 * @param eventBus шина событий;
 * @param clock время для события.
 */
class StartVodPlayback(
    private val progressPort: VodPlaybackProgressPort,
    private val eventBus: VodEventBus,
    private val clock: VodClock,
) {

    /**
     * Фиксирует старт просмотра.
     *
     * @param titleId карточка;
     * @param episodeId серия, если смотрят сезон;
     * @param episodeNumber номер серии.
     */
    suspend operator fun invoke(
        titleId: VodTitleId,
        episodeId: VodTitleId? = null,
        episodeNumber: VodEpisodeNumber? = null,
    ) {
        val now = clock.nowMillis()
        val existing = progressPort.progress.first().firstOrNull { it.titleId == titleId }
        val updated = (existing ?: VodPlaybackProgress(titleId = titleId)).copy(
            episodeId = episodeId ?: existing?.episodeId,
            episodeNumber = episodeNumber ?: existing?.episodeNumber,
            updatedAtMillis = now,
        )
        progressPort.save(updated)
        eventBus.publish(
            VodPlaybackStarted(
                titleId = titleId,
                episodeId = updated.episodeId,
                occurredAtMillis = now,
            ),
        )
    }
}
