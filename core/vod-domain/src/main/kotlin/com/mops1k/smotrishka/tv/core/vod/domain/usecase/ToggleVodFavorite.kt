package com.mops1k.smotrishka.tv.core.vod.domain.usecase

import com.mops1k.smotrishka.tv.core.vod.domain.VodFavoriteToggle
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodFavoriteAdded
import com.mops1k.smotrishka.tv.core.vod.domain.event.VodFavoriteRemoved
import com.mops1k.smotrishka.tv.core.vod.domain.model.VodTitle
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodClock
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodEventBus
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import kotlinx.coroutines.flow.first

/**
 * Добавляет карточку в избранное медиатеки или убирает её оттуда.
 *
 * Сначала сохраняется состояние, затем публикуется событие — подписчики, пропустившие событие,
 * всё равно увидят актуальное избранное при следующем чтении.
 *
 * @param favoritesPort хранилище избранного;
 * @param eventBus шина событий;
 * @param clock время для события.
 */
class ToggleVodFavorite(
    private val favoritesPort: VodFavoritesPort,
    private val eventBus: VodEventBus,
    private val clock: VodClock,
) {

    /**
     * Переключает карточку.
     *
     * @param title карточка из каталога или карточки фильма.
     */
    suspend operator fun invoke(title: VodTitle): VodFavoriteToggle {
        val now = clock.nowMillis()
        val toggled = favoritesPort.favorites.first().toggle(title, now)
        favoritesPort.replace(toggled.favorites)
        eventBus.publish(
            if (toggled.added) {
                VodFavoriteAdded(titleId = title.id, title = title, occurredAtMillis = now)
            } else {
                VodFavoriteRemoved(titleId = title.id, occurredAtMillis = now)
            },
        )
        return toggled
    }
}
