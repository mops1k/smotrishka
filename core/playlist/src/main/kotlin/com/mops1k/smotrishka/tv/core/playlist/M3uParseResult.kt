package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.Playlist

/**
 * Результат разбора M3U: на границе слоя ошибка не бросается исключением,
 * чтобы вызывающий код обязан был её обработать.
 */
sealed interface M3uParseResult {

    /** Плейлист разобран; пустой плейлист — тоже успешный результат. */
    data class Success(val playlist: Playlist) : M3uParseResult

    /**
     * Текст не является плейлистом (например, HTML-страница ошибки провайдера).
     *
     * @param reason причина для лога и экрана ошибки.
     */
    data class Failure(val reason: String) : M3uParseResult
}
