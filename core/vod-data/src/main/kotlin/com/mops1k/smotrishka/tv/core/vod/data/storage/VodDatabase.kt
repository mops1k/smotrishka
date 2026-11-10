package com.mops1k.smotrishka.tv.core.vod.data.storage

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * База медиатеки: избранное и история просмотра.
 *
 * Отдельный файл, а не общая база каналов: контекст живёт сам по себе, у него свой цикл
 * изменений, и правки схемы медиатеки не должны трогать базу каналов и программу передач.
 *
 * Версия 1: первая схема, миграции не нужны — база создаётся вместе с подключением медиатеки.
 */
@Database(
    entities = [VodFavoriteEntity::class, VodProgressEntity::class],
    version = VOD_DB_VERSION,
    exportSchema = false,
)
abstract class VodDatabase : RoomDatabase() {

    /** Избранное медиатеки. */
    abstract fun favoritesDao(): VodFavoritesDao

    /** История просмотра медиатеки. */
    abstract fun progressDao(): VodProgressDao
}

/** Версия схемы базы медиатеки. */
internal const val VOD_DB_VERSION: Int = 1
