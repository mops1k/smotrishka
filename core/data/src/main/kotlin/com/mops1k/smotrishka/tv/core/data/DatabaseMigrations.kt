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
}
