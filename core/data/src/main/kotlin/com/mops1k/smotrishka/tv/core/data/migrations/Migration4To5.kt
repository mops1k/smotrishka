package com.mops1k.smotrishka.tv.core.data.migrations

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import javax.inject.Inject

/**
 * Миграция схемы 4 → 5: связи каналов каталога с каналами гида.
 *
 * Регистрируется мультибиндингом в [MigrationsModule]: builder базы получает набор миграций
 * от Hilt и не перечисляет их вручную (решение Александра 10.11.2026).
 */
class Migration4To5 @Inject constructor() : Migration(4, 5) {

    override fun migrate(database: SupportSQLiteDatabase) {
            database.execSQL(
                "CREATE TABLE IF NOT EXISTS `channel_epg` (" +
                    "`channelId` TEXT NOT NULL, " +
                    "`epgChannelId` TEXT NOT NULL, " +
                    "PRIMARY KEY(`channelId`))",
            )
    }
}
