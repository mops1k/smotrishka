package com.mops1k.smotrishka.tv.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject

/**
 * Миграция схемы 5 → 6: язык канала для фильтра списков.
 *
 * Регистрируется мультибиндингом в [MigrationsModule]: builder базы получает набор миграций
 * от Hilt и не перечисляет их вручную (решение Александра 10.11.2026).
 */
class Migration5To6 @Inject constructor() : Migration(5, 6) {

    override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("ALTER TABLE `channels` ADD COLUMN `language` TEXT")
    }
}
