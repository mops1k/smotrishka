package com.mops1k.smotrishka.tv.core.playlist

/**
 * Результат построения архивного URL: либо адрес для плеера, либо причина отказа.
 */
sealed interface ArchiveUrlResult {

    /** Адрес архивного потока. */
    data class Available(val url: String) : ArchiveUrlResult

    /**
     * Архив недоступен — UI показывает причину вместо кнопки воспроизведения.
     *
     * @param reason текст причины на русском.
     */
    data class Unavailable(val reason: String) : ArchiveUrlResult
}
