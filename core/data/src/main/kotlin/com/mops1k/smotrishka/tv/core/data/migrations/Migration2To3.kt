package com.mops1k.smotrishka.tv.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject

/**
 * Миграция схемы 2 → 3: полнотекстовый индекс поиска.
 *
 * Регистрируется мультибиндингом в [MigrationsModule]: builder базы получает набор миграций
 * от Hilt и не перечисляет их вручную (решение Александра 10.11.2026).
 */
class Migration2To3 @Inject constructor() : Migration(2, 3) {

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
