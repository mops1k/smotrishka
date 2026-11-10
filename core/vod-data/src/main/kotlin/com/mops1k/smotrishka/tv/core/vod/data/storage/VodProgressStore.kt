package com.mops1k.smotrishka.tv.core.vod.data.storage

import com.mops1k.smotrishka.tv.core.vod.domain.VodPlaybackProgress
import com.mops1k.smotrishka.tv.core.vod.domain.port.VodPlaybackProgressPort
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * История просмотра медиатеки поверх Room.
 *
 * @param dao доступ к таблице истории.
 */
class VodProgressStore(private val dao: VodProgressDao) : VodPlaybackProgressPort {

    override val progress: Flow<List<VodPlaybackProgress>> =
        dao.observeAll().map { entities -> entities.map { it.toDomain() } }

    override suspend fun save(progress: VodPlaybackProgress) {
        dao.upsert(progress.toEntity())
    }

    override suspend fun clear() {
        dao.clear()
    }
}
