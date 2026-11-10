package com.mops1k.smotrishka.tv.core.vod.domain.error

/**
 * Причина, по которой операция медиатеки не удалась.
 *
 * Домен не знает ни про HTTP-клиент, ни про исключения: адаптеры обязаны свести любую
 * проблему к одному из этих случаев, чтобы интерфейс мог показать понятный текст.
 */
sealed interface VodFailure {

    /** Портал ещё не настроен: нет адреса или ключа. */
    data object NotConfigured : VodFailure

    /** Порталу не понравился ключ: `subscription required`. */
    data object SubscriptionRequired : VodFailure

    /** Портал не понял запрос — обычно сменился формат API. */
    data class PortalRejected(val description: String?) : VodFailure

    /** Сервер ответил кодом, отличным от успеха. */
    data class Http(val code: Int) : VodFailure

    /** Сеть недоступна или соединение оборвалось. */
    data class Network(val message: String?) : VodFailure

    /** Ответ пришёл, но разобрать его не удалось. */
    data object BadFormat : VodFailure
}
