package com.mops1k.smotrishka.tv.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Доступ к пользовательским гидам программы передач.
 */
@Dao
interface EpgGuideDao {

    /** Гиды в порядке добавления. */
    @Query("SELECT * FROM epg_guides ORDER BY addedAt")
    suspend fun guides(): List<EpgGuideEntity>

    /** Сколько гидов сохранено. */
    @Query("SELECT COUNT(*) FROM epg_guides")
    suspend fun count(): Int

    /** Гид с этим адресом, если он уже добавлен. */
    @Query("SELECT * FROM epg_guides WHERE url = :url LIMIT 1")
    suspend fun byUrl(url: String): EpgGuideEntity?

    /** Последний момент добавления: нужен, чтобы порядок не ломался при одинаковых часах. */
    @Query("SELECT MAX(addedAt) FROM epg_guides")
    suspend fun maxAddedAt(): Long?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(guide: EpgGuideEntity)

    /** Удаляет гид по идентификатору. */
    @Query("DELETE FROM epg_guides WHERE id = :id")
    suspend fun delete(id: String)
}
