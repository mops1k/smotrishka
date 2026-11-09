package com.mops1k.smotrishka.tv.core.data

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert

/**
 * Доступ к состояниям импорта гидов.
 */
@Dao
interface EpgImportDao {

    /** Все записи состояний. */
    @Query("SELECT * FROM epg_imports")
    suspend fun all(): List<EpgImportEntity>

    /** Состояние одного источника. */
    @Query("SELECT * FROM epg_imports WHERE source = :source")
    suspend fun bySource(source: String): EpgImportEntity?

    /** Сохраняет состояние. */
    @Upsert
    suspend fun upsert(entity: EpgImportEntity)

    /** Удаляет состояние после успешного завершения. */
    @Query("DELETE FROM epg_imports WHERE source = :source")
    suspend fun delete(source: String)
}
