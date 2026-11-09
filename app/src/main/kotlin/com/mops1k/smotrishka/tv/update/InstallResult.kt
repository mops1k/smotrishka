package com.mops1k.smotrishka.tv.update

/**
 * Итог запуска системного установщика.
 */
sealed interface InstallResult {

    /** Установщик открыт, решение за пользователем. */
    data object Started : InstallResult

    /** Установка запрещена: нужен переключатель «неизвестные источники» для приложения. */
    data object NeedsPermission : InstallResult

    /**
     * Запустить установщик не удалось.
     *
     * @param reason причина для пользователя.
     */
    data class Failed(val reason: String) : InstallResult
}
