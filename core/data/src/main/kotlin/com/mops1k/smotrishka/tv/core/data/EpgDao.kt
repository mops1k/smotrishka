package com.mops1k.smotrishka.tv.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * Доступ к программе передач.
 */
@Dao
interface EpgDao {

    /** Каналы EPG. */
    @Query("SELECT * FROM epg_channels")
    suspend fun channels(): List<EpgChannelEntity>

    /** Передачи канала, попадающие в интервал. */
    @Query(
        "SELECT * FROM programmes WHERE channelId = :channelId AND endAt > :fromAt AND startAt < :toAt ORDER BY startAt",
    )
    suspend fun programmes(channelId: String, fromAt: Long, toAt: Long): List<ProgrammeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammes(programmes: List<ProgrammeEntity>)

    /** Вставляет только те передачи, которых ещё нет (ключ — канал и время начала). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgrammesMissing(programmes: List<ProgrammeEntity>)

    /** Наблюдение за числом передач: меняется, когда гид дописывается в базу. */
    @Query("SELECT COUNT(*) FROM programmes")
    fun observeProgrammeCount(): kotlinx.coroutines.flow.Flow<Int>

    /** Сколько передач сейчас в базе. */
    @Query("SELECT COUNT(*) FROM programmes")
    suspend fun countProgrammes(): Long

    /**
     * Сколько разных каналов имеют передачи в интервале — мера покрытия каталога.
     *
     * Нужна, чтобы не качать остальные публичные гиды, когда каталог уже накрыт.
     */
    @Query("SELECT COUNT(DISTINCT channelId) FROM programmes WHERE endAt > :fromAt AND startAt < :toAt")
    suspend fun countCoveredChannels(fromAt: Long, toAt: Long): Int

    @Query("DELETE FROM programmes")
    suspend fun deleteProgrammes()

    @Query("DELETE FROM epg_channels")
    suspend fun deleteChannels()

    /** Удаляет передачи, вышедшие за окно хранения. */
    @Query("DELETE FROM programmes WHERE startAt < :fromAt OR startAt > :toAt")
    suspend fun deleteOutside(fromAt: Long, toAt: Long)
}
