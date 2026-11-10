package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import kotlinx.coroutines.flow.Flow

/**
 * Наблюдает за избранным медиатеки.
 *
 * @param favoritesPort хранилище избранного.
 */
class ObserveVodFavorites(private val favoritesPort: VodFavoritesPort) {

    /** Поток избранного в порядке показа. */
    operator fun invoke(): Flow<VodFavorites> = favoritesPort.favorites
}
