package com.mops1k.smotrishka.tv.core.data.backup

/**
 * Итог сохранения настроек в файл.
 */
sealed interface SettingsBackupResult {

    /** Настройки собраны и сериализованы. */
    data class Success(val json: String) : SettingsBackupResult

    /** Сохранить не удалось: причина для пользователя. */
    data class Failure(val reason: String) : SettingsBackupResult
}
