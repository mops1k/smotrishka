package com.mops1k.smotrishka.tv.core.data.backup

/**
 * Итог чтения файла настроек.
 */
sealed interface SettingsBackupReadResult {

    /** Файл прочитан и проверен. */
    data class Success(val backup: SettingsBackup) : SettingsBackupReadResult

    /** Файл не подходит: причина для пользователя. */
    data class Failure(val reason: String) : SettingsBackupReadResult
}
