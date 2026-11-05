package com.mops1k.smotrishka.tv.core.data

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * База каталога и программы передач: категории, каналы, источники, EPG, пользовательские гиды.
 *
 * Версия 1: приложение ещё не выпускалось, поэтому миграции не нужны — схема
 * собирается заново на каждой установке debug-сборки.
 *
 * Версия 2: таблица пользовательских гидов программы передач
 * (миграция [DatabaseMigrations.MIGRATION_1_2]).
 */
@Database(
    entities = [
        CategoryEntity::class,
        ChannelEntity::class,
        SourceEntity::class,
        EpgChannelEntity::class,
        ProgrammeEntity::class,
        EpgGuideEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SmotrishkaDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao

    abstract fun epgDao(): EpgDao

    abstract fun epgGuideDao(): EpgGuideDao
}
