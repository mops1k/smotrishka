package com.mops1k.smotrishka.tv.core.model

/**
 * Канал в файле программы передач (XMLTV).
 *
 * @param id идентификатор канала в EPG (`tvg-id` плейлиста обычно совпадает с ним);
 * @param displayName отображаемое имя;
 * @param iconUrl иконка канала.
 */
data class EpgChannel(
    val id: String,
    val displayName: String,
    val iconUrl: String? = null,
)
