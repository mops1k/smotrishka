package com.mops1k.smotrishka.tv.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject

/**
 * Миграция схемы 3 → 4: индексы по времени передач.
 *
 * Регистрируется мультибиндингом в [MigrationsModule]: builder базы получает набор миграций
 * от Hilt и не перечисляет их вручную (решение Александра 10.11.2026).
 */
class Migration3To4 @Inject constructor() : Migration(3, 4) {

    override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_startAt` ON `programmes` (`startAt`)")
            database.execSQL("CREATE INDEX IF NOT EXISTS `index_programmes_endAt` ON `programmes` (`endAt`)")
    }
}
