package com.mops1k.smotrishka.tv.core.data

/**
 * Гид, который предлагается пользователю при первом запуске.
 *
 * @param name имя источника для настроек;
 * @param url адрес XMLTV.
 */
data class EpgGuideDraft(
    val name: String,
    val url: String,
)
