package com.mops1k.smotrishka.tv.core.data

/**
 * Результат HTTP-запроса текста: ошибки сети и HTTP различаются,
 * чтобы UI мог показать разный текст и предложить разное действие.
 */
sealed interface HttpTextResult {

    /** Тело ответа в строке (кодировка уже определена). */
    data class Success(val text: String) : HttpTextResult

    /** Сервер ответил, но код не 2xx. */
    data class HttpError(val code: Int) : HttpTextResult

    /** До сервера не дошли: нет сети, таймаут, недоступный хост. */
    data class NetworkError(val reason: String) : HttpTextResult

    /** Ответ пришёл, но тело пустое. */
    data object EmptyBody : HttpTextResult
}
