package com.mops1k.smotrishka.tv.core.data

/**
 * Результат потоковой загрузки.
 */
sealed interface HttpStreamResult<out T> {

    /** Поток прочитан, значение готово. */
    data class Success<T>(val value: T) : HttpStreamResult<T>

    /** Сервер ответил ошибкой. */
    data class HttpError(val code: Int) : HttpStreamResult<Nothing>

    /** Сеть недоступна или соединение оборвалось. */
    data class NetworkError(val reason: String) : HttpStreamResult<Nothing>
}
