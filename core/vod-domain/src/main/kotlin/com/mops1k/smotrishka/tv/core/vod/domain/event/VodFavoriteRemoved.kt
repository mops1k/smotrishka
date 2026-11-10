package com.mops1k.smotrishka.tv.core.vod.domain.event

import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitleId

/**
 * Карточка убрана из избранного медиатеки.
 *
 * @property titleId идентификатор карточки;
 * @property occurredAtMillis момент события.
 */
data class VodFavoriteRemoved(
    val titleId: VodTitleId,
    override val occurredAtMillis: Long,
) : VodEvent
