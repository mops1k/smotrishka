package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Результат разбора строки подключения медиатеки.
 *
 * Причины разделены, чтобы настройки показали точный текст: не «ошибка», а «в ссылке нет ключа»
 * или «ссылка не похожа на адрес портала».
 */
sealed interface VodPortalLinkParseResult {

    /**
     * Ссылка разобрана.
     *
     * @property config готовое подключение с нормализованным адресом.
     */
    data class Parsed(val config: VodPortalConfig) : VodPortalLinkParseResult

    /** Адрес есть, а ключа в ссылке нет. */
    data object MissingKey : VodPortalLinkParseResult

    /** Строка не похожа на адрес портала. */
    data object BadUrl : VodPortalLinkParseResult
}
