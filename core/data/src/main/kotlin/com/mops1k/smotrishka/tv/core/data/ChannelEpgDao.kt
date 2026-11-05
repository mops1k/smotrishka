package com.mops1k.smotrishka.tv.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/** Доступ к сохранённым связям каналов каталога с каналами гида. */
@Dao
interface ChannelEpgDao {

    /** Все сохранённые связи. */
    @Query("SELECT * FROM channel_epg")
    suspend fun all(): List<ChannelEpgEntity>

    /** Перезаписывает связь канала: гид мог смениться. */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(links: List<ChannelEpgEntity>)

    /** Удаляет все связи перед пересборкой. */
    @Query("DELETE FROM channel_epg")
    suspend fun clear()
}
