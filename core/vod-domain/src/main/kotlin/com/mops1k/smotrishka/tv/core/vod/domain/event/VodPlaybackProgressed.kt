package com.mops1k.smotrishka.tv.core.vod.domain.event

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Позиция просмотра изменилась и сохранена.
 *
 * Событие публикуется не на каждый кадр, а по заметному шагу плеера: иначе подписчики
 * (плашка «Продолжить» в сетке, карточка) захлебнутся обновлениями.
 *
 * @property titleId карточка, которую смотрят;
 * @property positionMillis позиция в миллисекундах;
 * @property episodeId серия, если смотрят сериал;
 * @property occurredAtMillis момент события.
 */
data class VodPlaybackProgressed(
    val titleId: VodTitleId,
    val positionMillis: Long,
    val episodeId: VodTitleId? = null,
    override val occurredAtMillis: Long,
) : VodEvent
