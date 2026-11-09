package com.mops1k.smotrishka.tv.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject

/**
 * Миграция схемы 1 → 2: пользовательские гиды программы передач.
 *
 * Регистрируется мультибиндингом в [MigrationsModule]: builder базы получает набор миграций
 * от Hilt и не перечисляет их вручную (решение Александра 10.11.2026).
 */
class Migration1To2 @Inject constructor() : Migration(1, 2) {

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
