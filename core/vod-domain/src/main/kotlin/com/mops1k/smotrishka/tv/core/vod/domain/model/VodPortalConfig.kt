package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Данные подключения к порталу медиатеки.
 *
 * Оба поля приходят от пользователя из личного кабинета провайдера. Адрес уже нормализован,
 * ключ — значение, которое нельзя печатать в логах.
 *
 * @property url адрес портала;
 * @property key ключ портала.
 */
data class VodPortalConfig(
    val url: VodPortalUrl,
    val key: VodPortalKey,
) {

    companion object {

        /**
         * Собирает конфигурацию из пользовательского ввода.
         *
         * @param rawUrl адрес в любом виде (с пробелами, без слэша);
         * @param rawKey ключ портала;
         * @return конфигурация либо `null`, если адрес или ключ не годятся.
         */
        fun from(rawUrl: String, rawKey: String): VodPortalConfig? {
            val url = VodPortalUrl.from(rawUrl) ?: return null
            val key = rawKey.trim()
            if (key.isEmpty()) return null
            if (key.any { it.isWhitespace() || it == '/' }) return null
            return VodPortalConfig(url = url, key = VodPortalKey(key))
        }
    }
}
