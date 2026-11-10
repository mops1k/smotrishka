package com.mops1k.smotrishka.tv.core.vod.data.storage

import com.mops1k.smotrishka.tv.core.vod.domain.VodFavorites
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodFavoritesPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Избранное медиатеки поверх Room.
 *
 * Агрегат приходит с новым состоянием целиком, поэтому адаптер приводит таблицу к нему:
 * лишние строки удаляются, недостающие добавляются. Набор маленький, так что это дешевле,
 * чем вычислять разницу в use case.
 *
 * @param dao доступ к таблице избранного.
 */
class VodFavoritesStore(private val dao: VodFavoritesDao) : VodFavoritesPort {

    override val favorites: Flow<VodFavorites> = dao.observeAll().map { entities ->
        VodFavorites(entities.map { it.toDomain() })
    }

    override suspend fun replace(favorites: VodFavorites) {
        val incoming = favorites.items.associateBy { it.title.id.value }
        val stored = dao.getAll().map { it.titleId }.toSet()
        // Сначала удаляем то, чего больше нет, потом пишем новое: так не остаётся «хвостов».
        (stored - incoming.keys).forEach { dao.delete(it) }
        favorites.items.forEach { dao.upsert(it.toEntity()) }
    }

    override suspend fun clear() {
        dao.clear()
    }
}
