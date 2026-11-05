package com.mops1k.smotrishka.tv.core.data

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Миграции схемы базы.
 */
internal object DatabaseMigrations {

    /**
     * Версия 2: пользовательские гиды программы передач.
     *
     * Таблица создаётся пустой: стартовый набор вставляет [EpgGuideRepository.ensureDefaults].
     */
    val MIGRATION_1_2: Migration = object : Migration(1, 2) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `epg_guides` (" +
                    "`id` TEXT NOT NULL, " +
                    "`name` TEXT NOT NULL, " +
                    "`url` TEXT NOT NULL, " +
                    "`addedAt` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`id`))",
            )
        }
    }

    /**
     * Версия 3: полнотекстовый индекс поиска.
     *
     * Таблица виртуальная (FTS4) и создаётся пустой: индекс собирает [SearchRepository.rebuild]
     * при первом поиске или после обновления каталога.
     */
    val MIGRATION_2_3: Migration = object : Migration(2, 3) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE VIRTUAL TABLE IF NOT EXISTS `search_index` USING FTS4(" +
                    "`kind` TEXT NOT NULL, " +
                    "`refId` TEXT NOT NULL, " +
                    "`title` TEXT NOT NULL, " +
                    "`subtitle` TEXT NOT NULL)",
            )
        }
    }

    /**
     * Версия 4: индексы по времени передач.
     *
     * Экран каналов выбирает передачи окном «два часа назад — двенадцать вперёд»; без индексов
     * SQLite читал всю таблицу и старт занимал секунды.
     */
    val MIGRATION_3_4: Migration = object : Migration(3, 4) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_startAt` ON `programmes` (`startAt`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_endAt` ON `programmes` (`endAt`)")
        }
    }

    /** 4 → 5: сохранённые связи каналов каталога с каналами гида. */
    val MIGRATION_4_5: Migration = object : Migration(4, 5) {
        override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `channel_epg` (" +
                    "`channelId` TEXT NOT NULL, " +
                    "`epgChannelId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`channelId`))",
            )
        }
    }
}
