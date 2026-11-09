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
 *
 * Версия 3: полнотекстовый индекс поиска (`search_index`, FTS4) по каналам, категориям и
 * передачам (миграция [DatabaseMigrations.MIGRATION_2_3]).
 *
 * Версия 4: индексы по времени передач — выборка «что идёт сейчас»
 * (миграция [DatabaseMigrations.MIGRATION_3_4]).
 */
@Database(
    entities = [
        CategoryEntity::class,
        ChannelEntity::class,
        SourceEntity::class,
        EpgChannelEntity::class,
        ProgrammeEntity::class,
        EpgGuideEntity::class,
        SearchIndexEntity::class,
        ChannelEpgEntity::class,
        EpgImportEntity::class,
    ],
    version = DB_VERSION,
    exportSchema = false,
)
abstract class SmotrishkaDatabase : RoomDatabase() {

    abstract fun catalogDao(): CatalogDao

    abstract fun epgDao(): EpgDao

    abstract fun epgGuideDao(): EpgGuideDao

    abstract fun searchDao(): SearchDao

    abstract fun channelEpgDao(): ChannelEpgDao

    abstract fun epgImportDao(): EpgImportDao
}
