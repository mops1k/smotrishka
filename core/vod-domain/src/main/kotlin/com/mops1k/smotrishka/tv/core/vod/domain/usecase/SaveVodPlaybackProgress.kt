package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodPlaybackProgressed
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodEpisodeNumber
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import kotlinx.coroutines.flow.first

/**
 * Сохраняет позицию просмотра.
 *
 * Вызывается плеером по заметному шагу времени, а не на каждый кадр: запись в Room на каждый
 * тик — лишняя нагрузка, а плашке «Продолжить» хватает обновления раз в несколько секунд.
 *
 * @param progressPort история просмотра;
 * @param eventBus шина событий;
 * @param clock время события.
 */
class SaveVodPlaybackProgress(
    private val progressPort: VodPlaybackProgressPort,
    private val eventBus: VodEventBus,
    private val clock: VodClock,
) {

    /**
     * Сохраняет позицию карточки.
     *
     * @param titleId карточка (фильм или сезон);
     * @param positionMillis позиция в миллисекундах;
     * @param durationMillis длительность из плеера, если известна;
     * @param episodeId серия, если смотрят сезон;
     * @param episodeNumber номер серии.
     */
    suspend operator fun invoke(
        titleId: VodTitleId,
        positionMillis: Long,
        durationMillis: Long?,
        episodeId: VodTitleId? = null,
        episodeNumber: VodEpisodeNumber? = null,
    ) {
        val now = clock.nowMillis()
        val existing = progressPort.progress.first().firstOrNull { it.titleId == titleId }
        val updated = (existing ?: VodPlaybackProgress(titleId = titleId))
            .copy(
                episodeId = episodeId ?: existing?.episodeId,
                episodeNumber = episodeNumber ?: existing?.episodeNumber,
            )
            .withPosition(positionMillis = positionMillis, durationMillis = durationMillis, atMillis = now)
        progressPort.save(updated)
        eventBus.publish(
            VodPlaybackProgressed(
                titleId = titleId,
                positionMillis = positionMillis,
                episodeId = updated.episodeId,
                occurredAtMillis = now,
            ),
        )
    }
}
