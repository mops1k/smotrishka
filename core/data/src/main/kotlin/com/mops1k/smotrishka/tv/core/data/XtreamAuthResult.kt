package com.mops1k.smotrishka.tv.core.data

/**
 * Результат входа в Xtream-аккаунт: причины отказа различаются,
 * потому что пользователю нужно разное действие (проверить пароль, продлить подписку, проверить адрес).
 */
sealed interface XtreamAuthResult {

    /** Вход выполнен. */
    data class Success(val account: XtreamAccount) : XtreamAuthResult

    /** Сервер не принял логин/пароль. */
    data object InvalidCredentials : XtreamAuthResult

    /** Подписка закончилась. */
    data class Expired(val until: String?) : XtreamAuthResult

    /** До сервера не дошли. */
    data class NetworkError(val reason: String) : XtreamAuthResult

    /** Сервер ответил ошибкой HTTP. */
    data class HttpError(val code: Int) : XtreamAuthResult

    /** Ответ не разобрать: неверный адрес сервера или это не Xtream. */
    data class Malformed(val reason: String) : XtreamAuthResult
}
