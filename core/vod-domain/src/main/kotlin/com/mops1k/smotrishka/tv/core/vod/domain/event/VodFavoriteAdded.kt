package com.mops1k.smotrishka.tv.core.vod.domain.event

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Карточка добавлена в избранное медиатеки.
 *
 * @property titleId идентификатор карточки;
 * @property title снапшот карточки на момент добавления — он и рисуется в избранном без сети;
 * @property occurredAtMillis момент события.
 */
data class VodFavoriteAdded(
    val titleId: VodTitleId,
    val title: VodTitle,
    override val occurredAtMillis: Long,
) : VodEvent
