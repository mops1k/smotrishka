package com.mops1k.smotrishka.tv.core.model

/**
 * Заголовки, с которыми провайдер разрешает забирать поток.
 *
 * Провайдеры регулярно требуют конкретный User-Agent или Referer, иначе отдают 403;
 * значения приходят из плейлиста тегами `#EXTVLCOPT` и `#KODIPROP`.
 *
 * @param userAgent значение `http-user-agent`;
 * @param referrer значение `http-referrer`;
 * @param headers прочие заголовки (например, `inputstream.adaptive.*`).
 */
data class StreamHeaders(
    val userAgent: String? = null,
    val referrer: String? = null,
    val headers: Map<String, String> = emptyMap(),
)
