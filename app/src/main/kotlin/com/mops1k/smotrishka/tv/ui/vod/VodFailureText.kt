package com.mops1k.smotrishka.tv.ui.vod

import com.mops1k.smotrishka.tv.core.vod.domain.error.VodFailure

/**
 * Текст ошибки медиатеки для экрана.
 *
 * Формулировки понятные пользователю: что случилось и что можно сделать.
 *
 * @param failure причина из домена.
 */
fun VodFailure.toMessage(): String = when (this) {
    VodFailure.NotConfigured ->
        "Медиатека не подключена. Укажите адрес и ключ портала в настройках."

    VodFailure.SubscriptionRequired ->
        "Подписка медиатеки не найдена. Проверьте ключ портала в настройках."

    is VodFailure.PortalRejected ->
        description?.let { "Портал не понял запрос: $it" } ?: "Портал не понял запрос."

    is VodFailure.Http ->
        "Портал ответил кодом $code. Попробуйте позже."

    is VodFailure.Network ->
        "Нет связи с порталом медиатеки. Проверьте сеть и попробуйте ещё раз."

    VodFailure.BadFormat ->
        "Портал вернул неожиданный ответ. Возможно, изменился формат медиатеки."
}
