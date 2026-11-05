package com.mops1k.smotrishka.tv.core.playlist

import com.mops1k.smotrishka.tv.core.model.Epg

/**
 * Результат разбора XMLTV: ошибка возвращается значением, а не исключением.
 */
sealed interface XmltvParseResult {

    /** Программа передач разобрана. */
    data class Success(val epg: Epg) : XmltvParseResult

    /**
     * Файл не является корректным XMLTV.
     *
     * @param reason причина для лога и экрана ошибки.
     */
    data class Failure(val reason: String) : XmltvParseResult
}
