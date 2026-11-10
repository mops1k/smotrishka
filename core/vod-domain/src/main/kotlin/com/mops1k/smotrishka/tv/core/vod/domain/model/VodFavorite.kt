package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Избранная карточка медиатеки вместе со снапшотом для показа без сети.
 *
 * @property title снапшот карточки на момент добавления;
 * @property addedAtMillis когда добавили — по этому полю строится порядок «новые сверху».
 */
data class VodFavorite(
    val title: VodTitle,
    val addedAtMillis: Long,
)
