package com.mops1k.smotrishka.tv.core.data.update

import java.io.File

/**
 * Итог загрузки APK.
 */
sealed interface ApkDownloadResult {

    /**
     * Файл скачан целиком.
     *
     * @param file файл на диске;
     * @param bytes сколько байт записано.
     */
    data class Success(val file: File, val bytes: Long) : ApkDownloadResult

    /** Загрузка не удалась: причина для показа пользователю. */
    data class Failure(val reason: String) : ApkDownloadResult
}
