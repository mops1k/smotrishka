package com.mops1k.smotrishka.tv.core.vod.data.storage

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Доступ к истории просмотра медиатеки.
 */
@Dao
interface VodProgressDao {

    /** Все позиции просмотра. */
    @Query("SELECT * FROM vod_progress ORDER BY updatedAtMillis DESC")
    fun observeAll(): Flow<List<VodProgressEntity>>

    /** Добавляет или обновляет позицию карточки. */
    @Upsert
    suspend fun upsert(entity: VodProgressEntity)

    /** Очищает историю. */
    @Query("DELETE FROM vod_progress")
    suspend fun clear()
}
