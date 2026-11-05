package com.mops1k.smotrishka.tv.core.data

/**
 * Результат HTTP-запроса бинарного тела (EPG приходит gzip-ом).
 */
sealed interface HttpBytesResult {

    /** Тело ответа как есть. */
    data class Success(val bytes: ByteArray) : HttpBytesResult

    /** Сервер ответил не 2xx. */
    data class HttpError(val code: Int) : HttpBytesResult

    /** До сервера не дошли. */
    data class NetworkError(val reason: String) : HttpBytesResult

    /** Тело пустое. */
    data object EmptyBody : HttpBytesResult
}
