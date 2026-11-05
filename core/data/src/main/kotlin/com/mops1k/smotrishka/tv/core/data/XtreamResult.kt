package com.mops1k.smotrishka.tv.core.data

/**
 * Результат запроса данных Xtream API: значение либо понятная причина отказа.
 */
sealed interface XtreamResult<out T> {

    /** Данные получены. */
    data class Success<T>(val value: T) : XtreamResult<T>

    /** Запрос не удался. */
    data class Failure(val reason: String) : XtreamResult<Nothing>
}
