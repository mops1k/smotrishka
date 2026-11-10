package com.mops1k.smotrishka.tv.core.vod.domain

/**
 * Результат переключения избранного.
 *
 * @property favorites новое состояние избранного;
 * @property added `true` — карточку добавили, `false` — убрали. По этому признаку выбирается
 *   доменное событие.
 */
data class VodFavoriteToggle(
    val favorites: VodFavorites,
    val added: Boolean,
)
