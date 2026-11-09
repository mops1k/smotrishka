package com.mops1k.smotrishka.tv.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject

/**
 * Миграция схемы 6 → 7: состояния импорта гидов.
 *
 * Регистрируется мультибиндингом в [MigrationsModule]: builder базы получает набор миграций
 * от Hilt и не перечисляет их вручную (решение Александра 10.11.2026).
 */
class Migration6To7 @Inject constructor() : Migration(6, 7) {

    override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `epg_imports` (" +
                    "`source` TEXT NOT NULL, " +
                    "`running` INTEGER NOT NULL, " +
                    "`startedAt` INTEGER NOT NULL, " +
                    "`updatedAt` INTEGER NOT NULL, " +
                    "`parsedChannels` INTEGER NOT NULL, " +
                    "`addedProgrammes` INTEGER NOT NULL, " +
                    "PRIMARY KEY(`source`))",
            )
    }
}
