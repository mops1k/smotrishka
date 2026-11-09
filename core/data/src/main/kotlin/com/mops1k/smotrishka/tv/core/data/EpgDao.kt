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

    /**
     * Передачи канала, попадающие в интервал, по возрастанию времени начала.
     *
     * Дубли из разных гидов здесь не отсеиваются: правило одно для всех экранов и живёт
     * в [withoutGuideDuplicates], иначе одиночная выборка и выборка окна (список каналов)
     * давали бы разные списки передач.
     */
    @Query(
        "SELECT * FROM programmes " +
            "WHERE channelId = :channelId AND endAt > :fromAt AND startAt < :toAt " +
            "ORDER BY startAt",
    )
    suspend fun programmes(channelId: String, fromAt: Long, toAt: Long): List<ProgrammeEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<EpgChannelEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProgrammes(programmes: List<ProgrammeEntity>)

    /** Вставляет только те передачи, которых ещё нет (ключ — канал и время начала). */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertProgrammesMissing(programmes: List<ProgrammeEntity>)

    /**
     * Дописывает описание уже загруженной передаче, не трогая название и время.
     *
     * Провайдерский гид перезаписывать нельзя, но описание из общего гида должно доехать:
     * раньше строка не обновлялась вообще и описание терялось (замечание Александра
     * 09.11.2026).
     */
    @Query(
        "UPDATE programmes SET description = :description " +
            "WHERE channelId = :channelId AND startAt = :startAt " +
            "AND (description IS NULL OR description = '')",
    )
    suspend fun fillDescription(channelId: String, startAt: Long, description: String)

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

    /** Передачи, пересекающиеся с интервалом: нужно для индекса поиска. */
    @Query("SELECT * FROM programmes WHERE endAt > :fromAt AND startAt < :toAt")
    suspend fun programmesInRange(fromAt: Long, toAt: Long): List<ProgrammeEntity>

    /**
     * Передачи, начавшиеся в интервале.
     *
     * В отличие от [programmesInRange] это условие опирается на индекс `startAt`: границы
     * заданы с двух сторон. Запрос «endAt > :fromAt AND startAt < :toAt» индекс не использует
     * и читает всю таблицу — на приставке это не укладывалось в 8 секунд (05.11.2026).
     */
    @Query("SELECT * FROM programmes WHERE startAt >= :fromAt AND startAt <= :toAt")
    suspend fun programmesStartingBetween(fromAt: Long, toAt: Long): List<ProgrammeEntity>

    /** Сколько каналов гида имеют иконку. */
    @Query("SELECT COUNT(*) FROM epg_channels WHERE iconUrl IS NOT NULL AND iconUrl != ''")
    suspend fun countIcons(): Int

    /** Иконки каналов гида для указанных идентификаторов. */
    @Query("SELECT id, iconUrl FROM epg_channels WHERE id IN (:ids)")
    suspend fun iconsFor(ids: List<String>): List<EpgIconRow>

    /** Последнее время окончания передачи: до какого момента гид покрывает эфир. */
    @Query("SELECT MAX(endAt) FROM programmes")
    suspend fun maxEndAt(): Long?

    @Query("DELETE FROM programmes")
    suspend fun deleteProgrammes()

    @Query("DELETE FROM epg_channels")
    suspend fun deleteChannels()

    /** Удаляет передачи, вышедшие за окно хранения. */
    @Query("DELETE FROM programmes WHERE startAt < :fromAt OR startAt > :toAt")
    suspend fun deleteOutside(fromAt: Long, toAt: Long)
}
