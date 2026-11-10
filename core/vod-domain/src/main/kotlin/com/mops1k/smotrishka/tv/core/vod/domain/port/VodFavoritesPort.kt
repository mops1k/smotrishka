package com.mops1k.smotrishka.tv.core.vod.domain.port

import com.mops1k.smotrishka.tv.core.vod.domain.VodFavorites
import kotlinx.coroutines.flow.Flow

/**
 * Хранилище избранного медиатеки.
 *
 * Хранится снапшот карточки: избранное показывается и без связи с порталом.
 */
interface VodFavoritesPort {

    /** Текущее избранное в порядке показа. */
    val favorites: Flow<VodFavorites>

    /**
     * Заменяет избранное целиком — так его сохраняет use case после переключения.
     *
     * @param favorites новое состояние.
     */
    suspend fun replace(favorites: VodFavorites)

    /** Очищает избранное — при смене или отключении портала. */
    suspend fun clear()
}
