package com.mops1k.smotrishka.tv.core.vod.domain.model

/**
 * Строка подключения медиатеки из личного кабинета провайдера.
 *
 * Провайдер выдаёт её целиком: `portal::[key:КЛЮЧ]http://хост/api/v1/`. Ключ и адрес живут
 * в одной строке, поэтому в настройках одно поле, а разбор — здесь, в одном месте.
 */
object VodPortalLink {

    /** Ключ в квадратных скобках; пустое значение допускается, чтобы отличить отсутствие ключа. */
    private val KEY = Regex("""\[key:([^\]]*)]""")

    /** Адрес портала — первый http(s)-токен строки. */
    private val URL = Regex("""https?://[^\s\[\]]+""")

    /**
     * Разбирает строку подключения.
     *
     * @param raw строка из личного кабинета как её скопировал пользователь;
     * @return разобранное подключение либо причина отказа.
     */
    fun parse(raw: String): VodPortalLinkParseResult {
        val trimmed = raw.trim()
        val url = URL.find(trimmed)?.value?.let(VodPortalUrl::from)
            ?: return VodPortalLinkParseResult.BadUrl
        val key = KEY.find(trimmed)?.groupValues?.get(1)?.trim()
        if (key.isNullOrEmpty()) return VodPortalLinkParseResult.MissingKey

        val config = VodPortalConfig.from(url.value, key)
            ?: return VodPortalLinkParseResult.BadUrl
        return VodPortalLinkParseResult.Parsed(config)
    }
}
