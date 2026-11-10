package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Адрес портала медиатеки, полученный в личном кабинете провайдера.
 *
 * Адрес нормализуется один раз при создании: без окружающих пробелов и всегда с завершающим
 * слэшем, иначе сборка запросов даёт двойной или потерянный сегмент пути.
 *
 * @property value нормализованный адрес.
 */
@JvmInline
value class VodPortalUrl private constructor(val value: String) {

    companion object {

        /**
         * Собирает адрес из пользовательской строки.
         *
         * @param raw ввод пользователя с любыми пробелами по краям;
         * @return адрес либо `null`, если строка не похожа на http(s)-адрес.
         */
        fun from(raw: String): VodPortalUrl? {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return null
            if (trimmed.any { it.isWhitespace() }) return null
            if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return null
            return VodPortalUrl(if (trimmed.endsWith("/")) trimmed else "$trimmed/")
        }
    }
}
