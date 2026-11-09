package com.mops1k.smotrishka.tv.core.data.backup

/**
 * Итог восстановления настроек из файла.
 */
sealed interface SettingsImportResult {

    /** Настройки применены. */
    data object Success : SettingsImportResult

    /** Восстановить не удалось: причина для пользователя. */
    data class Failure(val reason: String) : SettingsImportResult
}
