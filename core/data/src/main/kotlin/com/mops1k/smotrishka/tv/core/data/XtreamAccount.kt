package com.mops1k.smotrishka.tv.core.data

/**
 * Сведения об аккаунте Xtream после успешного входа.
 *
 * @param username логин, как его подтвердил сервер;
 * @param isTrial пробный ли аккаунт;
 * @param expiresAt дата окончания подписки в формате провайдера (обычно unix-время).
 */
data class XtreamAccount(
    val username: String?,
    val isTrial: Boolean,
    val expiresAt: String?,
)
