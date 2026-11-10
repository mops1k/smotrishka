package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.event.VodEpisodeCompleted
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus

/**
 * Сообщает, что серия закончилась.
 *
 * Отдельный use case нужен, потому что именно событие — единственный повод показать отсчёт
 * перед следующей серией. Какая серия следующая, решает агрегат карточки; use case только
 * публикует факт и не считает номера сам.
 *
 * @param eventBus шина событий;
 * @param clock время события.
 */
class CompleteVodEpisode(
    private val eventBus: VodEventBus,
    private val clock: VodClock,
) {

    /**
     * Публикует завершение серии.
     *
     * @param titleId сезон, к которому относится серия;
     * @param episodeId закончившаяся серия;
     * @param episodeNumber номер серии;
     * @param nextEpisodeId следующая серия, если есть.
     */
    suspend operator fun invoke(
        titleId: VodTitleId,
        episodeId: VodTitleId,
        episodeNumber: VodEpisodeNumber,
        nextEpisodeId: VodTitleId? = null,
    ) {
        eventBus.publish(
            VodEpisodeCompleted(
                titleId = titleId,
                episodeId = episodeId,
                episodeNumber = episodeNumber,
                nextEpisodeId = nextEpisodeId,
                occurredAtMillis = clock.nowMillis(),
            ),
        )
    }
}
