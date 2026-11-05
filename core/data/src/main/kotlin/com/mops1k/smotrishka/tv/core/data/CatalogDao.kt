package com.mops1k.smotrishka.tv.core.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Upsert

/**
 * Доступ к каталогу каналов и списку источников.
 */
@Dao
interface CatalogDao {

    /** Категории в порядке рейла. */
    @Query("SELECT * FROM categories ORDER BY position")
    suspend fun categories(): List<CategoryEntity>

    /** Все каналы каталога. */
    @Query("SELECT * FROM channels")
    suspend fun channels(): List<ChannelEntity>

    /** Каналы одной категории. */
    @Query("SELECT * FROM channels WHERE categoryId = :categoryId")
    suspend fun channelsByCategory(categoryId: String): List<ChannelEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategories(categories: List<CategoryEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels")
    suspend fun deleteChannels()

    @Query("DELETE FROM categories")
    suspend fun deleteCategories()

    /** Настроенные источники. */
    @Query("SELECT * FROM sources")
    suspend fun sources(): List<SourceEntity>

    @Upsert
    suspend fun upsertSource(source: SourceEntity)
}
