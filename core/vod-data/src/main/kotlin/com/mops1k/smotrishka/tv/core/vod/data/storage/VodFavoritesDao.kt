package com.mops1k.smotrishka.tv.core.vod.data.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к избранному медиатеки.
 */
@Dao
interface VodFavoritesDao {

    /** Избранное в порядке показа: новые сверху. */
    @Query("SELECT * FROM vod_favorites ORDER BY addedAtMillis DESC")
    fun observeAll(): Flow<List<VodFavoriteEntity>>

    /** Текущее избранное одним снимком. */
    @Query("SELECT * FROM vod_favorites ORDER BY addedAtMillis DESC")
    suspend fun getAll(): List<VodFavoriteEntity>

    /** Добавляет или обновляет карточку. */
    @Upsert
    suspend fun upsert(entity: VodFavoriteEntity)

    /** Убирает карточку. */
    @Query("DELETE FROM vod_favorites WHERE titleId = :titleId")
    suspend fun delete(titleId: Int)

    /** Очищает избранное целиком. */
    @Query("DELETE FROM vod_favorites")
    suspend fun clear()
}
