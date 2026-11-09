package com.mops1k.smotrishka.tv.core.data.update

/**
 * Итог проверки обновлений.
 */
sealed interface UpdateCheckResult {

    /**
     * Найдена версия новее установленной.
     *
     * @param release релиз;
     * @param apk APK под тип установленной сборки.
     */
    data class Available(val release: SmotrishkaRelease, val apk: ReleaseAsset) : UpdateCheckResult

    /**
     * Установлена самая свежая версия.
     *
     * @param version установленная версия.
     */
    data class UpToDate(val version: SemverVersion) : UpdateCheckResult

    /** Проверить не удалось: причина для показа пользователю. */
    data class Failure(val reason: String) : UpdateCheckResult
}
